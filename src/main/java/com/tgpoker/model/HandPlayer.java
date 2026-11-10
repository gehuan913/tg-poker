package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 一手牌里某个座位的战绩。
 *
 * <p>这张表同时承担两个职责：</p>
 * <ol>
 *   <li>牌谱回放与对账（起止筹码、输赢、动作序列）；</li>
 *   <li>统计口径的原始数据（vpip / pfr 是 0/1 标记，聚合时求和再除以手数）。</li>
 * </ol>
 */
@Data
@TableName("hand_player")
public class HandPlayer {

    /** 手牌 ID，与 hand.hand_id 对应。 */
    private String handId;

    /** 座位号，从 0 开始。 */
    private Integer seat;

    /** 用户 ID。 */
    private Long userId;

    /** 底牌，如 KsKh。当前明文，生产应改为 AES-256-GCM 密文。 */
    private String holeCards;

    /** 本手开始前筹码（含已下注部分）。 */
    private Long startStack;

    /** 本手结算后筹码。 */
    private Long endStack;

    /** 本手输赢 = endStack - startStack。 */
    private Long net;

    /** 翻前是否主动入池：1=是，0=否（VPIP 分子）。 */
    private Integer vpip;

    /** 翻前是否加注/全下：1=是，0=否（PFR 分子）。 */
    private Integer pfr;

    /** 动作序列 JSON，风控回放用。 */
    private String actions;

    /** 本手保险净额 = 赔付 - 保费（micro-USDT，可为负）。 */
    private Long insuranceNet;
}
