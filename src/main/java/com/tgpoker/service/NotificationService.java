package com.tgpoker.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tgpoker.mapper.NotificationMapper;
import com.tgpoker.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息中心。
 *
 * <p>只做"事件 → 一条通知"的轻量记录，不保证必达（失败只打日志，不影响主流程）。
 * 产品口径：消息中心只推<b>接收</b>记录，发送记录去账单看。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper mapper;

    /** 推一条通知；任何异常都吞掉，绝不因为消息中心写失败而回滚资金。 */
    public void push(long userId, String kind, String title, String body, String refId) {
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setKind(kind);
            n.setTitle(title);
            n.setBody(body);
            n.setRefId(refId);
            n.setReadFlag(0);
            n.setCreatedAt(LocalDateTime.now());
            mapper.insert(n);
        } catch (Exception e) {
            log.warn("通知写入失败 user={} kind={}", userId, kind, e);
        }
    }

    public List<Notification> recent(long userId, int limit) {
        return mapper.recent(userId, Math.min(Math.max(limit, 1), 100));
    }

    public int unread(long userId) {
        return mapper.unreadCount(userId);
    }

    /** 全部标记已读。 */
    public void markAllRead(long userId) {
        UpdateWrapper<Notification> w = new UpdateWrapper<>();
        w.eq("user_id", userId).eq("read_flag", 0).set("read_flag", 1);
        mapper.update(null, w);
    }
}
