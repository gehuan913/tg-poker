package com.tgpoker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tgpoker.model.NiuniuResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 牛牛战绩 Mapper。
 *
 * <p>聚合 SQL 见 {@code resources/mapper/NiuniuResultMapper.xml}。</p>
 */
@Mapper
public interface NiuniuResultMapper extends BaseMapper<NiuniuResult> {

    /**
     * 按用户聚合牛牛战绩。
     *
     * <p>返回 key：rounds / net / win_rounds / banker_rounds（snake_case）。</p>
     */
    Map<String, Object> stats(@Param("userId") long userId);
}
