package com.tgpoker.room;

import com.tgpoker.engine.ActionType;
import com.tgpoker.engine.Card;
import com.tgpoker.engine.Deck;
import com.tgpoker.engine.GameException;
import com.tgpoker.engine.SeatState;
import com.tgpoker.engine.Stage;
import com.tgpoker.engine.TableState;
import com.tgpoker.engine.TexasEngine;
import com.tgpoker.engine.Variant;
import com.tgpoker.util.Money;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 一张牌桌的运行时。
 *
 * <p>所有对外方法都丢进同一个单线程 executor，因此 {@link TableState} 永远只被一个线程访问，
 * 不需要任何锁。这就是「1 桌 = 1 Actor」的落地方式。</p>
 *
 * <p>旁观者与座位玩家共用同一个 actor，但下发视图时按 role 裁剪：旁观者拿不到任何底牌，
 * 摊牌也只推胜方。协议上显式声明 role，不靠"有没有座位号"推断——一次越权就是致命事故。</p>
 */
@Slf4j
public class TableActor {

    private final TableConfig cfg;
    private final TableState state;
    private final TableEvents events;
    private final ScheduledExecutorService timer;

    private final ExecutorService exec;

    private final Map<Integer, Long> handStartStack = new HashMap<>();
    private final Map<Integer, Card[]> handStartHole = new HashMap<>();
    private LocalDateTime handStartedAt;
    private ScheduledFuture<?> timeoutFuture;
    private ScheduledFuture<?> nextHandFuture;
    private boolean settledReported = true;
    private volatile boolean closed;

    /** 暂停前的现场，用于恢复：阶段 / 行动人 / 剩余时间。 */
    private Stage pausedStage;
    private int pausedActingSeat = -1;
    private long pausedRemainMs;

    /** 玩家提前提交的种子，下一手生效。 */
    private final Map<Long, String> pendingClientSeeds = new HashMap<>();
    /** 本手保险单。 */
    private final List<InsuranceOrder> insuranceOrders = new ArrayList<>();
    /** 本桌累计保险流水（桌内"保险"Tab 用，最近 50 条）。 */
    private final List<InsuranceOrder> insuranceHistory = new ArrayList<>();
    private int insuranceSeq;
    private String insuranceHandId;

    /** 桌内聊天，环形保留最近 30 条。 */
    private final List<ChatMsg> chat = new ArrayList<>();
    /** 每个用户最后发言时间，用来做 2 秒限频（防刷屏/广告）。 */
    private final Map<Long, Long> lastChatAt = new HashMap<>();
    private static final int CHAT_MAX = 30;
    private static final int CHAT_MIN_INTERVAL_MS = 2000;

    private final long createdAt = System.currentTimeMillis();

    public TableActor(TableConfig cfg, TableEvents events, ScheduledExecutorService timer) {
        this.cfg = cfg;
        this.events = events;
        this.timer = timer;
        this.state = new TableState(cfg.maxSeats);
        this.state.tableId = cfg.id;
        this.state.name = cfg.name;
        this.state.variant = cfg.variantEnum();
        this.state.smallBlind = cfg.smallBlind;
        this.state.bigBlind = cfg.bigBlind;
        this.state.ante = cfg.ante;
        this.state.anteMode = cfg.anteMode;
        this.state.minBuyIn = cfg.minBuyIn;
        this.state.maxBuyIn = cfg.maxBuyIn;
        this.state.minRaise = cfg.bigBlind;
        this.state.insuranceEnabled = cfg.insuranceEnabled;
        this.state.straddle = cfg.straddle;
        this.state.straddleAmount = cfg.straddle ? cfg.bigBlind * 2 : 0;
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "table-" + cfg.id);
            t.setDaemon(true);
            return t;
        });
    }

    /** 牌桌配置（只读）。 */
    public TableConfig getConfig() {
        return cfg;
    }

    /** 牌桌状态；调用方必须知道这是 actor 内部状态，读字段前应通过视图方法。 */
    public TableState getState() {
        return state;
    }

    /** 有效在座人数（不含暂离与空位）。 */
    public int seatedCount() {
        return state.countSeated();
    }

    private long actionMs() {
        return cfg.actionSeconds * 1000L;
    }

    private void submit(String name, Runnable task) {
        if (closed) {
            return;
        }
        exec.execute(() -> {
            try {
                // 所有状态变更都在 state 锁内执行；HTTP 侧的视图/账单读取也加同一把锁，
                // 避免"管理端拉账单时正好在结算"读到中间态。锁粒度是单桌，不会互相阻塞。
                synchronized (state) {
                    task.run();
                }
            } catch (GameException e) {
                log.debug("{} 规则拒绝: {}", name, e.getMessage());
            } catch (Exception e) {
                log.error("{} 执行异常", name, e);
            }
        });
    }

    // ---------------------------------------------------------------- 入座 / 离桌

    /**
     * 入座并买入。
     *
     * <p>扣款与占座放在同一个牌桌线程里同步完成，避免"扣了钱没座位"或"占了座没扣钱"。
     * 买入必须在 [minBuyIn, maxBuyIn] 区间内。</p>
     *
     * @param preferSeat 期望座位号，-1 表示随便找个空位
     */
    public void sit(long userId, String nick, String avatar, long buyIn, int preferSeat) {
        submit("sit", () -> {
            if (state.seatOf(userId) != null) {
                events.onError(userId, "你已经在座上了");
                return;
            }
            if (buyIn < cfg.minBuyIn || buyIn > cfg.maxBuyIn) {
                events.onError(userId, "买入需在 " + Money.fmt(cfg.minBuyIn) + " ~ " + Money.fmt(cfg.maxBuyIn) + " 之间");
                return;
            }
            SeatState target = null;
            if (preferSeat >= 0 && preferSeat < state.seats.size()) {
                SeatState s = state.seats.get(preferSeat);
                if (!s.seated) {
                    target = s;
                }
            }
            if (target == null) {
                for (SeatState s : state.seats) {
                    if (!s.seated) {
                        target = s;
                        break;
                    }
                }
            }
            if (target == null) {
                events.onError(userId, "牌桌已满");
                return;
            }

            // 扣款放在牌桌线程里同步做：座位一定拿得到，不会出现"扣了钱没座"
            String bizId = "buyin-" + cfg.id + "-" + userId + "-" + System.currentTimeMillis();
            if (!events.onBuyIn(userId, buyIn, bizId)) {
                events.onError(userId, "钱包余额不足");
                return;
            }

            target.seated = true;
            target.sittingOut = false;
            target.userId = userId;
            target.nick = nick;
            target.avatar = avatar == null ? "" : avatar;
            target.stack = buyIn;
            target.buyInTotal = buyIn;
            target.handsPlayed = 0;
            target.vpipHands = 0;
            target.pfrHands = 0;
            target.folded = true;
            target.inHand = false;

            state.logMsg(nick + " 入座 " + target.seat + " 号位，买入 " + Money.fmt(buyIn));
            afterChange();

            if (state.stage == Stage.WAITING || state.stage == Stage.SETTLED) {
                scheduleNextHand(2500);
            }
        });
    }

    /**
     * 补码：手牌间隙追加买入，补后不得超过最大买入。
     */
    public void rebuy(long userId, long amount) {
        submit("rebuy", () -> {
            SeatState st = state.seatOf(userId);
            if (st == null) {
                events.onError(userId, "你不在座位上");
                return;
            }
            if (state.stage != Stage.WAITING && state.stage != Stage.SETTLED) {
                events.onError(userId, "牌局进行中不能补码");
                return;
            }
            if (amount <= 0 || st.stack + amount > cfg.maxBuyIn) {
                events.onError(userId, "补码后不能超过最大买入");
                return;
            }
            String bizId = "rebuy-" + cfg.id + "-" + userId + "-" + System.currentTimeMillis();
            if (!events.onBuyIn(userId, amount, bizId)) {
                events.onError(userId, "钱包余额不足");
                return;
            }
            st.stack += amount;
            st.buyInTotal += amount;
            state.logMsg(st.nick + " 补码 " + Money.fmt(amount));
            afterChange();
        });
    }

    /**
     * 离桌。
     *
     * <p>手牌进行中先弃牌并坐出，筹码等这手结束再退（不能把正在池里的钱直接抽走）。
     * 手牌间隙则立即结算退筹码。</p>
     */
    public void leave(long userId) {
        submit("leave", () -> {
            SeatState st = state.seatOf(userId);
            if (st == null) {
                return;
            }
            if (st.inHand && !st.folded && state.stage != Stage.SETTLED && state.stage != Stage.WAITING) {
                // 手牌进行中：先弃牌并坐出，筹码等这手结束再退
                st.folded = true;
                st.sittingOut = true;
                state.logMsg(st.nick + " 离桌，自动弃牌");
                if (st.seat == state.actingSeat) {
                    try {
                        TexasEngine.act(state, st.seat, ActionType.FOLD, 0, now(), actionMs());
                    } catch (GameException ignore) {
                        // 已经推进过了
                    }
                }
                afterChange();
                return;
            }
            cashOutAndRelease(st);
            afterChange();
        });
    }

    private void cashOutAndRelease(SeatState st) {
        long amount = st.stack;
        if (amount > 0) {
            events.onCashOut(st.userId, amount, amount, "离桌退筹码");
        }
        state.logMsg(st.nick + " 离桌，带走 " + Money.fmt(amount));
        st.seated = false;
        st.sittingOut = false;
        st.inHand = false;
        st.stack = 0;
        st.bet = 0;
        st.folded = true;
        st.allIn = false;
        st.hole = new Card[2];
        st.lastAction = "";
        st.userId = 0;
        st.nick = "";
        st.avatar = "";
        st.buyInTotal = 0;
        st.handsPlayed = 0;
        st.vpipHands = 0;
        st.pfrHands = 0;
    }

    // ---------------------------------------------------------------- 行动

    /**
     * 玩家行动。
     *
     * <p>规则校验与状态推进全部交给 {@link TexasEngine}，这里只负责把动作投递到牌桌线程。
     * 非法动作会通过 {@link TableEvents#onError} 回给该玩家，不会中断牌桌。</p>
     *
     * @param amount RAISE 时是"加注到的总额"，其余动作忽略
     */
    public void act(long userId, ActionType type, long amount) {
        submit("act", () -> {
            if (state.stage == Stage.PAUSED) {
                events.onError(userId, "牌桌已暂停，请等待管理员恢复");
                return;
            }
            SeatState st = state.seatOf(userId);
            if (st == null) {
                events.onError(userId, "你不在座位上");
                return;
            }
            TexasEngine.act(state, st.seat, type, amount, now(), actionMs());
            afterChange();
        });
    }

    /** 立即开局（人够且处于等待/结算态才生效）。 */
    public void startNow() {
        submit("start", () -> {
            if (state.stage == Stage.PAUSED) {
                return;
            }
            if (state.countSeated() < 2) {
                return;
            }
            if (state.stage != Stage.WAITING && state.stage != Stage.SETTLED) {
                return;
            }
            doStartHand();
        });
    }

    /**
     * 暂停牌桌（管理员）。
     *
     * <p>冻结计时器与行动入口，但<b>不冻结底池</b>：筹码已经在池里，冻结底池等于改账。
     * 恢复时按剩余时间重置 deadline，超时时间不会被暂停"吃掉"。</p>
     */
    public void pause() {
        submit("pause", () -> {
            if (state.stage == Stage.PAUSED) {
                return;
            }
            if (state.stage == Stage.WAITING || state.stage == Stage.SETTLED) {
                events.onError(0, "当前没有进行中的牌局");
                return;
            }
            pausedStage = state.stage;
            pausedActingSeat = state.actingSeat;
            pausedRemainMs = Math.max(3000, state.deadline - now());
            state.stage = Stage.PAUSED;
            state.actingSeat = -1;
            state.deadline = 0;
            state.logMsg("牌桌已暂停（管理员操作）");
            afterChange();
        });
    }

    /** 恢复牌桌：回到暂停前的阶段与行动人，剩余时间至少 3 秒。 */
    public void resume() {
        submit("resume", () -> {
            if (state.stage != Stage.PAUSED || pausedStage == null) {
                return;
            }
            state.stage = pausedStage;
            state.actingSeat = pausedActingSeat;
            state.deadline = now() + Math.max(3000, pausedRemainMs);
            pausedStage = null;
            pausedActingSeat = -1;
            pausedRemainMs = 0;
            state.logMsg("牌桌已恢复");
            afterChange();
        });
    }

    /**
     * 解散牌桌：退还所有在座玩家的桌上筹码，清空牌局状态，桌子重置为可继续使用。
     *
     * <p>已经在底池里的钱不会消失——它会随筹码一起退给各自的主人，
     * 因为本手没有产生赢家，这属于"取消本手"，不是"重新分配"。</p>
     */
    public void disband() {
        submit("disband", () -> {
            for (SeatState st : state.seats) {
                if (st.seated) {
                    // 本手作废：把自己已投入底池的筹码先退回，再整体退到钱包
                    st.stack += st.contributed;
                    st.contributed = 0;
                    st.bet = 0;
                    cashOutAndRelease(st);
                }
            }
            state.stage = Stage.WAITING;
            state.actingSeat = -1;
            state.deadline = 0;
            state.pot = 0;
            state.board.clear();
            state.currentBet = 0;
            state.insurancePending = false;
            state.logMsg("牌桌已解散，筹码已退回各玩家钱包");
            afterChange();
        });
    }

    /**
     * 提交玩家种子（下一手生效，提交后不可改）。
     *
     * <p>这是"防运营方提前挑牌序"的关键：只要有一个玩家提交真随机数，
     * 服务端就无法离线枚举出对自己有利的牌序。</p>
     */
    public void submitClientSeed(long userId, String seed) {
        submit("clientSeed", () -> {
            if (state.seatOf(userId) == null) {
                events.onError(userId, "上桌后才能提交种子");
                return;
            }
            if (seed == null || seed.isBlank() || seed.length() > 64) {
                events.onError(userId, "种子格式不正确");
                return;
            }
            pendingClientSeeds.putIfAbsent(userId, seed.trim());
            afterChange();
        });
    }

    /** 查我是否已提交过种子（用于前端回显）。 */
    public String myClientSeed(long userId) {
        return pendingClientSeeds.get(userId);
    }

    /**
     * 桌内发言。
     *
     * <p>限频 2 秒、单条 60 字、去掉尖括号防注入。旁观者当前不允许发言
     * （技术方案 §8.5：旁观发言受限，先直接关掉，避免广告与骂人）。</p>
     */
    public void chat(long userId, String text) {
        submit("chat", () -> {
            SeatState st = state.seatOf(userId);
            if (st == null) {
                events.onError(userId, "上桌后才能发言");
                return;
            }
            if (text == null) {
                return;
            }
            String clean = text.replaceAll("[<>\\r\\n]", " ").trim();
            if (clean.isEmpty()) {
                return;
            }
            if (clean.length() > 60) {
                clean = clean.substring(0, 60);
            }
            long now = now();
            Long last = lastChatAt.get(userId);
            if (last != null && now - last < CHAT_MIN_INTERVAL_MS) {
                events.onError(userId, "发言太快，稍等一下");
                return;
            }
            lastChatAt.put(userId, now);
            chat.add(new ChatMsg(st.seat, userId, st.nick, clean, now));
            while (chat.size() > CHAT_MAX) {
                chat.remove(0);
            }
            afterChange();
        });
    }

    // ---------------------------------------------------------------- 状态推进

    private void doStartHand() {
        if (state.countSeated() < 2) {
            return;
        }
        // 手牌结束：结算上一手并清理离桌的人
        if (state.stage == Stage.SETTLED) {
            purgeSittingOutPlayers();
        }

        handStartedAt = LocalDateTime.now();
        // 按座位顺序收集玩家种子，最后提交者无法预知他人种子
        List<String> seeds = new ArrayList<>();
        for (SeatState st : state.seats) {
            if (st.seated && !st.sittingOut && st.stack > 0) {
                String s = pendingClientSeeds.get(st.userId);
                if (s != null) {
                    seeds.add(s);
                }
            }
        }
        pendingClientSeeds.clear();

        String serverSeed = randomHex(32);
        TexasEngine.startHand(state, now(), actionMs(), serverSeed, seeds);
        if (state.stage == Stage.WAITING) {
            return;
        }

        handStartStack.clear();
        handStartHole.clear();
        for (SeatState st : state.seats) {
            if (st.inHand) {
                handStartStack.put(st.seat, st.stack + st.bet);
                handStartHole.put(st.seat, new Card[]{st.hole[0], st.hole[1]});
            }
        }
        settledReported = false;
        insuranceOrders.clear();
        insuranceHandId = state.handId;
        afterChange();
    }

    private void purgeSittingOutPlayers() {
        for (SeatState st : state.seats) {
            if (st.seated && st.sittingOut && !st.inHand) {
                cashOutAndRelease(st);
            }
        }
    }

    private void afterChange() {
        state.seq++;

        if (state.stage == Stage.SETTLED && !settledReported) {
            settledReported = true;
            reportSettlement();
            if (state.lastServerSeed != null) {
                state.logMsg("本手种子已揭示：" + state.lastServerSeed.substring(0, 16) + "…");
            }
        }

        scheduleTimeout();
        events.onUpdate(this);

        if (state.stage == Stage.SETTLED || state.stage == Stage.WAITING) {
            scheduleNextHand(3000);
        }
    }

    private void reportSettlement() {
        HandOutcome o = new HandOutcome();
        o.handId = state.handId;
        o.tableId = state.tableId;
        o.clubId = cfg.clubId;
        o.variant = state.variant.name();
        o.blinds = Money.fmt(state.smallBlind) + "/" + Money.fmt(state.bigBlind);
        o.ante = state.ante;
        o.board = state.boardString();
        o.pot = state.pot;
        o.serverSeed = state.serverSeed;
        o.serverSeedHash = state.serverSeedHash;
        o.clientSeeds = String.join(",", state.clientSeeds);
        o.nonce = state.handNo;
        o.verifyCode = state.verifyCode;
        o.startedAt = handStartedAt;
        o.endedAt = LocalDateTime.now();

        // 先结算保险，再统计净额，否则"总输赢"口径会把保费漏掉
        settleInsurance();
        for (InsuranceOrder io : insuranceHistory) {
            if (io.handId.equals(state.handId)) {
                o.insurances.add(io);
            }
        }

        for (SeatState st : state.seats) {
            if (!handStartStack.containsKey(st.seat)) {
                continue;
            }
            HandOutcome.PlayerResult r = new HandOutcome.PlayerResult();
            r.seat = st.seat;
            r.userId = st.userId;
            r.startStack = handStartStack.get(st.seat);
            r.endStack = st.stack;
            r.net = st.stack - r.startStack;
            Card[] hole = handStartHole.get(st.seat);
            r.holeCards = hole != null && hole[0] != null ? hole[0].code() + hole[1].code() : "";
            r.vpip = st.vpipThisHand ? 1 : 0;
            r.pfr = st.pfrThisHand ? 1 : 0;
            r.insuranceNet = insuranceNetOf(st.userId);
            o.players.add(r);

            // 桌内统计（账单 Tab 的口径）
            st.handsPlayed++;
            if (st.vpipThisHand) {
                st.vpipHands++;
            }
            if (st.pfrThisHand) {
                st.pfrHands++;
            }
        }

        state.lastServerSeed = state.serverSeed;
        events.onHandSettled(this, o);
    }

    private long insuranceNetOf(long userId) {
        long net = 0;
        for (InsuranceOrder io : insuranceOrders) {
            if (io.buyerUserId == userId) {
                net += io.payout - io.premiumMain - io.premiumTie;
            }
        }
        return net;
    }

    // ---------------------------------------------------------------- 保险

    /**
     * 计算当前可买的保险报价。
     *
     * <p>触发条件（技术方案 §7.6）：转牌圈、双方单挑、有人 All-in 且已亮牌、
     * outs 在 1~14、金库保证金足够。任一不满足返回 {@code available=false} 并带原因。</p>
     */
    public InsuranceOdds.Quote quoteInsurance(long userId) {
        synchronized (state) {
            return quoteInsuranceLocked(userId);
        }
    }

    private InsuranceOdds.Quote quoteInsuranceLocked(long userId) {
        InsuranceOdds.Quote q = new InsuranceOdds.Quote();
        if (!cfg.insuranceEnabled) {
            q.reason = "本桌未开启保险";
            return q;
        }
        if (state.stage != Stage.TURN) {
            q.reason = "只在转牌圈（河牌前）提供保险";
            return q;
        }
        List<SeatState> alive = new ArrayList<>();
        for (SeatState st : state.seats) {
            if (st.inHand && !st.folded) {
                alive.add(st);
            }
        }
        // 长牌最多 4 人、短牌单池最多 4 人（§7.6 条件⑧）
        if (alive.size() < 2 || alive.size() > 4) {
            q.reason = "只支持 2~4 人 All-in 投保";
            return q;
        }
        boolean anyAllIn = false;
        for (SeatState st : alive) {
            if (st.allIn) {
                anyAllIn = true;
                break;
            }
        }
        if (!anyAllIn) {
            q.reason = "需要有人 All-in 并亮牌";
            return q;
        }
        if (!events.insuranceOptIn(userId)) {
            q.reason = "你已关闭保险，可在「我的」里重新开启";
            return q;
        }

        List<Card[]> holes = new ArrayList<>();
        List<Integer> seats = new ArrayList<>();
        for (SeatState st : alive) {
            holes.add(st.hole);
            seats.add(st.seat);
        }
        InsuranceOdds.Quote real = InsuranceOdds.quoteMulti(state.variant, holes, seats,
                state.board, state.pot, alive.size());
        if (!real.available) {
            return real;
        }
        SeatState buyer = state.seats.get(real.buyerSeat);
        if (buyer.userId != userId) {
            real.available = false;
            real.reason = "只有领先方可以投保";
            return real;
        }
        // 俱乐部金库必须能覆盖赔付（保证金 ≥ 1 个底池）
        if (!events.onTreasuryReserve(cfg.clubId, real.maxCoverage)) {
            real.available = false;
            real.reason = "俱乐部金库保证金不足，暂不可投保";
            return real;
        }
        return real;
    }

    /**
     * 买入保险。
     *
     * <p>保费从<b>场外钱包</b>扣，不扣桌上筹码：All-in 之后玩家桌上可能已经没钱，
     * 但保险保的是"这手牌的结果"，钱必须能收得到。</p>
     *
     * @param coverage 保额；传 0 表示按上限（满池）投保
     */
    public void buyInsurance(long userId, long coverage) {
        submit("buyInsurance", () -> {
            InsuranceOdds.Quote q = quoteInsuranceLocked(userId);
            if (!q.available) {
                events.onError(userId, q.reason == null ? "当前不能投保" : q.reason);
                return;
            }
            long cov = coverage <= 0 ? q.maxCoverage : Math.min(coverage, q.maxCoverage);
            long premium = (long) Math.ceil(cov / q.odds);
            if (premium <= 0) {
                events.onError(userId, "保额过小");
                return;
            }
            String bizId = "ins-" + state.handId + "-" + (++insuranceSeq);
            if (!events.onInsurancePremium(userId, premium, bizId)) {
                events.onError(userId, "钱包余额不足，无法支付保费");
                return;
            }
            InsuranceOrder io = new InsuranceOrder();
            io.id = insuranceSeq;
            io.handId = state.handId;
            io.tableId = state.tableId;
            io.potIndex = 0;
            io.buyerUserId = userId;
            io.buyerSeat = q.buyerSeat;
            io.behindSeat = q.behindSeat;
            io.outs = q.outs;
            io.odds = q.odds;
            io.tieOuts = q.tieOuts;
            io.tieOdds = q.tieOdds;
            io.premiumMain = premium;
            io.coverage = cov;
            io.nick = state.seats.get(q.buyerSeat).nick;
            io.handNo = (int) state.handNo;
            io.street = "TURN";
            insuranceOrders.add(io);
            state.logMsg(io.nick + " 买入保险 " + Money.fmt(cov) + "，保费 " + Money.fmt(premium)
                    + "（" + q.outs + " outs，赔率 " + q.oddsText() + "）");
            // 买完就继续发河牌，不让玩家干等
            if (state.insurancePending) {
                TexasEngine.continueRunout(state);
            }
            afterChange();
        });
    }

    /** 结算本手保险：反超方赢了就赔，没赢保费归金库。 */
    private void settleInsurance() {
        if (insuranceOrders.isEmpty()) {
            return;
        }
        for (InsuranceOrder io : insuranceOrders) {
            if (!"PENDING".equals(io.result)) {
                continue;
            }
            // 多人投保时 behindSeat 为 -1，用"领先方是否丢掉底池"判断是否触发赔付
            boolean triggered = !state.winners.contains(io.buyerSeat);
            if (triggered) {
                io.result = "WON";
                io.payout = io.coverage;
                events.onInsurancePayout(io.buyerUserId, io.coverage, "inspay-" + io.handId + "-" + io.id);
                events.onTreasurySettle(cfg.clubId, -io.coverage);
                state.logMsg("保险赔付 " + io.nick + " " + Money.fmt(io.coverage));
            } else {
                io.result = "LOST";
                io.payout = 0;
                events.onTreasurySettle(cfg.clubId, io.premiumMain + io.premiumTie);
                state.logMsg("保险未触发，" + io.nick + " 保费归金库");
            }
            insuranceHistory.add(io);
        }
        insuranceOrders.clear();
    }

    /** 本桌保险流水（最近若干条，桌内公开）。 */
    public List<InsuranceOrder> insuranceHistory() {
        synchronized (state) {
            return new ArrayList<>(insuranceHistory);
        }
    }

    // ---------------------------------------------------------------- 账单

    /**
     * 桌内账单。
     *
     * <p>买入金额只对本人返回（避免被推算资金实力），
     * 手数 / 入池率 / 输赢对全桌公开（天然的社会监督，技术方案 §11.1）。</p>
     *
     * @param viewerUserId 查看者，决定 buyIn 是否下发
     */
    public List<TableLedgerRow> ledger(long viewerUserId) {
        synchronized (state) {
            return ledgerLocked(viewerUserId);
        }
    }

    private List<TableLedgerRow> ledgerLocked(long viewerUserId) {
        List<TableLedgerRow> rows = new ArrayList<>();
        for (SeatState st : state.seats) {
            if (!st.seated) {
                continue;
            }
            TableLedgerRow r = new TableLedgerRow();
            r.seat = st.seat;
            r.userId = st.userId;
            r.nick = st.nick;
            r.avatar = st.avatar;
            r.seated = true;
            r.hands = st.handsPlayed;
            r.vpip = st.vpipPercent();
            r.pfr = st.pfrPercent();
            r.net = st.tableNet();
            r.netText = Money.fmtSigned(r.net);
            if (st.userId == viewerUserId) {
                r.buyIn = st.buyInTotal;
                r.buyInText = Money.fmt(st.buyInTotal);
            }
            rows.add(r);
        }
        return rows;
    }

    public int spectatorHint() {
        return 0;
    }

    private void scheduleTimeout() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
        // 保险窗口：没有行动人，但仍然有时限，到点继续发河牌
        if (state.insurancePending) {
            long wait = Math.max(50, state.deadline - now());
            String hand = state.handId;
            timeoutFuture = timer.schedule(() -> submit("insuranceTimeout", () -> {
                if (state.insurancePending && hand != null && hand.equals(state.handId)) {
                    TexasEngine.continueRunout(state);
                    afterChange();
                }
            }), wait, TimeUnit.MILLISECONDS);
            return;
        }
        if (state.actingSeat < 0 || state.stage == Stage.SETTLED || state.stage == Stage.WAITING) {
            return;
        }
        long delay = state.deadline - now();
        if (delay <= 0) {
            delay = 50;
        }
        long expectedSeq = state.seq;
        String expectedHand = state.handId;
        timeoutFuture = timer.schedule(() -> submit("timeout", () -> {
            if (state.seq < expectedSeq) {
                return;
            }
            if (state.handId == null || !state.handId.equals(expectedHand)) {
                return;
            }
            TexasEngine.timeoutAct(state, now(), actionMs());
            afterChange();
        }), delay, TimeUnit.MILLISECONDS);
    }

    private void scheduleNextHand(long delayMs) {
        if (closed) {
            return;
        }
        if (nextHandFuture != null && !nextHandFuture.isDone()) {
            return;
        }
        if (state.countSeated() < 2) {
            return;
        }
        nextHandFuture = timer.schedule(() -> submit("nextHand", () -> {
            if (state.countSeated() >= 2
                    && (state.stage == Stage.SETTLED || state.stage == Stage.WAITING)) {
                doStartHand();
            }
        }), delayMs, TimeUnit.MILLISECONDS);
    }

    private long now() {
        return System.currentTimeMillis();
    }

    // ---------------------------------------------------------------- 视图

    /**
     * 按查看者裁剪的视图。
     *
     * @param viewerUserId 查看者；null 表示未登录
     * @param role         SEATED / SPECTATOR；<b>由服务端根据真实座位计算</b>，不能信客户端
     */
    public TableView viewFor(Long viewerUserId, String role) {
        synchronized (state) {
            return viewForLocked(viewerUserId, role);
        }
    }

    private TableView viewForLocked(Long viewerUserId, String role) {
        TableView v = new TableView();
        v.tableId = state.tableId;
        v.name = state.name;
        v.variant = state.variant.name();
        v.variantLabel = state.variant.label();
        v.clubId = cfg.clubId;
        v.smallBlind = state.smallBlind;
        v.bigBlind = state.bigBlind;
        v.ante = state.ante;
        v.anteMode = state.anteMode;
        v.anteLabel = cfg.anteLabel();
        v.straddle = state.straddle;
        v.straddleAmount = state.straddleAmount;
        v.straddleSeat = state.straddleSeat;
        v.minBuyIn = state.minBuyIn;
        v.maxBuyIn = state.maxBuyIn;
        v.maxSeats = state.maxSeats;
        v.stage = state.stage.name();
        v.handId = state.handId;
        v.handNo = state.handNo;
        v.buttonSeat = state.buttonSeat;
        v.actingSeat = state.actingSeat;
        v.pot = state.pot;
        v.currentBet = state.currentBet;
        v.minRaise = state.minRaise;
        v.deadline = state.deadline;
        v.serverTime = now();
        v.showdown = state.showdown;
        v.serverSeedHash = state.serverSeedHash;
        v.lastServerSeed = state.lastServerSeed;
        v.verifyCode = state.verifyCode;
        v.seq = state.seq;
        v.actionSeconds = cfg.actionSeconds;
        v.insuranceEnabled = cfg.insuranceEnabled;
        v.canStart = state.countSeated() >= 2
                && (state.stage == Stage.WAITING || state.stage == Stage.SETTLED);

        // 玩家种子结算后才公开，否则等于提前泄露下一手熵源
        if (state.stage == Stage.SETTLED) {
            v.clientSeeds.addAll(state.clientSeeds);
        }

        for (Card c : state.board) {
            v.board.add(c.code());
        }
        v.log.addAll(state.log);
        v.chat = new ArrayList<>(chat);
        v.runningMinutes = (int) ((System.currentTimeMillis() - createdAt) / 60000);

        boolean viewerSeated = "SEATED".equals(role);
        boolean spectator = !viewerSeated;

        for (SeatState st : state.seats) {
            SeatView sv = new SeatView();
            sv.seat = st.seat;
            sv.seated = st.seated;
            sv.userId = st.seated ? st.userId : null;
            sv.nick = st.nick;
            sv.avatar = st.avatar;
            sv.sittingOut = st.sittingOut;
            sv.folded = st.folded;
            sv.allIn = st.allIn;
            sv.hasActed = st.hasActed;
            sv.inHand = st.inHand;
            sv.dealer = st.seat == state.buttonSeat;
            sv.acting = st.seat == state.actingSeat;
            sv.stack = st.stack;
            sv.bet = st.bet;
            sv.lastAction = st.lastAction;
            sv.lastAmount = st.lastAmount;

            boolean mine = viewerUserId != null && st.seated && st.userId == viewerUserId;
            if (mine) {
                v.mySeat = st.seat;
                v.role = "SEATED";
                if (st.hole[0] != null) {
                    v.myCards.add(st.hole[0].code());
                    v.myCards.add(st.hole[1].code());
                }
            }

            // 底牌下发规则：
            //   座位玩家：本人 / 摊牌 / All-in 亮牌
            //   旁观者：永远不给，摊牌也只给胜方（文档 §8.5）
            boolean show = false;
            if (!spectator) {
                show = mine || (st.seated && st.hole[0] != null
                        && ((state.showdown && st.showCards) || st.allIn));
            } else if (state.showdown && st.hole[0] != null && state.winners.contains(st.seat)) {
                show = true;
            }
            if (show && st.hole[0] != null) {
                sv.cards = new ArrayList<>();
                sv.cards.add(st.hole[0].code());
                sv.cards.add(st.hole[1].code());
                sv.revealed = true;
            }
            v.seats.add(sv);
        }

        if (spectator) {
            v.role = "SPECTATOR";
            v.mySeat = -1;
        }

        // 只有座位上的玩家才需要保险报价，且必须是当前手
        if (!spectator && viewerUserId != null && state.stage == Stage.TURN) {
            InsuranceOdds.Quote q = quoteInsuranceLocked(viewerUserId);
            if (q.available) {
                InsuranceQuoteView iv = new InsuranceQuoteView();
                iv.buyerSeat = q.buyerSeat;
                iv.behindSeat = q.behindSeat;
                iv.outs = q.outs;
                iv.odds = q.odds;
                iv.oddsText = q.oddsText();
                iv.maxCoverage = q.maxCoverage;
                iv.coverage = q.coverage;
                iv.premium = q.premium;
                iv.premiumText = Money.fmt(q.premium);
                iv.reason = q.reason;
                v.insurance = iv;
            }
        }
        v.insurances = new ArrayList<>(insuranceOrders);
        // 桌内"保险"Tab 要看得到已经成交的历史，而不只是当前手
        int from = Math.max(0, insuranceHistory.size() - 8);
        for (int i = from; i < insuranceHistory.size(); i++) {
            InsuranceOrder io = insuranceHistory.get(i);
            if (!io.handId.equals(state.handId)) {
                v.insurances.add(io);
            }
        }
        return v;
    }

    public TableView viewFor(Long viewerUserId) {
        String role = viewerUserId != null && state.seatOf(viewerUserId) != null ? "SEATED" : "SPECTATOR";
        return viewFor(viewerUserId, role);
    }

    /** 大厅列表用的摘要。 */
    public LobbyItem summary() {
        synchronized (state) {
            return summaryLocked();
        }
    }

    private LobbyItem summaryLocked() {
        LobbyItem item = new LobbyItem();
        item.tableId = state.tableId;
        item.name = state.name;
        item.variant = state.variant.name();
        item.variantLabel = state.variant.label();
        item.ante = state.ante;
        item.anteLabel = cfg.anteLabel();
        item.clubId = cfg.clubId;
        item.insuranceEnabled = cfg.insuranceEnabled;
        item.straddle = cfg.straddle;
        item.smallBlind = state.smallBlind;
        item.bigBlind = state.bigBlind;
        item.minBuyIn = state.minBuyIn;
        item.maxBuyIn = state.maxBuyIn;
        item.seated = state.countSeated();
        item.maxSeats = state.maxSeats;
        item.stage = state.stage.name();
        item.runningMinutes = (int) ((System.currentTimeMillis() - createdAt) / 60000);
        item.handNo = state.handNo;
        // 桌级入池率均值：三个地方（大厅/桌内/我的）口径必须一致
        int sum = 0;
        int n = 0;
        for (SeatState st : state.seats) {
            if (st.seated && st.handsPlayed > 0) {
                sum += st.vpipPercent();
                n++;
            }
        }
        item.avgVpip = n == 0 ? 0 : sum / n;
        return item;
    }

    public void close() {
        closed = true;
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
        }
        if (nextHandFuture != null) {
            nextHandFuture.cancel(false);
        }
        exec.shutdownNow();
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        new java.security.SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    public static class LobbyItem {
        public String tableId;
        public String name;
        public String variant;
        public String variantLabel;
        public long ante;
        public String anteLabel;
        public long clubId;
        public boolean insuranceEnabled;
        /** 是否启用 Straddle（UTG 2BB）。 */
        public boolean straddle;
        public long smallBlind;
        public long bigBlind;
        public long minBuyIn;
        public long maxBuyIn;
        public int seated;
        public int maxSeats;
        public String stage;
        public int avgVpip;
        public int runningMinutes;
        public long handNo;
    }
}
