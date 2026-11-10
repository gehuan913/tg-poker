package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 一手牌的存证记录。 */
@Data
@TableName("hand")
public class Hand {

    @TableId(type = IdType.INPUT)
    private String handId;

    private String tableId;
    private Long clubId;
    private String variant;
    private String blinds;
    private Long ante;
    private String board;
    private Long pot;
    private Long rake;

    /** 可验证发牌：serverSeed 结算后揭示，hash 开局即公示。 */
    private String serverSeed;
    private String serverSeedHash;
    private String clientSeeds;
    private Long nonce;
    private String verifyCode;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
