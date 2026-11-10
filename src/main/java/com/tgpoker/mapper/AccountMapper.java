package com.tgpoker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tgpoker.model.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 钱包账户 Mapper。
 *
 * <p>简单 CRUD 用 MyBatis-Plus 的 BaseMapper；两条带条件更新的 SQL 放在
 * {@code resources/mapper/AccountMapper.xml}（复杂 SQL 一律走 XML，便于审阅与调优）。</p>
 */
@Mapper
public interface AccountMapper extends BaseMapper<Account> {

    /**
     * 扣款：余额足够才扣，等价于乐观锁。
     *
     * @param userId 用户 ID
     * @param amount 扣款金额（micro-USDT）
     * @return 影响行数，0 表示余额不足
     */
    int deduct(@Param("userId") long userId, @Param("amount") long amount);

    /**
     * 加款。
     *
     * @param userId 用户 ID
     * @param amount 加款金额（micro-USDT）
     * @return 影响行数
     */
    int add(@Param("userId") long userId, @Param("amount") long amount);
}
