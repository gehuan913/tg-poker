package com.tgpoker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tgpoker.model.ActivityClaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 活动领取记录 Mapper。
 *
 * <p>幂等与限次查询见 {@code resources/mapper/ActivityClaimMapper.xml}。</p>
 */
@Mapper
public interface ActivityClaimMapper extends BaseMapper<ActivityClaim> {

    /**
     * 同一业务键是否已领取（幂等判断）。
     *
     * @param code  活动编码，如 BIG_HAND
     * @param bizId 业务键：手牌 ID / 被邀请人 ID / 日期
     */
    int countByBiz(@Param("userId") long userId, @Param("code") String code, @Param("bizId") String bizId);

    /** 今日该活动领取次数，用于每日限次。 */
    int countToday(@Param("userId") long userId, @Param("code") String code);
}
