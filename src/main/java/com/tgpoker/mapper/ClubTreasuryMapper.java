package com.tgpoker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tgpoker.model.ClubTreasury;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 俱乐部金库 Mapper。
 *
 * <p>金库是保险的承保方，扣款必须保证不会出现负数（穿仓），
 * 条件更新 SQL 见 {@code resources/mapper/ClubTreasuryMapper.xml}。</p>
 */
@Mapper
public interface ClubTreasuryMapper extends BaseMapper<ClubTreasury> {

    /**
     * 金库结算。
     *
     * @param delta 正数=保费入账，负数=赔付出账
     * @return 影响行数，0 表示余额不足或俱乐部不存在
     */
    int apply(@Param("clubId") long clubId, @Param("delta") long delta);
}
