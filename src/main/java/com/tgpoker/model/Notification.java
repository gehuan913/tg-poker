package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息中心的一条通知。
 *
 * <p>产品文档只在两处强调：充值/提现进度、红包接收记录（发送记录不推到消息中心）。
 * 所以这里 kind 只保留这几类，不做站内信系统。</p>
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人。 */
    private Long userId;

    /** DEPOSIT / WITHDRAW / REDPACKET / ACTIVITY / SYSTEM。 */
    private String kind;

    /** 标题，列表直接展示。 */
    private String title;

    /** 正文，可空。 */
    private String body;

    /** 关联业务 ID（订单号、红包 ID 等）。 */
    private String refId;

    /** 是否已读：1=已读。 */
    private Integer readFlag;

    private LocalDateTime createdAt;
}
