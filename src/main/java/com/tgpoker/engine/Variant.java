package com.tgpoker.engine;

/**
 * 玩法变体。
 *
 * <p>短牌不是"长牌加个开关"：牌堆 36 张（去掉 2/3/4/5）、牌型强弱顺序不同、
 * 最小顺子是 A6789、盲注改成前注制。所以两者共用状态机，但牌堆与牌型表必须分开。</p>
 */
public enum Variant {

    /** 长牌 52 张。 */
    LONG,
    /** 短牌 36 张（6 以上）。 */
    SHORT;

    public static Variant of(String s) {
        if (s == null) {
            return LONG;
        }
        try {
            return Variant.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LONG;
        }
    }

    public int deckSize() {
        return this == SHORT ? 36 : 52;
    }

    /** 短牌最低牌面是 6，长牌是 2。 */
    public int minRank() {
        return this == SHORT ? 6 : 2;
    }

    public String label() {
        return this == SHORT ? "短牌" : "长牌";
    }
}
