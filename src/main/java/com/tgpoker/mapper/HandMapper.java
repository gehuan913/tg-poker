package com.tgpoker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tgpoker.model.Hand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 手牌主表 Mapper。
 *
 * <p>{@link #maxHandNo} 用于服务重启后把桌子的手号接上，避免 handId 从 1 重来、
 * 和库里旧记录撞主键（撞了会被"重复写入"逻辑跳过，玩家战绩直接丢失）。</p>
 */
@Mapper
public interface HandMapper extends BaseMapper<Hand> {

    /**
     * 该桌已有的最大手号。
     *
     * @param tableId 牌桌 ID
     * @return 最大手号；没有记录返回 0
     */
    long maxHandNo(@Param("tableId") String tableId);
}
