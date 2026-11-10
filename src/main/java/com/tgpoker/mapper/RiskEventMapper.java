package com.tgpoker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tgpoker.model.RiskEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 风控事件 Mapper。
 *
 * <p>SELECT 见 {@code resources/mapper/RiskEventMapper.xml}。</p>
 */
@Mapper
public interface RiskEventMapper extends BaseMapper<RiskEvent> {

    /** 某人最近 20 条风控事件（kind / score / detail / created_at）。 */
    List<Map<String, Object>> recent(@Param("userId") long userId);
}
