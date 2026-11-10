package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 牛牛单局单人的战绩。
 *
 * <p>牛牛没有德州那种"手牌"概念，一个 round 里 1 庄 3 闲各自与庄结算，
 * 所以统计口径是"按局 × 人"：局数、输赢、坐庄局数、赢的局数。</p>
 */
@Data
@TableName("niuniu_result")
public class NiuniuResult {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 牌桌 ID，如 N01。 */
    private String tableId;

    /** 局号，从 1 开始。 */
    private Long roundNo;

    /** 用户 ID。 */
    private Long userId;

    /** 座位号（0~3）。 */
    private Integer seat;

    /** 是否本局庄家：1=庄，0=闲。 */
    private Integer banker;

    /** 本局下注额（micro-USDT）；庄家为 0。 */
    private Long bet;

    /** 本局净输赢（micro-USDT，可为负）。 */
    private Long net;

    /** 记录时间（UTC）。 */
    private LocalDateTime createdAt;
}
