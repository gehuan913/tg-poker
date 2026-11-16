package com.tgpoker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tgpoker.model.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息中心 Mapper。
 *
 * <p>列表与未读数写在 XML：都是"按用户 + 倒序 + 限制条数"，放注解里可读性差。</p>
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 最近 N 条。 */
    List<Notification> recent(@Param("userId") long userId, @Param("limit") int limit);

    /** 未读数。 */
    int unreadCount(@Param("userId") long userId);
}
