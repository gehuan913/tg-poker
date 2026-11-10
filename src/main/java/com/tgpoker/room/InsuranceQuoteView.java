package com.tgpoker.room;

/**
 * 保险报价（给前端展示）。
 *
 * <p>只暴露玩家需要看的字段：几个 outs、赔率多少、保额上限、要付多少保费。
 * 定价模型与金库校验在 {@link InsuranceOdds} 和 {@link TableActor} 里，不在这里。</p>
 */
public class InsuranceQuoteView {

    /** 领先方座位（只有他能买）。 */
    public int buyerSeat;

    /** 落后方座位（被保险的反超方）。 */
    public int behindSeat;

    /** 反超 outs 数量，1~14。 */
    public int outs;

    /** 赔率倍数，如 8 表示 1 赔 8。 */
    public double odds;

    /** 赔率展示串，保留一位小数。 */
    public String oddsText;

    /** 保额上限（= 当前底池）。 */
    public long maxCoverage;

    /** 默认保额。 */
    public long coverage;

    /** 应付保费 = 保额 ÷ 赔率。 */
    public long premium;

    /** 保费展示串，如 "12.5"。 */
    public String premiumText;

    /** 不可投保时的原因，如"只在转牌圈提供保险"。 */
    public String reason;
}
