package com.tgpoker.engine;

import com.tgpoker.util.Money;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 德州扑克规则引擎。
 *
 * <p>设计约束：所有方法都是对 {@link TableState} 的纯状态推进，不碰数据库、不碰网络、
 * 不发消息。副作用（落库、广播）由 TableActor 在调用之后统一处理。这样才能保证
 * 一手牌可以离线重放、结果唯一。</p>
 */
public final class TexasEngine {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TexasEngine() {
    }

    // ---------------------------------------------------------------- 开牌

    public static void startHand(TableState s, long now, long actionMs) {
        startHand(s, now, actionMs, randomHex(32), List.of());
    }

    /**
     * 开一手牌。
     *
     * @param serverSeed  服务端种子（16 进制），会先公示其 SHA256
     * @param clientSeeds 本手参与洗牌的玩家种子，按座位顺序
     */
    public static void startHand(TableState s, long now, long actionMs,
                                 String serverSeed, List<String> clientSeeds) {
        List<Integer> active = s.activeSeats();
        if (active.size() < 2) {
            s.stage = Stage.WAITING;
            s.actingSeat = -1;
            return;
        }

        s.handNo++;
        s.handId = s.tableId + "-" + s.handNo;
        s.board.clear();
        s.pot = 0;
        s.currentBet = 0;
        s.minRaise = s.bigBlind;
        s.showdown = false;
        s.lastAggressor = -1;
        s.winners.clear();
        s.insurancePending = false;

        for (SeatState st : s.seats) {
            st.resetForHand();
        }

        s.buttonSeat = s.buttonSeat < 0 ? active.get(0) : nextSeated(s, s.buttonSeat);
        if (s.buttonSeat < 0 || !s.seats.get(s.buttonSeat).seated) {
            s.buttonSeat = active.get(0);
        }

        int sb;
        int bb;
        if (active.size() == 2) {
            sb = s.buttonSeat;
            bb = nextSeated(s, sb);
        } else {
            sb = nextSeated(s, s.buttonSeat);
            bb = nextSeated(s, sb);
        }

        // 可验证洗牌：先承诺 serverSeed 的 hash，结算后揭示种子；
        // 玩家种子参与派生，运营方无法提前枚举有利牌序。
        s.serverSeed = serverSeed;
        s.serverSeedHash = Deck.sha256Hex(serverSeed);
        s.clientSeeds.clear();
        if (clientSeeds != null) {
            s.clientSeeds.addAll(clientSeeds);
        }
        byte[] seed = Deck.deriveSeed(s.serverSeed, s.clientSeedConcat(), s.handNo);
        s.deck = new Deck(seed, s.variant);
        s.verifyCode = s.handNo + "#" + Deck.hmacHex(serverSeed, String.valueOf(s.handNo))
                .substring(0, 10).toUpperCase();

        // 前注先收（短牌的核心结构差异：不是盲注，是每人前注 + 按钮 2 倍）
        postAntes(s, active);

        postBlind(s, sb, s.smallBlind, "小盲");
        postBlind(s, bb, s.bigBlind, "大盲");
        s.currentBet = s.bigBlind;
        s.minRaise = s.bigBlind;
        s.straddleSeat = -1;

        // Straddle：UTG（大盲左手）强制下 2BB，相当于第三盲注。
        // 它不算 VPIP（属于强制投入），行动顺序也从 straddle 的下一位开始。
        if (s.straddle && s.straddleAmount > 0) {
            int st = nextSeated(s, bb);
            SeatState ss = s.seats.get(st);
            if (st != sb && st != bb && ss.inHand && ss.stack > 0) {
                postBlind(s, st, s.straddleAmount, "Straddle");
                s.currentBet = Math.max(s.currentBet, ss.bet);
                s.straddleSeat = st;
            }
        }

        for (int round = 0; round < 2; round++) {
            for (SeatState st : s.seats) {
                if (st.inHand) {
                    st.hole[round] = s.deck.next();
                }
            }
        }

        s.stage = Stage.PREFLOP;
        if (active.size() == 2) {
            s.actingSeat = sb;
        } else if (s.straddleSeat >= 0) {
            s.actingSeat = nextSeated(s, s.straddleSeat);
        } else {
            s.actingSeat = nextSeated(s, bb);
        }
        s.deadline = now + actionMs;
        s.logMsg("第 " + s.handNo + " 手开始，" + s.variant.label() + " 盲注 "
                + Money.fmt(s.smallBlind) + "/" + Money.fmt(s.bigBlind)
                + (s.ante > 0 ? " 前注 " + Money.fmt(s.ante) : "")
                + "，存证 " + s.verifyCode);
    }

    /** 前注：NONE 不收；EVERYONE 每人一份；BUTTON_DOUBLE 按钮位 2 倍。 */
    private static void postAntes(TableState s, List<Integer> active) {
        if (s.ante <= 0 || "NONE".equalsIgnoreCase(s.anteMode)) {
            return;
        }
        for (int seat : active) {
            SeatState st = s.seats.get(seat);
            boolean dbl = "BUTTON_DOUBLE".equalsIgnoreCase(s.anteMode) && seat == s.buttonSeat;
            long pay = Math.min(dbl ? s.ante * 2 : s.ante, st.stack);
            if (pay <= 0) {
                continue;
            }
            st.stack -= pay;
            st.contributed += pay;
            s.pot += pay;
            if (st.stack == 0) {
                st.allIn = true;
            }
        }
        s.logMsg("前注 " + Money.fmt(s.ante)
                + ("BUTTON_DOUBLE".equalsIgnoreCase(s.anteMode) ? "（按钮 2 倍）" : ""));
    }

    private static void postBlind(TableState s, int seat, long amount, String label) {
        SeatState st = s.seats.get(seat);
        long pay = Math.min(amount, st.stack);
        moveChips(s, st, pay);
        st.hasActed = false;
        s.logMsg(st.nick + " 下" + label + " " + Money.fmt(pay));
    }

    // ---------------------------------------------------------------- 行动

    public static void act(TableState s, int seat, ActionType type, long amount,
                           long now, long actionMs) {
        if (s.stage == Stage.WAITING || s.stage == Stage.SETTLED || s.stage == Stage.SHOWDOWN) {
            throw new GameException("当前阶段不能行动");
        }
        if (seat != s.actingSeat) {
            throw new GameException("还没轮到你");
        }
        SeatState st = s.seats.get(seat);
        if (!st.canAct()) {
            throw new GameException("当前状态不能行动");
        }

        long toCall = s.currentBet - st.bet;

        switch (type) {
            case FOLD: {
                st.folded = true;
                st.lastAction = "弃牌";
                st.lastAmount = 0;
                s.logMsg(st.nick + " 弃牌");
                break;
            }
            case CHECK: {
                if (toCall > 0) {
                    throw new GameException("面前有下注，不能过牌");
                }
                st.lastAction = "过牌";
                st.lastAmount = 0;
                s.logMsg(st.nick + " 过牌");
                break;
            }
            case CALL: {
                if (toCall <= 0) {
                    throw new GameException("无需跟注，请选择过牌");
                }
                long pay = Math.min(toCall, st.stack);
                moveChips(s, st, pay);
                st.lastAction = pay < toCall ? "全下" : "跟注";
                st.lastAmount = pay;
                s.logMsg(st.nick + " " + st.lastAction + " " + Money.fmt(pay));
                break;
            }
            case ALLIN: {
                long target = st.bet + st.stack;
                raiseTo(s, st, target);
                break;
            }
            case RAISE: {
                raiseTo(s, st, amount);
                break;
            }
            default:
                throw new GameException("未知动作");
        }

        st.hasActed = true;

        // VPIP / PFR 口径（文档 §2.6）：翻前跟注/加注/全下计入入池；
        // 大盲过牌不算入池，小盲补齐跟注算入池。
        if (s.stage == Stage.PREFLOP) {
            if (type == ActionType.CALL || type == ActionType.RAISE || type == ActionType.ALLIN) {
                st.vpipThisHand = true;
            }
            if (type == ActionType.RAISE || type == ActionType.ALLIN) {
                st.pfrThisHand = true;
            }
        }

        if (countNotFolded(s) == 1) {
            settleLastStanding(s);
            return;
        }

        int next = findNextActor(s, seat);
        if (next >= 0) {
            s.actingSeat = next;
            s.deadline = now + actionMs;
        } else {
            endStreet(s, now, actionMs);
        }
    }

    private static void raiseTo(TableState s, SeatState st, long target) {
        long maxTarget = st.bet + st.stack;
        if (target > maxTarget) {
            target = maxTarget;
        }
        if (target <= s.currentBet) {
            throw new GameException("加注额必须高于当前注额");
        }
        long raiseBy = target - s.currentBet;
        boolean isAllIn = target == maxTarget;
        if (raiseBy < s.minRaise && !isAllIn) {
            throw new GameException("最小加注到 " + Money.fmt(s.currentBet + s.minRaise));
        }

        long pay = target - st.bet;
        moveChips(s, st, pay);
        boolean reopened = raiseBy >= s.minRaise;
        if (reopened) {
            s.minRaise = raiseBy;
        }
        s.currentBet = Math.max(s.currentBet, target);
        s.lastAggressor = st.seat;

        st.lastAction = s.currentBet > target ? "跟注" : (isAllIn ? "全下" : (raiseBy >= s.minRaise ? "加注" : "全下"));
        st.lastAmount = target;
        s.logMsg(st.nick + " " + st.lastAction + " 到 " + Money.fmt(target));

        if (reopened) {
            for (SeatState o : s.seats) {
                if (o.seat != st.seat && o.canAct()) {
                    o.hasActed = false;
                }
            }
        }
    }

    private static void moveChips(TableState s, SeatState st, long pay) {
        if (pay <= 0) {
            return;
        }
        st.stack -= pay;
        st.bet += pay;
        st.contributed += pay;
        s.pot += pay;
        if (st.stack == 0) {
            st.allIn = true;
        }
    }

    private static int findNextActor(TableState s, int from) {
        int n = s.seats.size();
        for (int i = 1; i <= n; i++) {
            int idx = (from + i) % n;
            SeatState st = s.seats.get(idx);
            if (st.canAct() && (!st.hasActed || st.bet < s.currentBet)) {
                return idx;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- 街推进

    private static void endStreet(TableState s, long now, long actionMs) {
        for (SeatState st : s.seats) {
            st.bet = 0;
            st.hasActed = false;
        }
        s.currentBet = 0;
        s.minRaise = s.bigBlind;
        s.actingSeat = -1;

        if (countNotFolded(s) == 1) {
            settleLastStanding(s);
            return;
        }
        if (s.stage == Stage.RIVER) {
            showdown(s);
            return;
        }

        // 全员 All-in 时不能再走"发一条街再看"的路径：如果这手在转牌圈就全下，
        // 河牌一发保险就没得买了。所以先把牌发到转牌，再挂起保险窗口。
        if (countCanAct(s) <= 1) {
            while (s.stage != Stage.TURN && s.stage != Stage.RIVER && s.stage != Stage.SETTLED) {
                dealNextStreet(s);
            }
            if (s.stage == Stage.TURN && s.insuranceEnabled && countNotFolded(s) == 2
                    && s.board.size() == 4) {
                s.insurancePending = true;
                s.actingSeat = -1;
                s.deadline = now + 5000;
                s.logMsg("双方 All-in 亮牌，开启 5 秒保险窗口");
                return;
            }
            runOutToShowdown(s);
            return;
        }

        dealNextStreet(s);
        s.actingSeat = firstToActPostflop(s);
        s.deadline = now + actionMs;
    }

    /** 保险窗口结束后继续发河牌并摊牌。 */
    public static void continueRunout(TableState s) {
        if (!s.insurancePending) {
            return;
        }
        s.insurancePending = false;
        runOutToShowdown(s);
    }

    private static void dealNextStreet(TableState s) {
        switch (s.stage) {
            case PREFLOP:
                for (int i = 0; i < 3; i++) {
                    s.board.add(s.deck.next());
                }
                s.stage = Stage.FLOP;
                s.logMsg("翻牌 " + boardText(s));
                break;
            case FLOP:
                s.board.add(s.deck.next());
                s.stage = Stage.TURN;
                s.logMsg("转牌 " + boardText(s));
                break;
            case TURN:
                s.board.add(s.deck.next());
                s.stage = Stage.RIVER;
                s.logMsg("河牌 " + boardText(s));
                break;
            default:
                break;
        }
    }

    private static String boardText(TableState s) {
        StringBuilder sb = new StringBuilder();
        for (Card c : s.board) {
            sb.append(Card.rankStr(c.r)).append(Card.suitSymbol(c.s)).append(' ');
        }
        return sb.toString().trim();
    }

    private static int firstToActPostflop(TableState s) {
        int n = s.seats.size();
        for (int i = 1; i <= n; i++) {
            int idx = (s.buttonSeat + i) % n;
            if (s.seats.get(idx).canAct()) {
                return idx;
            }
        }
        return -1;
    }

    private static void runOutToShowdown(TableState s) {
        int guard = 0;
        while (s.stage != Stage.RIVER && guard++ < 5) {
            dealNextStreet(s);
        }
        showdown(s);
    }

    // ---------------------------------------------------------------- 结算

    private static void settleLastStanding(TableState s) {
        SeatState winner = null;
        for (SeatState st : s.seats) {
            if (st.inHand && !st.folded) {
                winner = st;
                break;
            }
        }
        if (winner != null) {
            winner.stack += s.pot;
            s.winners.add(winner.seat);
            s.logMsg(winner.nick + " 收下底池 " + Money.fmt(s.pot));
        }
        s.pot = 0;
        s.stage = Stage.SETTLED;
        s.actingSeat = -1;
        s.showdown = false;
    }

    private static void showdown(TableState s) {
        s.stage = Stage.SHOWDOWN;
        s.showdown = true;
        s.actingSeat = -1;

        // 注意：这里不预先 showCards。按技术方案 §9.4，未秀牌是玩家隐私，
        // 结算后只亮赢家（All-in 亮牌除外，由视图层按 allIn 单独放行），
        // 输家自动 muck —— 和真实牌桌一致，也避免"公开 serverSeed 就等于公开所有人底牌"。

        int n = s.seats.size();
        long[] contributed = new long[n];
        boolean[] folded = new boolean[n];
        for (SeatState st : s.seats) {
            contributed[st.seat] = st.contributed;
            folded[st.seat] = st.folded || !st.inHand;
        }

        List<Pot> pots = PotBuilder.build(contributed, folded);

        long[] scores = new long[n];
        for (SeatState st : s.seats) {
            if (st.inHand && !st.folded) {
                scores[st.seat] = evalSeven(s, st);
            }
        }

        for (Pot p : pots) {
            long best = -1;
            List<Integer> winners = new ArrayList<>();
            for (int idx : p.eligible) {
                if (scores[idx] > best) {
                    best = scores[idx];
                    winners.clear();
                    winners.add(idx);
                } else if (scores[idx] == best) {
                    winners.add(idx);
                }
            }
            if (winners.isEmpty() || best < 0) {
                continue;
            }
            long share = p.amount / winners.size();
            long rem = p.amount % winners.size();
            for (int i = 0; i < winners.size(); i++) {
                SeatState w = s.seats.get(winners.get(i));
                long gain = share + (i == 0 ? rem : 0);
                w.stack += gain;
                if (!s.winners.contains(w.seat)) {
                    s.winners.add(w.seat);
                }
                // 只让赢家亮牌；输家保持 muck
                w.showCards = true;
                s.logMsg(w.nick + " 赢得 " + Money.fmt(gain) + "（"
                        + HandEvaluator.name(best, s.variant) + "）");
            }
        }

        s.pot = 0;
        s.stage = Stage.SETTLED;
    }

    private static long evalSeven(TableState s, SeatState st) {
        int boardN = s.board.size();
        Card[] seven = new Card[2 + boardN];
        seven[0] = st.hole[0];
        seven[1] = st.hole[1];
        for (int i = 0; i < boardN; i++) {
            seven[2 + i] = s.board.get(i);
        }
        return HandEvaluator.best(seven, seven.length, s.variant);
    }

    // ---------------------------------------------------------------- 超时

    public static void timeoutAct(TableState s, long now, long actionMs) {
        if (s.actingSeat < 0) {
            return;
        }
        SeatState st = s.seats.get(s.actingSeat);
        if (!st.canAct()) {
            return;
        }
        boolean facing = s.currentBet - st.bet > 0;
        try {
            if (facing) {
                act(s, s.actingSeat, ActionType.FOLD, 0, now, actionMs);
                s.logMsg(st.nick + " 超时自动弃牌");
            } else {
                act(s, s.actingSeat, ActionType.CHECK, 0, now, actionMs);
                s.logMsg(st.nick + " 超时自动过牌");
            }
        } catch (GameException ignore) {
            // 状态已经变化，忽略
        }
    }

    // ---------------------------------------------------------------- 工具

    private static int nextSeated(TableState s, int from) {
        int n = s.seats.size();
        for (int i = 1; i <= n; i++) {
            int idx = (from + i) % n;
            SeatState st = s.seats.get(idx);
            if (st.seated && !st.sittingOut && st.stack > 0) {
                return idx;
            }
        }
        return -1;
    }

    private static int countNotFolded(TableState s) {
        int n = 0;
        for (SeatState st : s.seats) {
            if (st.inHand && !st.folded) {
                n++;
            }
        }
        return n;
    }

    private static int countCanAct(TableState s) {
        int n = 0;
        for (SeatState st : s.seats) {
            if (st.canAct()) {
                n++;
            }
        }
        return n;
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}
