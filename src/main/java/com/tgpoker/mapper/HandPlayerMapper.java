package com.tgpoker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tgpoker.model.HandPlayer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 手牌-玩家维度 Mapper。
 *
 * <p>聚合与跨表查询（join hand 拿玩法/公牌）都写在
 * {@code resources/mapper/HandPlayerMapper.xml}，接口只保留方法签名。</p>
 */
@Mapper
public interface HandPlayerMapper extends BaseMapper<HandPlayer> {

    /**
     * 按玩法聚合战绩。
     *
     * <p>返回 key：hands / net / insurance_net / vpip_hands / pfr_hands。
     * 注意是 snake_case，调用方用 {@code Maps.camel()} 转驼峰。</p>
     *
     * @param userId  用户 ID
     * @param variant 玩法：LONG / SHORT
     */
    Map<String, Object> stats(@Param("userId") long userId, @Param("variant") String variant);

    /**
     * 最近牌局（带玩法、公牌、起止筹码）。
     */
    List<Map<String, Object>> recent(@Param("userId") long userId, @Param("limit") int limit);

    /**
     * 最近已结算手牌的底牌与公牌，大牌奖励判定用。
     */
    List<Map<String, Object>> recentCards(@Param("userId") long userId, @Param("limit") int limit);
}
