package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 风控事件。规则先落库，模型后置。 */
@Data
@TableName("risk_event")
public class RiskEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String tableId;
    /** COLLUSION / MULTI_ACCOUNT / BOT / WASH / INSURANCE_ARBITRAGE。 */
    private String kind;
    private Integer score;
    private String detail;
    private LocalDateTime createdAt;
}
