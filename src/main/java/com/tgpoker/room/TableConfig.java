package com.tgpoker.room;

import com.tgpoker.engine.Variant;

/** 牌桌配置。盲注结构、前注、玩法都来自这里，绝不写死在引擎里。 */
public class TableConfig {

    public String id;
    public String name;
    /** LONG / SHORT。 */
    public String variant = "LONG";

    public long smallBlind;
    public long bigBlind;
    /** 前注金额；短牌用它替代盲注结构。 */
    public long ante;
    /** NONE / EVERYONE / BUTTON_DOUBLE。 */
    public String anteMode = "NONE";

    public long minBuyIn;
    public long maxBuyIn;
    public int maxSeats = 6;
    /** 每次行动倒计时。 */
    public int actionSeconds = 15;

    public long clubId = 0;
    public boolean insuranceEnabled = true;
    /** 是否启用 Straddle（UTG 强制 2BB）。 */
    public boolean straddle;

    public TableConfig(String id, String name, long smallBlind, long bigBlind,
                       long minBuyIn, long maxBuyIn, int maxSeats) {
        this.id = id;
        this.name = name;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.minBuyIn = minBuyIn;
        this.maxBuyIn = maxBuyIn;
        this.maxSeats = maxSeats;
    }

    public TableConfig withVariant(String v) {
        this.variant = v;
        return this;
    }

    public TableConfig withAnte(long ante, String anteMode) {
        this.ante = ante;
        this.anteMode = anteMode;
        return this;
    }

    public TableConfig withClub(long clubId, boolean insurance) {
        this.clubId = clubId;
        this.insuranceEnabled = insurance;
        return this;
    }

    /** 开启 Straddle：UTG 下 2BB，盲注展示变成 SB/BB/(2BB)。 */
    public TableConfig withStraddle(boolean on) {
        this.straddle = on;
        return this;
    }

    public Variant variantEnum() {
        return Variant.of(variant);
    }

    /** 桌内显式标注，如「短牌前注 0.2」。 */
    public String anteLabel() {
        if (ante <= 0 || "NONE".equalsIgnoreCase(anteMode)) {
            return "";
        }
        String prefix = Variant.of(variant) == Variant.SHORT ? "短牌前注 " : "前注 ";
        return prefix + com.tgpoker.util.Money.fmt(ante);
    }
}
