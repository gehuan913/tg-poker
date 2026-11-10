package com.tgpoker.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 牌型评估：把 5~7 张牌的最佳 5 张压成一个可比较的 long。
 *
 * <p>编码：category 占高位，然后 5 个 4 位 tiebreaker（0~14）。直接比大小即可。</p>
 *
 * <p><b>长牌与短牌的 category 编号不同，不能混用。</b>短牌规则里
 * 同花 &gt; 葫芦、三条 &gt; 顺子、最小顺子是 A6789。这里用两套编号把"比较"
 * 这件事交给数值大小，调用方不需要知道规则差异。</p>
 */
public final class HandEvaluator {

    // ---- 长牌序号 -------------------------------------------------
    public static final int HIGH_CARD = 0;
    public static final int PAIR = 1;
    public static final int TWO_PAIR = 2;
    public static final int TRIPS = 3;
    public static final int STRAIGHT = 4;
    public static final int FLUSH = 5;
    public static final int FULL_HOUSE = 6;
    public static final int QUADS = 7;
    public static final int STRAIGHT_FLUSH = 8;

    private static final String[] LONG_NAMES = {
            "高牌", "一对", "两对", "三条", "顺子", "同花", "葫芦", "四条", "同花顺"
    };
    /** 短牌：顺子 &lt; 三条 &lt; 葫芦 &lt; 同花。 */
    private static final String[] SHORT_NAMES = {
            "高牌", "一对", "两对", "顺子", "三条", "葫芦", "同花", "四条", "同花顺"
    };

    private HandEvaluator() {
    }

    public static long best(Card[] cards, int n) {
        return best(cards, n, Variant.LONG);
    }

    /** 从 n 张牌里取最好的 5 张（n = 5/6/7）。 */
    public static long best(Card[] cards, int n, Variant variant) {
        long best = -1;
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                for (int c = b + 1; c < n; c++) {
                    for (int d = c + 1; d < n; d++) {
                        for (int e = d + 1; e < n; e++) {
                            long sc = score5(cards[a], cards[b], cards[c], cards[d], cards[e], variant);
                            if (sc > best) {
                                best = sc;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    public static long score5(Card a, Card b, Card c, Card d, Card e) {
        return score5(a, b, c, d, e, Variant.LONG);
    }

    public static long score5(Card a, Card b, Card c, Card d, Card e, Variant variant) {
        Card[] cs = {a, b, c, d, e};
        int[] cnt = new int[15];
        for (Card x : cs) {
            cnt[x.r]++;
        }

        boolean flush = a.s == b.s && b.s == c.s && c.s == d.s && d.s == e.s;

        int[] ranks = {a.r, b.r, c.r, d.r, e.r};
        Arrays.sort(ranks);

        boolean straight = false;
        int straightHigh = 0;
        if (ranks[0] + 1 == ranks[1] && ranks[1] + 1 == ranks[2]
                && ranks[2] + 1 == ranks[3] && ranks[3] + 1 == ranks[4]) {
            straight = true;
            straightHigh = ranks[4];
        } else if (variant == Variant.LONG
                && ranks[0] == 2 && ranks[1] == 3 && ranks[2] == 4
                && ranks[3] == 5 && ranks[4] == 14) {
            // 长牌轮子顺 2-3-4-5-A，A 当 1
            straight = true;
            straightHigh = 5;
        } else if (variant == Variant.SHORT
                && ranks[0] == 6 && ranks[1] == 7 && ranks[2] == 8
                && ranks[3] == 9 && ranks[4] == 14) {
            // 短牌最小顺 A-6-7-8-9
            straight = true;
            straightHigh = 9;
        }

        // (count, rank) 按 count 降序、rank 降序
        List<int[]> groups = new ArrayList<>();
        for (int r = 14; r >= 2; r--) {
            if (cnt[r] > 0) {
                groups.add(new int[]{cnt[r], r});
            }
        }
        groups.sort((x, y) -> x[0] != y[0] ? y[0] - x[0] : y[1] - x[1]);

        int cat;
        int[] tk = new int[5];

        boolean isQuads = groups.get(0)[0] == 4;
        boolean isFull = groups.get(0)[0] == 3 && groups.size() > 1 && groups.get(1)[0] == 2;
        boolean isTrips = groups.get(0)[0] == 3;
        boolean isTwoPair = groups.get(0)[0] == 2 && groups.size() > 1 && groups.get(1)[0] == 2;
        boolean isPair = groups.get(0)[0] == 2;

        if (variant == Variant.SHORT) {
            // 短牌：顺子 3 < 三条 4 < 葫芦 5 < 同花 6
            // 注意这里用的是"短牌自己的序号"，不能复用长牌常量（长牌 FLUSH=5 / FULL_HOUSE=6）。
            if (straight && flush) {
                cat = 8;
                tk[0] = straightHigh;
            } else if (isQuads) {
                cat = 7;
                tk[0] = groups.get(0)[1];
                tk[1] = groups.get(1)[1];
            } else if (flush) {
                cat = 6;
                for (int i = 0; i < 5; i++) {
                    tk[i] = ranks[4 - i];
                }
            } else if (isFull) {
                cat = 5;
                tk[0] = groups.get(0)[1];
                tk[1] = groups.get(1)[1];
            } else if (isTrips) {
                cat = 4;
                tk[0] = groups.get(0)[1];
                tk[1] = groups.get(1)[1];
                tk[2] = groups.get(2)[1];
            } else if (straight) {
                cat = 3;
                tk[0] = straightHigh;
            } else if (isTwoPair) {
                cat = 2;
                tk[0] = groups.get(0)[1];
                tk[1] = groups.get(1)[1];
                tk[2] = groups.get(2)[1];
            } else if (isPair) {
                cat = 1;
                tk[0] = groups.get(0)[1];
                tk[1] = groups.get(1)[1];
                tk[2] = groups.get(2)[1];
                tk[3] = groups.get(3)[1];
            } else {
                cat = 0;
                for (int i = 0; i < 5; i++) {
                    tk[i] = ranks[4 - i];
                }
            }
        } else {
            if (straight && flush) {
                cat = STRAIGHT_FLUSH;
                tk[0] = straightHigh;
            } else if (isQuads) {
                cat = QUADS;
                tk[0] = groups.get(0)[1];
                tk[1] = groups.get(1)[1];
            } else if (isFull) {
                cat = FULL_HOUSE;
                tk[0] = groups.get(0)[1];
                tk[1] = groups.get(1)[1];
            } else if (flush) {
                cat = FLUSH;
                for (int i = 0; i < 5; i++) {
                    tk[i] = ranks[4 - i];
                }
            } else if (straight) {
                cat = STRAIGHT;
                tk[0] = straightHigh;
            } else if (isTrips) {
                cat = TRIPS;
                tk[0] = groups.get(0)[1];
                tk[1] = groups.get(1)[1];
                tk[2] = groups.get(2)[1];
            } else if (isTwoPair) {
                cat = TWO_PAIR;
                tk[0] = groups.get(0)[1];
                tk[1] = groups.get(1)[1];
                tk[2] = groups.get(2)[1];
            } else if (isPair) {
                cat = PAIR;
                tk[0] = groups.get(0)[1];
                tk[1] = groups.get(1)[1];
                tk[2] = groups.get(2)[1];
                tk[3] = groups.get(3)[1];
            } else {
                cat = HIGH_CARD;
                for (int i = 0; i < 5; i++) {
                    tk[i] = ranks[4 - i];
                }
            }
        }

        return ((long) cat << 20)
                | ((long) tk[0] << 16)
                | ((long) tk[1] << 12)
                | ((long) tk[2] << 8)
                | ((long) tk[3] << 4)
                | tk[4];
    }

    public static int category(long score) {
        return (int) (score >>> 20) & 0xF;
    }

    public static String name(long score) {
        return name(score, Variant.LONG);
    }

    public static String name(long score, Variant variant) {
        int c = category(score);
        if (c < 0 || c > 8) {
            return "-";
        }
        return variant == Variant.SHORT ? SHORT_NAMES[c] : LONG_NAMES[c];
    }
}
