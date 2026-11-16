package com.tgpoker.room;

/**
 * 单个底池的保险报价。
 *
 * <p>产品文档要求按 Pot 分别定价：每个池有自己的金额、参与人数、outs 与赔率，
 * 领先方可以只买其中几个池（最多 3 个），也可以按 1/2、1/3 池购买。</p>
 */
public class PotQuote {

    /** 底池序号，从 0 开始（与 PotBuilder 输出顺序一致）。 */
    public int potIndex;

    /** 该底池金额（micro-USDT）。 */
    public long potAmount;

    /** 该底池的参与人数（未弃牌且有资格分这一层的人数）。 */
    public int players;

    /** 反超 outs 数。 */
    public int outs;

    /** 平局 outs 数。 */
    public int tieOuts;

    /** 主险赔率。 */
    public double odds;

    /** 平局险赔率（tieOuts=0 时为 0）。 */
    public double tieOdds;

    /** 本池可投保上限：通常等于池额；同牌平分时按 Pot/N 下调。 */
    public long maxCoverage;

    /** 满额保费（按 maxCoverage 算）。 */
    public long premium;

    /** 平局险保费（未单独购买，随主险附带）。 */
    public long tiePremium;

    /** 本池领先方座位（= 可投保人）。 */
    public int buyerSeat = -1;

    /** 默认保额（= maxCoverage）。 */
    public long coverage;

    /** 我是否是本池领先方。 */
    public boolean mine;

    /** 是否可买。 */
    public boolean available;

    /** 不可买原因。 */
    public String reason;
}
