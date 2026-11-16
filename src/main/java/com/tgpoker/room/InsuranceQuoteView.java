package com.tgpoker.room;

import java.util.ArrayList;
import java.util.List;

/**
 * 保险报价（给前端展示）。
 *
 * <p>按底池分组：一个 All-in 场景可能有主池 + 边池，每个池单独定价、单独购买，
 * 最多买 3 个池。前端只需要照着列表渲染，不需要自己算赔率。</p>
 */
public class InsuranceQuoteView {

    /** 我是否是至少一个池的领先方（决定要不要显示投保入口）。 */
    public boolean anyAvailable;

    /** 领先方座位，-1 表示我不是领先方。 */
    public int buyerSeat = -1;

    /** 全局不可投保原因（如未开启保险、未到转牌圈）。 */
    public String reason;

    /** 各底池报价。 */
    public List<PotQuote> pots = new ArrayList<>();

    /** 我所有可买池的满额保费合计。 */
    public long totalPremium;

    /** 我所有可买池的保额合计。 */
    public long totalCoverage;
}
