package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 每人独立充值地址。TRON 没有 memo，共享地址必然错账，所以必须一人一址。 */
@Data
@TableName("deposit_address")
public class DepositAddress {

    @TableId(type = IdType.INPUT)
    private String address;

    private Long userId;
    private String chain;
    private String asset;
    private LocalDateTime allocatedAt;
}
