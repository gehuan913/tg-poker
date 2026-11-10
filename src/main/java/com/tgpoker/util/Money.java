package com.tgpoker.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额工具。全系统统一用 micro-USDT（1 USDT = 1,000,000），避免浮点误差。
 */
public final class Money {

    public static final long ONE = 1_000_000L;

    private Money() {
    }

    public static long parse(String usdt) {
        return new BigDecimal(usdt).multiply(BigDecimal.valueOf(ONE))
                .setScale(0, RoundingMode.DOWN).longValueExact();
    }

    /** 1.234567 -> "1.23"（去掉无意义的尾零） */
    public static String fmt(long micro) {
        BigDecimal v = BigDecimal.valueOf(micro).divide(BigDecimal.valueOf(ONE), 6, RoundingMode.DOWN);
        v = v.stripTrailingZeros();
        if (v.scale() < 0) {
            v = v.setScale(0);
        }
        return v.toPlainString();
    }

    public static String fmtSigned(long micro) {
        return (micro > 0 ? "+" : "") + fmt(micro);
    }
}
