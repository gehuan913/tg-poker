package com.tgpoker.web;

import com.tgpoker.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 活动接口。
 *
 * <p>活动本身全部配置化（{@code activity} 表），这里只做"列表 + 领取"两个动作；
 * 具体发放规则在 {@code ActivityService} 里按 code 分支。</p>
 */
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(activityService.list(userId));
    }

    @PostMapping("/claim")
    public ApiResponse<Map<String, Object>> claim(@RequestAttribute("userId") Long userId,
                                                  @RequestBody Map<String, Object> body) {
        String code = String.valueOf(body.getOrDefault("code", ""));
        String bizId = body.get("bizId") == null ? null : String.valueOf(body.get("bizId"));
        return ApiResponse.ok(activityService.claim(userId, code, bizId));
    }

    @GetMapping("/invite-code")
    public ApiResponse<Map<String, Object>> inviteCode(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(Map.of(
                "code", activityService.inviteCode(userId),
                "link", "https://t.me/?startapp=" + activityService.inviteCode(userId)
        ));
    }
}
