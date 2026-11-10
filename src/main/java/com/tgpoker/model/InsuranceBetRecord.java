package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保险单落库记录。
 *
 * <p>投保（premium）与赔付（payout）是两个独立科目，不能合并成"保险净额"，
 * 否则桌内保险流水与对账都会失真。outs 与赔率一并落库，便于事后复核定价。</p>
 */
@Data
@TableName("insurance_bet")
public class InsuranceBetRecord {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手牌 ID。 */
    private String handId;

    /** 牌桌 ID。 */
    private String tableId;

    /** 底池序号，多人边池时区分。 */
    private Integer potIndex;

    /** 投保人用户 ID（当时领先方）。 */
    private Long buyerUserId;

    /** 反超 outs 数量，1~14。 */
    private Integer outs;

    /** 主险赔率，如 8.000。 */
    private BigDecimal odds;

    /** 平局 outs 数量。 */
    private Integer tieOuts;

    /** 平局险赔率。 */
    private BigDecimal tieOdds;

    /** 主险保费（micro-USDT）。 */
    private Long premiumMain;

    /** 平局险保费（micro-USDT）。 */
    private Long premiumTie;

    /** 保额（micro-USDT），上限为一个底池。 */
    private Long coverage;

    /** 结果：PENDING / WON（反超成功，金库赔）/ LOST（未触发，保费归金库）。 */
    private String result;

    /** 结算时间（UTC）。 */
    private LocalDateTime settledAt;

    /** 投保时间（UTC）。 */
    private LocalDateTime createdAt;
}
