package com.tgpoker.niuniu;

import com.tgpoker.engine.Card;
import com.tgpoker.service.WalletService;
import com.tgpoker.util.Money;

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
 * 一张牛牛桌。
 *
 * <p>1 庄 3 闲，三个闲家分别与庄家独立结算。庄家轮换；闲家下注后等开牌。
 * 与德州一样用「1 桌 1 线程」，不同的是规则：牛牛没有行动轮，只有下注窗口。</p>
 *
 * <p>关键风控：下注上限 = min(闲家筹码, 庄家筹码 ÷ 5)——因为最大倍数是 5 倍，
 * 不预留就会出现"庄家赔不起"。</p>
 */
public class NiuniuRoom {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(NiuniuRoom.class);

    public static final int MAX_SEATS = 4;
    private static final int BET_SECONDS = 8;
    private static final int NEXT_ROUND_MS = 4000;

    public static class Seat {
        public int seat;
        public long userId;
        public String nick = "";
        public String avatar = "";
        public boolean seated;
        public long stack;
        public long bet;
        public boolean betPlaced;
        /** 本局牌，未开牌为 null。 */
        public NiuniuEngine.Hand hand;
        /** 本局输赢。 */
        public long result;
        /** 是否申请上庄（排队）。 */
        public boolean wantBanker;
        /** 申请顺序号，越小越优先。 */
        public long applySeq;
    }

    public static class View {
        public String tableId;
        public String name;
        public String stage;
        public long deadline;
        public long serverTime;
        public int bankerSeat = -1;
        public long roundNo;
        public int mySeat = -1;
        public long maxBet;
        public int betSeconds = BET_SECONDS;
        public List<Map<String, Object>> seats = new ArrayList<>();
        public List<String> log = new ArrayList<>();
        public boolean canBet;
        /** 我是否已申请上庄。 */
        public boolean wantBanker;
        /** 上庄最低桌内资金（micro-USDT）。 */
        public long bankerMin;
        /** 上庄排队人数。 */
        public int queueCount;
        /** 上局开出牛牛的座位，-1 表示没有。 */
        public int lastNiuNiuSeat = -1;
        /** 是否允许申请上庄（在座且不是庄家）。 */
        public boolean canApplyBanker;
    }

    private final String tableId;
    private final String name;
    private final long minBuyIn;
    private final long maxBuyIn;
    private final Seat[] seats = new Seat[MAX_SEATS];
    private final List<String> log = new ArrayList<>();
    private final WalletService walletService;
    private final ScheduledExecutorService timer;
    private final ExecutorService exec;
    /** 结算回调：把本局每人战绩交给上层落库（null 表示只跑内存，不记账）。 */
    private final java.util.function.Consumer<java.util.List<com.tgpoker.model.NiuniuResult>> recorder;

    private volatile String stage = "WAITING";
    private volatile long deadline = 0;
    private volatile int bankerSeat = -1;
    private volatile long roundNo = 0;
    private ScheduledFuture<?> pending;
    private volatile boolean closed;

    /** 上庄最低桌内资金：要能覆盖"最大 5 倍"的赔付。 */
    private final long bankerMinStack;
    /** 上局开出牛牛的座位，下局"选庄"时优先。 */
    private volatile int lastNiuNiuSeat = -1;
    /** 上庄申请序号，FIFO 排队用。 */
    private long applyCounter = 0;

    public NiuniuRoom(String tableId, String name, long minBuyIn, long maxBuyIn, long bankerMinStack,
                      WalletService walletService, ScheduledExecutorService timer,
                      java.util.function.Consumer<java.util.List<com.tgpoker.model.NiuniuResult>> recorder) {
        this.tableId = tableId;
        this.name = name;
        this.minBuyIn = minBuyIn;
        this.maxBuyIn = maxBuyIn;
        this.bankerMinStack = bankerMinStack;
        this.walletService = walletService;
        this.timer = timer;
        this.recorder = recorder;
        for (int i = 0; i < MAX_SEATS; i++) {
            Seat s = new Seat();
            s.seat = i;
            seats[i] = s;
        }
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "niuniu-" + tableId);
            t.setDaemon(true);
            return t;
        });
    }

    private void submit(String name, Runnable task) {
        if (closed) {
            return;
        }
        exec.execute(() -> {
            try {
                synchronized (this) {
                    task.run();
                }
            } catch (Exception e) {
                logMsg(name + " 失败：" + e.getMessage());
                LOG.warn("牛牛 {} 执行异常", name, e);
            }
        });
    }

    /**
     * 入座并买入；第一个入座的人自动成为庄家。
     */
    public void sit(long userId, String nick, String avatar, long buyIn, int preferSeat) {
        submit("sit", () -> {
            for (Seat s : seats) {
                if (s.seated && s.userId == userId) {
                    throw new IllegalStateException("你已经在座上了");
                }
            }
            if (buyIn < minBuyIn || buyIn > maxBuyIn) {
                throw new IllegalStateException("买入需在 " + Money.fmt(minBuyIn) + " ~ " + Money.fmt(maxBuyIn));
            }
            Seat target = null;
            if (preferSeat >= 0 && preferSeat < MAX_SEATS && !seats[preferSeat].seated) {
                target = seats[preferSeat];
            }
            if (target == null) {
                for (Seat s : seats) {
                    if (!s.seated) {
                        target = s;
                        break;
                    }
                }
            }
            if (target == null) {
                throw new IllegalStateException("牌桌已满");
            }
            String bizId = "nnbuyin-" + tableId + "-" + userId + "-" + System.currentTimeMillis();
            if (!walletService.buyIn(userId, buyIn, bizId)) {
                throw new IllegalStateException("钱包余额不足");
            }
            target.seated = true;
            target.userId = userId;
            target.nick = nick;
            target.avatar = avatar == null ? "" : avatar;
            target.stack = buyIn;
            target.bet = 0;
            target.hand = null;
            target.result = 0;
            logMsg(nick + " 入座 " + target.seat + " 号位");
            if (bankerSeat < 0) {
                bankerSeat = target.seat;
            }
            scheduleNextRound(800);
        });
    }

    /**
     * 离桌退筹码；本局已下注时不允许离桌（等开牌结算完再走）。
     */
    public void leave(long userId) {
        submit("leave", () -> {
            Seat s = seatOf(userId);
            if (s == null) {
                return;
            }
            if ("BETTING".equals(stage) && s.betPlaced) {
                throw new IllegalStateException("本局已下注，开牌后才能离桌");
            }
            long amount = s.stack;
            if (amount > 0) {
                walletService.cashOut(userId, amount, "nncashout-" + System.currentTimeMillis() + "-" + userId);
            }
            logMsg(s.nick + " 离桌，带走 " + Money.fmt(amount));
            s.seated = false;
            s.wantBanker = false;
            s.userId = 0;
            s.nick = "";
            s.stack = 0;
            s.bet = 0;
            s.hand = null;
            if (s.seat == bankerSeat) {
                bankerSeat = -1;
            }
        });
    }

    /**
     * 闲家下注。
     *
     * <p>上限 = min(闲家筹码, 庄家筹码 ÷ 5)：最大赔率是 5 倍，不预留就会出现庄家赔不起。
     * 所有闲家都下注后立即开牌，不等满 8 秒。</p>
     */
    public void bet(long userId, long amount) {
        submit("bet", () -> {
            if (!"BETTING".equals(stage)) {
                throw new IllegalStateException("当前不是下注时间");
            }
            Seat s = seatOf(userId);
            if (s == null) {
                throw new IllegalStateException("你不在座位上");
            }
            if (s.seat == bankerSeat) {
                throw new IllegalStateException("庄家不能下注");
            }
            if (s.betPlaced) {
                throw new IllegalStateException("本局已下注");
            }
            long max = maxBetFor(s);
            if (amount <= 0 || amount > max) {
                throw new IllegalStateException("下注需在 0.1 ~ " + Money.fmt(max) + " 之间");
            }
            s.bet = amount;
            s.betPlaced = true;
            logMsg(s.nick + " 下注 " + Money.fmt(amount));
            if (allBetsPlaced()) {
                settle();
            }
        });
    }

    private long maxBetFor(Seat s) {
        Seat banker = bankerSeat >= 0 ? seats[bankerSeat] : null;
        long bankerCap = banker == null ? 0 : banker.stack / 5;
        return Math.max(0, Math.min(s.stack, bankerCap));
    }

    private boolean allBetsPlaced() {
        int need = 0;
        for (Seat s : seats) {
            if (s.seated && s.stack > 0 && s.seat != bankerSeat) {
                need++;
                if (!s.betPlaced) {
                    return false;
                }
            }
        }
        return need > 0;
    }

    private void scheduleNextRound(long delayMs) {
        if (closed) {
            return;
        }
        if (pending != null) {
            pending.cancel(false);
        }
        pending = timer.schedule(() -> submit("nextRound", this::startBetting), delayMs, TimeUnit.MILLISECONDS);
    }

    private void startBetting() {
        if (countActive() < 2) {
            stage = "WAITING";
            return;
        }
        if (!resolveBanker()) {
            stage = "WAITING";
            logMsg("暂无满足条件的庄家（需 " + Money.fmt(bankerMinStack) + " 以上），等待上庄申请");
            scheduleNextRound(3000);
            return;
        }
        for (Seat s : seats) {
            s.bet = 0;
            s.betPlaced = false;
            s.hand = null;
            s.result = 0;
        }
        stage = "BETTING";
        deadline = System.currentTimeMillis() + BET_SECONDS * 1000L;
        logMsg("第 " + (roundNo + 1) + " 局开始下注，庄家 " + seats[bankerSeat].nick);
        if (pending != null) {
            pending.cancel(false);
        }
        pending = timer.schedule(() -> submit("timeout", () -> {
            if ("BETTING".equals(stage)) {
                settle();
            }
        }), BET_SECONDS * 1000L, TimeUnit.MILLISECONDS);
    }

    /**
     * 决定本局庄家。
     *
     * <p>优先级：连庄（资金够且未下庄）→ 上局牛牛玩家的"选庄"→ 先申请者 FIFO → 系统兜底指定。
     * 庄家必须满足 bankerMinStack：最大 5 倍赔付，资金不够就是穿仓。</p>
     */
    private boolean resolveBanker() {
        Seat cur = bankerSeat >= 0 ? seats[bankerSeat] : null;
        if (cur != null && cur.seated && cur.wantBanker) {
            if (cur.stack < bankerMinStack) {
                if (tryTopUp(cur)) {
                    return true;
                }
                cur.wantBanker = false;
                logMsg(cur.nick + " 资金不足，自动下庄");
            } else {
                return true;
            }
        }

        Seat best = null;
        for (Seat s : seats) {
            if (!s.seated || !s.wantBanker || s.stack <= 0) {
                continue;
            }
            if (s.stack < bankerMinStack && !tryTopUp(s)) {
                continue;
            }
            if (best == null) {
                best = s;
                continue;
            }
            boolean sNiu = s.seat == lastNiuNiuSeat;
            boolean bNiu = best.seat == lastNiuNiuSeat;
            if (sNiu && !bNiu) {
                best = s;
            } else if (sNiu == bNiu && s.applySeq < best.applySeq) {
                best = s;
            }
        }
        if (best != null) {
            bankerSeat = best.seat;
            best.wantBanker = true;
            logMsg(best.nick + " 上庄（桌内 " + Money.fmt(best.stack) + "）");
            return true;
        }

        // 兜底：没人申请时指定筹码最多的在座玩家，避免桌停摆
        Seat fallback = null;
        for (Seat s : seats) {
            if (s.seated && s.stack >= bankerMinStack && (fallback == null || s.stack > fallback.stack)) {
                fallback = s;
            }
        }
        if (fallback != null) {
            bankerSeat = fallback.seat;
            fallback.wantBanker = true;
            logMsg("无人申请上庄，系统指定 " + fallback.nick);
            return true;
        }
        return false;
    }

    /** 补庄：从钱包自动补到最低上庄资金，失败则不能坐庄。 */
    private boolean tryTopUp(Seat s) {
        long target = Math.min(bankerMinStack, maxBuyIn);
        long need = target - s.stack;
        if (need <= 0) {
            return true;
        }
        long balance = walletService.balance(s.userId);
        if (balance < need) {
            return false;
        }
        String bizId = "nnrebuy-" + tableId + "-" + s.userId + "-" + System.currentTimeMillis();
        if (!walletService.buyIn(s.userId, need, bizId)) {
            return false;
        }
        s.stack += need;
        logMsg(s.nick + " 自动补庄 " + Money.fmt(need));
        return true;
    }

    /**
     * 申请 / 取消上庄。
     *
     * <p>下庄时若当前不在牌局中（WAITING/SETTLED），立即生效；否则本局打完再生效。</p>
     */
    public void applyBanker(long userId, boolean on) {
        submit("applyBanker", () -> {
            Seat s = seatOf(userId);
            if (s == null) {
                throw new IllegalStateException("你不在座位上");
            }
            if (on) {
                if (s.seat == bankerSeat) {
                    return;
                }
                if (s.wantBanker) {
                    return;
                }
                s.wantBanker = true;
                s.applySeq = ++applyCounter;
                logMsg(s.nick + " 申请上庄");
                if ("WAITING".equals(stage) && countActive() >= 2) {
                    scheduleNextRound(800);
                }
            } else {
                s.wantBanker = false;
                logMsg(s.nick + " 取消上庄");
                if (s.seat == bankerSeat && ("WAITING".equals(stage) || "SETTLED".equals(stage))) {
                    bankerSeat = -1;
                }
            }
        });
    }

    private void settle() {
        if (!"BETTING".equals(stage)) {
            return;
        }
        int bettors = 0;
        for (Seat s : seats) {
            if (s.seated && s.seat != bankerSeat && s.bet > 0 && s.stack > 0) {
                bettors++;
            }
        }
        if (bettors == 0) {
            // 没人下注就空转一局，不发牌也不结算，避免刷出无意义的牌局记录
            stage = "WAITING";
            logMsg("无人下注，本局跳过");
            scheduleNextRound(1500);
            return;
        }
        roundNo++;
        List<Card> deck = NiuniuEngine.shuffledDeck();
        int idx = 0;
        Seat banker = bankerSeat >= 0 ? seats[bankerSeat] : null;
        if (banker == null) {
            stage = "WAITING";
            return;
        }
        banker.hand = deal(deck, idx);
        idx += 5;

        long bankerNet = 0;
        java.util.List<com.tgpoker.model.NiuniuResult> rows = new java.util.ArrayList<>();

        for (Seat s : seats) {
            if (!s.seated || s.seat == bankerSeat || s.bet <= 0 || s.stack <= 0) {
                continue;
            }
            s.hand = deal(deck, idx);
            idx += 5;
            int cmp = NiuniuEngine.compare(s.hand, banker.hand);
            long stake = s.bet;
            if (cmp > 0) {
                long win = stake * s.hand.multiplier();
                banker.stack -= win;
                s.stack += win;
                s.result = win;
                bankerNet -= win;
                logMsg(s.nick + " " + s.hand.niuLabel() + " 赢 " + Money.fmt(win));
            } else if (cmp < 0) {
                long lose = stake * banker.hand.multiplier();
                s.stack -= lose;
                banker.stack += lose;
                s.result = -lose;
                bankerNet += lose;
                logMsg(s.nick + " " + s.hand.niuLabel() + " 输 " + Money.fmt(lose));
            } else {
                s.result = 0;
                logMsg(s.nick + " 与庄家平局，退回下注");
            }
            rows.add(row(s, false, stake));
        }
        banker.result = bankerNet;
        rows.add(row(banker, true, 0L));

        // 记录本局开出牛牛的人，下一局选庄时优先
        lastNiuNiuSeat = banker.hand != null && banker.hand.niu == 10 ? banker.seat : -1;
        for (Seat s : seats) {
            if (s.hand != null && s.hand.niu == 10) {
                lastNiuNiuSeat = s.seat;
            }
        }

        stage = "SETTLED";
        logMsg("开牌：庄家 " + banker.hand.niuLabel());
        if (recorder != null) {
            try {
                recorder.accept(rows);
            } catch (Exception e) {
                LOG.warn("牛牛战绩落库失败 table={} round={}", tableId, roundNo, e);
            }
        }
        scheduleNextRound(NEXT_ROUND_MS);
    }

    /** 组装一条战绩行，供上层落库。 */
    private com.tgpoker.model.NiuniuResult row(Seat s, boolean banker, long bet) {
        com.tgpoker.model.NiuniuResult r = new com.tgpoker.model.NiuniuResult();
        r.setTableId(tableId);
        r.setRoundNo(roundNo);
        r.setUserId(s.userId);
        r.setSeat(s.seat);
        r.setBanker(banker ? 1 : 0);
        r.setBet(bet);
        r.setNet(s.result);
        r.setCreatedAt(java.time.LocalDateTime.now());
        return r;
    }

    private NiuniuEngine.Hand deal(List<Card> deck, int from) {
        Card[] hand = new Card[5];
        for (int i = 0; i < 5; i++) {
            hand[i] = deck.get(from + i);
        }
        return NiuniuEngine.evaluate(hand);
    }

    private int countActive() {
        int n = 0;
        for (Seat s : seats) {
            if (s.seated && s.stack > 0) {
                n++;
            }
        }
        return n;
    }

    private int nextActiveSeat(int from) {
        for (int i = 1; i <= MAX_SEATS; i++) {
            int idx = Math.floorMod(from + i, MAX_SEATS);
            if (seats[idx].seated && seats[idx].stack > 0) {
                return idx;
            }
        }
        return -1;
    }

    private Seat seatOf(long userId) {
        for (Seat s : seats) {
            if (s.seated && s.userId == userId) {
                return s;
            }
        }
        return null;
    }

    private void logMsg(String msg) {
        log.add(msg);
        while (log.size() > 20) {
            log.remove(0);
        }
    }

    /** 当前牌桌视图（含各座位牌、牛型、倍数、输赢）。 */
    public synchronized View view(long userId) {
        View v = new View();
        v.tableId = tableId;
        v.name = name;
        v.stage = stage;
        v.deadline = deadline;
        v.serverTime = System.currentTimeMillis();
        v.bankerSeat = bankerSeat;
        v.roundNo = roundNo;
        v.log = new ArrayList<>(log);

        Seat me = seatOf(userId);
        if (me != null) {
            v.mySeat = me.seat;
            v.maxBet = maxBetFor(me);
            v.canBet = "BETTING".equals(stage) && me.seat != bankerSeat && !me.betPlaced;
            v.wantBanker = me.wantBanker;
            v.canApplyBanker = me.seat != bankerSeat;
        }
        v.bankerMin = bankerMinStack;
        v.lastNiuNiuSeat = lastNiuNiuSeat;
        int queue = 0;
        for (Seat s : seats) {
            if (s.seated && s.wantBanker && s.seat != bankerSeat) {
                queue++;
            }
        }
        v.queueCount = queue;

        for (Seat s : seats) {
            Map<String, Object> m = new HashMap<>();
            m.put("seat", s.seat);
            m.put("userId", s.userId == 0 ? null : s.userId);
            m.put("nick", s.nick);
            m.put("avatar", s.avatar);
            m.put("seated", s.seated);
            m.put("stack", s.stack);
            m.put("stackText", Money.fmt(s.stack));
            m.put("bet", s.bet);
            m.put("betText", Money.fmt(s.bet));
            m.put("betPlaced", s.betPlaced);
            m.put("banker", s.seat == bankerSeat);
            m.put("result", s.result);
            m.put("resultText", Money.fmtSigned(s.result));
            if (s.hand != null) {
                List<String> codes = new ArrayList<>();
                for (Card c : s.hand.cards) {
                    codes.add(c.code());
                }
                m.put("cards", codes);
                m.put("niu", s.hand.niu);
                m.put("niuLabel", s.hand.niuLabel());
                m.put("multiplier", s.hand.multiplier());
            }
            v.seats.add(m);
        }
        return v;
    }

    public List<String> log() {
        return new ArrayList<>(log);
    }

    public String getTableId() {
        return tableId;
    }

    public void close() {
        closed = true;
        if (pending != null) {
            pending.cancel(false);
        }
        exec.shutdownNow();
    }
}
