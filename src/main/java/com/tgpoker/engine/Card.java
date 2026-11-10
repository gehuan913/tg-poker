package com.tgpoker.engine;

/**
 * 一张牌。r = 点数 2..14（14=A），s = 花色 0=♠ 1=♥ 2=♣ 3=♦。
 */
public final class Card {

    public final int r;
    public final int s;

    public Card(int r, int s) {
        this.r = r;
        this.s = s;
    }

    public String code() {
        return rankStr(r) + suitChar(s);
    }

    public static String rankStr(int r) {
        switch (r) {
            case 14: return "A";
            case 13: return "K";
            case 12: return "Q";
            case 11: return "J";
            case 10: return "T";
            default: return String.valueOf(r);
        }
    }

    public static char suitChar(int s) {
        return "shcd".charAt(s);
    }

    public static String suitSymbol(int s) {
        switch (s) {
            case 0: return "♠";
            case 1: return "♥";
            case 2: return "♣";
            default: return "♦";
        }
    }

    public static Card parse(String c) {
        int r = "23456789TJQKA".indexOf(c.charAt(0)) + 2;
        int s = "shcd".indexOf(c.charAt(1));
        if (r < 2 || s < 0) {
            throw new IllegalArgumentException("bad card: " + c);
        }
        return new Card(r, s);
    }

    @Override
    public String toString() {
        return code();
    }
}
