package com.tgpoker.engine;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 胜率计算（Monte Carlo）。
 *
 * <p>这是教学"工具"Tab 与保险定价用的<b>另一套</b>需求：结算时一手牌只算 7 次，
 * 朴素实现足够；但算胜率要跑几万次随机发牌，所以这里用随机模拟。</p>
 *
 * <p>精度：2 万次抽样，胜率标准误约 0.35%，对教学场景足够。要更高精度就加次数，
 * 时间复杂度线性。</p>
 */
public final class EquityCalculator {

    private static final SecureRandom RND = new SecureRandom();

    private EquityCalculator() {
    }

    public static class Result {
        public double win;
        public double tie;
        public double lose;
        public int iterations;
        public int opponents;
        public String handName;
    }

    /**
     * @param hole       自己两张底牌
     * @param board      已知公牌（0/3/4/5 张）
     * @param opponents  对手数量（1~8）
     * @param variant    长牌 / 短牌
     * @param iterations 模拟次数
     */
    public static Result compute(Card[] hole, List<Card> board, int opponents,
                                 Variant variant, int iterations) {
        if (hole == null || hole.length < 2 || hole[0] == null || hole[1] == null) {
            throw new IllegalArgumentException("请先选两张底牌");
        }
        int ops = Math.max(1, Math.min(opponents, 8));
        int iters = Math.max(1000, Math.min(iterations, 200000));

        List<Card> base = new ArrayList<>();
        for (int s = 0; s < 4; s++) {
            for (int r = variant.minRank(); r <= 14; r++) {
                base.add(new Card(r, s));
            }
        }
        base.removeIf(c -> same(hole[0], c) || same(hole[1], c) || board.contains(c));

        Result res = new Result();
        res.opponents = ops;
        res.iterations = iters;

        int wins = 0;
        int ties = 0;
        int needBoard = 5 - board.size();

        for (int it = 0; it < iters; it++) {
            List<Card> pool = new ArrayList<>(base);
            Collections.shuffle(pool, RND);
            int idx = 0;
            // 对手手牌
            Card[][] oppHole = new Card[ops][2];
            for (int o = 0; o < ops; o++) {
                oppHole[o][0] = pool.get(idx++);
                oppHole[o][1] = pool.get(idx++);
            }
            List<Card> fullBoard = new ArrayList<>(board);
            for (int b = 0; b < needBoard; b++) {
                fullBoard.add(pool.get(idx++));
            }

            long mine = score(hole, fullBoard, variant);
            long best = mine;
            boolean tie = false;
            for (int o = 0; o < ops; o++) {
                long v = score(oppHole[o], fullBoard, variant);
                if (v > best) {
                    best = v;
                    tie = false;
                } else if (v == best) {
                    tie = true;
                }
            }
            if (mine > best) {
                wins++;
            } else if (mine == best) {
                if (tie) {
                    ties++;
                } else {
                    wins++;
                }
            }
        }

        res.win = (double) wins / iters;
        res.tie = (double) ties / iters;
        res.lose = 1 - res.win - res.tie;
        res.handName = board.size() >= 3
                ? HandEvaluator.name(score(hole, board, variant), variant)
                : "待公牌";
        return res;
    }

    private static long score(Card[] hole, List<Card> board, Variant variant) {
        List<Card> all = new ArrayList<>();
        all.add(hole[0]);
        all.add(hole[1]);
        all.addAll(board);
        if (all.size() < 5) {
            return 0;
        }
        Card[] arr = all.toArray(new Card[0]);
        return HandEvaluator.best(arr, arr.length, variant);
    }

    private static boolean same(Card a, Card b) {
        return a.r == b.r && a.s == b.s;
    }
}
