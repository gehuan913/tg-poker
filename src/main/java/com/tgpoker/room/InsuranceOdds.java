package com.tgpoker.room;

import com.tgpoker.engine.Card;
import com.tgpoker.engine.HandEvaluator;
import com.tgpoker.engine.Variant;

import java.util.ArrayList;
import java.util.List;

/**
 * 保险定价。
 *
 * <p>保险不是"抽水"，是一个独立 EV 子系统：庄家（俱乐部金库）按 outs 给赔率。
 * 这里的赔率表照竞品线上实测（文档 §7.6）抄下来，outs 越大赔率越低——
 * 因为反超概率越高。之所以要单独算 outs 而不用"胜率百分比"，是因为产品文案
 * 与结算口径都按 outs 表达。</p>
 *
 * <p>只支持 turn 圈（还剩一张牌）。river 圈没有"未发公牌"，不存在保险。</p>
 */
public final class InsuranceOdds {

    /** 长牌 outs 1..14 对应的赔率倍数。 */
    private static final double[] LONG_ODDS = {
            0, 30, 16, 10, 8, 6, 5, 4, 3.5, 3, 2.5, 2.2, 2, 1.8, 1.6
    };

    /** 短牌：牌少、反超概率高，同样 outs 的赔率要低一档。 */
    private static final double[] SHORT_ODDS = {
            0, 25, 14, 9, 7.2, 5.5, 4.6, 3.8, 3.3, 2.8, 2.4, 2.1, 1.9, 1.7, 1.55
    };

    private InsuranceOdds() {
    }

    public static double odds(Variant variant, int outs) {
        double[] table = variant == Variant.SHORT ? SHORT_ODDS : LONG_ODDS;
        if (outs <= 0) {
            return 0;
        }
        if (outs >= table.length) {
            return table[table.length - 1];
        }
        return table[outs];
    }

    public static class Quote {
        /** 领先方座位（买保险的人）。 */
        public int buyerSeat = -1;
        /** 落后方座位（被保险的反超方）。 */
        public int behindSeat = -1;
        public int outs;
        public int tieOuts;
        public double odds;
        public double tieOdds;
        /** 可投保上限（满池）。 */
        public long maxCoverage;
        /** 默认保额。 */
        public long coverage;
        /** 应付保费 = 保额 ÷ 赔率。 */
        public long premium;
        public long premiumTie;
        public String reason;
        public boolean available;

        public String oddsText() {
            return String.format("%.1f", odds);
        }
    }

    /**
     * 计算报价（两人单挑，兼容旧调用）。
     */
    public static Quote quote(Variant variant, Card[] holeA, Card[] holeB,
                              List<Card> board, long pot, int seatA, int seatB) {
        List<Card[]> holes = new ArrayList<>();
        holes.add(holeA);
        holes.add(holeB);
        List<Integer> seats = new ArrayList<>();
        seats.add(seatA);
        seats.add(seatB);
        return quoteMulti(variant, holes, seats, board, pot, 2);
    }

    /**
     * 多人 All-in 报价。
     *
     * <p>领先方按当前牌力选出，outs = 剩余牌里"任何一家反超领先方"的牌数。
     * 短牌按 2/3/4 人池分档：以 2 人为基准，3 人 ×0.92、4 人 ×0.84
     * （对应文档 §7.6 的 3.8 / 3.5 / 3.2 比例）。精确分档表仍需精算，见附录 C。</p>
     *
     * @param holes   各家底牌（已亮牌）
     * @param seats   对应座位号
     * @param players 参与人数（2~4）
     */
    public static Quote quoteMulti(Variant variant, List<Card[]> holes, List<Integer> seats,
                                   List<Card> board, long pot, int players) {
        Quote q = new Quote();
        if (board.size() != 4) {
            q.reason = "只在转牌圈（河牌前）提供保险";
            return q;
        }
        if (holes.size() < 2 || holes.size() > 4) {
            q.reason = "只支持 2~4 人 All-in";
            return q;
        }
        for (Card[] h : holes) {
            if (h == null || h[0] == null) {
                q.reason = "需要全部亮牌（All-in）才能定价";
                return q;
            }
        }
        if (variant == Variant.SHORT && players > 4) {
            q.reason = "短牌单池最多 4 人投保";
            return q;
        }

        // 找当前领先方
        int leader = 0;
        long best = -1;
        for (int i = 0; i < holes.size(); i++) {
            long sc = score(variant, holes.get(i), board, null);
            if (sc > best) {
                best = sc;
                leader = i;
            }
        }
        Card[] leaderHole = holes.get(leader);
        List<Card[]> behind = new ArrayList<>();
        for (int i = 0; i < holes.size(); i++) {
            if (i != leader) {
                behind.add(holes.get(i));
            }
        }

        List<Card> unseen = unseen(variant, holes, board);
        int outs = 0;
        int ties = 0;
        for (Card c : unseen) {
            long leaderScore = score(variant, leaderHole, board, c);
            boolean lose = false;
            boolean allTie = true;
            for (Card[] bh : behind) {
                long bs = score(variant, bh, board, c);
                if (bs > leaderScore) {
                    lose = true;
                    break;
                }
                if (bs != leaderScore) {
                    allTie = false;
                }
            }
            if (lose) {
                outs++;
            } else if (allTie) {
                ties++;
            }
        }

        q.buyerSeat = seats.get(leader);
        q.behindSeat = behind.size() == 1 ? seats.get(leader == 0 ? 1 : 0) : -1;
        q.outs = outs;
        q.tieOuts = ties;
        q.maxCoverage = Math.max(0, pot);
        if (outs < 1 || outs > 14) {
            q.reason = outs == 0 ? "对手没有反超 outs，无需保险" : "outs 超过 14，不提供保险";
            return q;
        }
        q.odds = odds(variant, outs, players);
        q.tieOdds = ties > 0 ? Math.max(1.2, q.odds * 0.8) : 0;
        q.coverage = q.maxCoverage;
        q.premium = ceilDiv(q.coverage, q.odds);
        q.premiumTie = 0;
        q.available = true;
        q.reason = "ok";
        return q;
    }

    /** 按人数分档的赔率（短牌 3/4 人池打折，长牌不区分人数）。 */
    public static double odds(Variant variant, int outs, int players) {
        double base = odds(variant, outs);
        if (variant != Variant.SHORT) {
            return base;
        }
        double factor = players >= 4 ? 0.84 : players == 3 ? 0.92 : 1.0;
        return Math.max(1.2, Math.round(base * factor * 10) / 10.0);
    }

    /** 计算某手牌在给定公牌（+可选河牌）下的最佳牌力。 */
    public static long score(Variant variant, Card[] hole, List<Card> board, Card extra) {
        List<Card> all = new ArrayList<>();
        if (hole != null && hole[0] != null) {
            all.add(hole[0]);
            all.add(hole[1]);
        }
        all.addAll(board);
        if (extra != null) {
            all.add(extra);
        }
        Card[] arr = all.toArray(new Card[0]);
        return HandEvaluator.best(arr, arr.length, variant);
    }

    /** 未知牌 = 全牌堆 − 各家底牌 − 公牌（按玩法取 52 或 36 张）。 */
    public static List<Card> unseen(Variant variant, List<Card[]> holes, List<Card> board) {
        int minRank = variant == Variant.SHORT ? 6 : 2;
        List<Card> all = new ArrayList<>();
        for (int s = 0; s < 4; s++) {
            for (int r = minRank; r <= 14; r++) {
                Card c = new Card(r, s);
                boolean used = board.contains(c);
                for (Card[] h : holes) {
                    if (contains(h, c)) {
                        used = true;
                        break;
                    }
                }
                if (!used) {
                    all.add(c);
                }
            }
        }
        return all;
    }

    /** 兼容两人调用。 */
    public static List<Card> unseen(Card[] holeA, Card[] holeB, List<Card> board) {
        List<Card[]> hs = new ArrayList<>();
        hs.add(holeA);
        hs.add(holeB);
        // 无法从入参判断玩法，保留旧行为：按公牌是否含 2~5 推断
        boolean shortDeck = board.stream().noneMatch(c -> c.r < 6);
        return unseen(shortDeck ? Variant.SHORT : Variant.LONG, hs, board);
    }

    private static boolean contains(Card[] arr, Card c) {
        return arr != null && arr[0] != null
                && (arr[0].r == c.r && arr[0].s == c.s || arr[1].r == c.r && arr[1].s == c.s);
    }

    private static long ceilDiv(long amount, double odds) {
        return (long) Math.ceil(amount / odds);
    }
}
