package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 俱乐部。
 *
 * <p>俱乐部是牌桌与金库的容器：牌桌归属俱乐部，保险的承保资金在
 * {@link ClubTreasury} 里。默认俱乐部由系统在首次启动时创建（owner_id = 0）。</p>
 */
@Data
@TableName("club")
public class Club {

    /** 俱乐部 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 俱乐部名称。 */
    private String name;

    /** 盟主用户 ID；0 表示系统默认俱乐部。 */
    private Long ownerId;

    /** 头像 URL。 */
    private String avatar;

    /** 公告。 */
    private String notice;

    /** 加入方式：OPEN=直接加入，APPROVAL=需审核。 */
    private String joinType;

    /** 最小买入（micro-USDT）。 */
    private Long minBuyIn;

    /** 最大买入（micro-USDT）。 */
    private Long maxBuyIn;

    /** 保险开关：1=开，0=关。 */
    private Integer insuranceEnabled;

    /** 创建时间（UTC）。 */
    private LocalDateTime createdAt;
}
