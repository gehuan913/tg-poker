package com.tgpoker.web;

import com.tgpoker.model.Notification;
import com.tgpoker.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息中心接口：列表、未读数、全部已读。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestAttribute("userId") Long userId,
                                                 @RequestParam(defaultValue = "30") int limit) {
        List<Notification> rows = notificationService.recent(userId, limit);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unread", notificationService.unread(userId));
        m.put("items", rows);
        return ApiResponse.ok(m);
    }

    @PostMapping("/read")
    public ApiResponse<Void> readAll(@RequestAttribute("userId") Long userId) {
        notificationService.markAllRead(userId);
        return ApiResponse.ok(null);
    }
}
