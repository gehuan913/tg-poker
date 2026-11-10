package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资金流水。
 *
 * <p>只增不改：任何余额变动都要有一条流水，对账时用
 * "account.balance 汇总 = platform 余额" 校验。
 * 幂等键是 (bizType, bizId, userId)，重试不会重复入账。</p>
 */
@Data
@TableName("ledger_entry")
public class LedgerEntry {

    /** 自增主键，也用作流水序号。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 方向：1=支出（DEBIT），2=收入（CREDIT）。 */
    private Integer direction;

    /** 发生金额（micro-USDT，正数）。 */
    private Long amount;

    /** 业务类型：DEPOSIT / WITHDRAW / TABLE_BUYIN / TABLE_CASHOUT / INSURANCE_* / TRANSFER_* / ACTIVITY。 */
    private String bizType;

    /** 业务幂等键：充值用 txHash，手牌用 handId-座位，活动用 code-bizId。 */
    private String bizId;

    /** 本次变动后的余额，用于客服追溯与对账。 */
    private Long balanceAfter;

    /** 展示用摘要，如"离桌退筹码"。 */
    private String memo;

    /** 记账时间（UTC）。 */
    private LocalDateTime createdAt;
}
