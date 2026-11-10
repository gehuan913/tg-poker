package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 钱包账户。
 *
 * <p>金额单位统一 micro-USDT（1 USDT = 1,000,000），用 BIGINT 存整数，
 * 避免浮点误差。余额只在这里维护，任何变动都必须同时写一条 {@link LedgerEntry}。</p>
 */
@Data
@TableName("account")
public class Account {

    /** 用户 ID，与 app_user.id 一一对应。 */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /** 可用余额（micro-USDT）。 */
    private Long balance;

    /** 冻结金额（micro-USDT），如提现审核中。 */
    private Long frozen;

    /** 版本号，乐观锁用，每次余额变动 +1。 */
    private Long version;
}
