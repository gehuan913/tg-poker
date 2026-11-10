package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 活动配置。
 *
 * <p>四个活动（VIP 群 / 大牌奖励 / 推荐红包 / 幸运骰子）全部配置化：
 * 上线新活动只需要插一行配置，代码里按 code 走四段领取规则，不写 if-else 分支爆炸。</p>
 */
@Data
@TableName("activity")
public class Activity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动编码：VIP_GROUP / BIG_HAND / REFERRAL / LUCKY_DICE。 */
    private String code;

    /** 活动名称。 */
    private String name;

    /** 图标（emoji）。 */
    private String icon;

    /** 活动说明。 */
    private String description;

    /** 跳转链接，如 VIP 群。 */
    private String link;

    /** 固定奖励金额（micro-USDT）；0 表示按规则动态计算（如幸运骰子）。 */
    private Long rewardAmount;

    /** 每日可领次数。 */
    private Integer dailyLimit;

    /** 是否启用：1=是，0=否。 */
    private Integer enabled;

    /** 排序号。 */
    private Integer sortNo;
}
