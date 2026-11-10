package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 活动领取记录。(activity_code,user_id,biz_id) 唯一，保证幂等。 */
@Data
@TableName("activity_claim")
public class ActivityClaim {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String activityCode;
    private Long amount;
    private String bizId;
    private LocalDateTime createdAt;
}
