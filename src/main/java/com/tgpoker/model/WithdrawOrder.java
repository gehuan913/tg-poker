package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 提现工单。真实签名由区块链平台执行，本站只负责工单与账本。 */
@Data
@TableName("withdraw_order")
public class WithdrawOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String address;
    private Long amount;
    private Long fee;
    private String txHash;
    /** PENDING_REVIEW / PROCESSING / SUCCESS / REJECTED。 */
    private String status;
    private String memo;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
