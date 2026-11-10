package com.tgpoker.niuniu;

import com.tgpoker.engine.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * 牛牛（独立引擎，不塞进德州状态机）。
 *
 * <p>规则：5 张牌，A=1、JQK=10。先找 3 张凑成 10 的倍数，剩下 2 张的和取个位就是牛数；
 * 个位为 0 是牛牛（记 10），找不到三张组合是无牛（记 0）。</p>
 *
 * <p>比较：牛数大者胜；同牛数比最大单牌（K&gt;Q&gt;J&gt;10&gt;…&gt;A）；再比花色（♠&gt;♥&gt;♣&gt;♦）。
 * 不算五小牛/五花牛/炸弹牛。</p>
 *
 * <p>倍数：牛牛 5 / 牛 9 4 / 牛 8 3 / 牛 7 2 / 牛 6 及以下 1。
 * 闲赢按闲家倍数赔，庄赢按庄家倍数收。</p>
 */
public final class NiuniuEngine {

    /** 花色权重：♠ 0 &gt; ♥ 1 &gt; ♣ 2 &gt; ♦ 3，数值越小越大。 */
    private static final int[] SUIT_ORDER = {0, 1, 2, 3};

    private NiuniuEngine() {
    }

    public static class Hand {
        public Card[] cards = new Card[5];
        /** 0=无牛，1..9=牛几，10=牛牛。 */
        public int niu;
        public int maxRank;
        public int maxSuit;

        public String niuLabel() {
            if (niu == 10) {
                return "牛牛";
            }
            return niu == 0 ? "无牛" : "牛" + niu;
        }

        public int multiplier() {
            if (niu == 10) {
                return 5;
            }
            if (niu == 9) {
                return 4;
            }
            if (niu == 8) {
                return 3;
            }
            if (niu == 7) {
                return 2;
            }
            return 1;
        }
    }

    public static Hand evaluate(Card[] cards) {
        if (cards == null || cards.length != 5) {
            throw new IllegalArgumentException("牛牛必须 5 张牌");
        }
        Hand h = new Hand();
        h.cards = cards;
        int[] pt = new int[5];
        for (int i = 0; i < 5; i++) {
            pt[i] = points(cards[i]);
        }

        int bestNiu = -1;
        for (int a = 0; a < 5; a++) {
            for (int b = a + 1; b < 5; b++) {
                for (int c = b + 1; c < 5; c++) {
                    if ((pt[a] + pt[b] + pt[c]) % 10 != 0) {
                        continue;
                    }
                    int rest = 0;
                    for (int i = 0; i < 5; i++) {
                        if (i != a && i != b && i != c) {
                            rest += pt[i];
                        }
                    }
                    int niu = rest % 10;
                    if (niu == 0) {
                        niu = 10;
                    }
                    if (niu > bestNiu) {
                        bestNiu = niu;
                    }
                }
            }
        }
        h.niu = bestNiu < 0 ? 0 : bestNiu;

        // 最大单牌：先比点数，再比花色
        int mr = -1;
        int ms = 4;
        for (Card c : cards) {
            if (c.r > mr || (c.r == mr && suitWeight(c.s) < suitWeight(ms))) {
                mr = c.r;
                ms = c.s;
            }
        }
        h.maxRank = mr;
        h.maxSuit = ms;
        return h;
    }

    /** 比较两手牌：&gt;0 表示 A 赢，&lt;0 表示 B 赢，0 平。 */
    public static int compare(Hand a, Hand b) {
        if (a.niu != b.niu) {
            return a.niu - b.niu;
        }
        if (a.maxRank != b.maxRank) {
            return a.maxRank - b.maxRank;
        }
        // 花色：权重小的大
        return suitWeight(b.maxSuit) - suitWeight(a.maxSuit);
    }

    public static int points(Card c) {
        if (c.r == 14) {
            return 1;
        }
        return Math.min(c.r, 10);
    }

    private static int suitWeight(int suit) {
        // 0=♠ 1=♥ 2=♣ 3=♦，数值即权重，越小越大
        return SUIT_ORDER[suit];
    }

    /** 生成一副 52 张并洗牌（牛牛用长牌 52 张，不是 36 张）。 */
    public static List<Card> shuffledDeck() {
        List<Card> deck = new ArrayList<>(52);
        for (int s = 0; s < 4; s++) {
            for (int r = 2; r <= 14; r++) {
                deck.add(new Card(r, s));
            }
        }
        java.util.Collections.shuffle(deck, new java.security.SecureRandom());
        return deck;
    }
}
