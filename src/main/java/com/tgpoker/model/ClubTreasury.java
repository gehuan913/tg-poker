package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 俱乐部金库。
 *
 * <p>保险的承保方：保费进这里，赔付从这里出。
 * {@code minReserve} 是"保证金下限"，低于它就不允许开保险，
 * 避免出现"玩家赢了保险但金库赔不出来"的穿仓。</p>
 */
@Data
@TableName("club_treasury")
public class ClubTreasury {

    /** 俱乐部 ID。 */
    @TableId(type = IdType.INPUT)
    private Long clubId;

    /** 金库余额（micro-USDT）。 */
    private Long balance;

    /** 最低保证金（micro-USDT），低于该值不允许开保险。 */
    private Long minReserve;

    /** 版本号，乐观锁用。 */
    private Long version;
}
