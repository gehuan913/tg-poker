package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 红包。
 *
 * <p>红包本质是用户间转账，和 {@code ledger_entry} 里的
 * TRANSFER_IN / TRANSFER_OUT 是同一笔业务，这里只记录社交属性（谁给谁、什么场景）。</p>
 */
@Data
@TableName("red_packet")
public class RedPacket {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发送人用户 ID。 */
    private Long senderId;

    /** 接收人用户 ID。 */
    private Long receiverId;

    /** 金额（micro-USDT）。 */
    private Long amount;

    /** 备注。 */
    private String memo;

    /** 场景：CHAT=大厅，TABLE=桌内，ACTIVITY=活动。 */
    private String scene;

    /** 桌内红包所属牌桌 ID。 */
    private String tableId;

    /** 状态：CLAIMED=已领取，PENDING=待领取。 */
    private String status;

    /** 创建时间（UTC）。 */
    private LocalDateTime createdAt;

    /** 领取时间（UTC）。 */
    private LocalDateTime claimedAt;
}
