package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 充值订单。
 *
 * <p>{@code txHash} 上有唯一索引，这是"同一笔链上交易永远只入账一次"的技术保证。
 * 不要用业务判断（例如"查一下最近有没有相同金额"）来代替唯一索引——
 * 并发回调时那种判断一定会漏。</p>
 */
@Data
@TableName("deposit_order")
public class DepositOrder {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 收款地址（用户的专属地址）。 */
    private String address;

    /** 链上交易哈希，唯一。 */
    private String txHash;

    /** 到账金额（micro-USDT）。 */
    private Long amount;

    /** 确认数。 */
    private Integer confirmations;

    /** 状态：CREDITED=已入账，PENDING=待确认。 */
    private String status;

    /** 入账时间（UTC）。 */
    private LocalDateTime creditedAt;

    /** 创建时间（UTC）。 */
    private LocalDateTime createdAt;
}
