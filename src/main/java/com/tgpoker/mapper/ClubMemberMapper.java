package com.tgpoker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tgpoker.model.ClubMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 俱乐部成员 Mapper。
 *
 * <p>club_member 是 (club_id, user_id) 复合主键，没有单列 {@code @TableId}，
 * 所以不能用 {@code selectById}；按人/按俱乐部查询都写在
 * {@code resources/mapper/ClubMemberMapper.xml}。</p>
 */
@Mapper
public interface ClubMemberMapper extends BaseMapper<ClubMember> {

    /**
     * 查某人在某俱乐部的成员记录。
     *
     * @return 不存在时返回 null
     */
    ClubMember find(@Param("clubId") long clubId, @Param("userId") long userId);

    /**
     * 某人加入的全部俱乐部。
     */
    List<ClubMember> byUser(@Param("userId") long userId);

    /**
     * 俱乐部有效成员列表（user_id / role / status / joined_at）。
     */
    List<Map<String, Object>> members(@Param("clubId") long clubId);

    /**
     * 有效成员数。
     */
    int countMembers(@Param("clubId") long clubId);
}
