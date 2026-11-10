package com.tgpoker.web;

import com.tgpoker.room.TableActor;
import com.tgpoker.room.TableManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运营操作的极简接口（没有后台 UI，先用 curl / 脚本调）。
 *
 * <p>鉴权用固定 token（{@code app.admin.token}），不是用户 token：
 * 这些操作会让整桌停摆、甚至退款，不能挂在普通玩家身份上。</p>
 *
 * <p>生产接入后台时，把 token 校验换成 RBAC 即可，业务方法不用动。</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TableManager tableManager;

    @Value("${app.admin.token:kk-admin-2026}")
    private String adminToken;

    /**
     * 暂停牌桌。
     *
     * @param token 请求头 X-Admin-Token
     */
    @PostMapping("/table/{tableId}/pause")
    public ApiResponse<Map<String, Object>> pause(@PathVariable String tableId,
                                                  @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        check(token);
        TableActor actor = tableManager.get(tableId);
        if (actor == null) {
            throw new IllegalArgumentException("牌桌不存在");
        }
        actor.pause();
        return ApiResponse.ok(Map.of("ok", true, "action", "pause"));
    }

    /** 恢复牌桌。 */
    @PostMapping("/table/{tableId}/resume")
    public ApiResponse<Map<String, Object>> resume(@PathVariable String tableId,
                                                   @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        check(token);
        TableActor actor = tableManager.get(tableId);
        if (actor == null) {
            throw new IllegalArgumentException("牌桌不存在");
        }
        actor.resume();
        return ApiResponse.ok(Map.of("ok", true, "action", "resume"));
    }

    /** 解散牌桌：退还所有筹码并重置。 */
    @PostMapping("/table/{tableId}/disband")
    public ApiResponse<Map<String, Object>> disband(@PathVariable String tableId,
                                                    @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        check(token);
        TableActor actor = tableManager.get(tableId);
        if (actor == null) {
            throw new IllegalArgumentException("牌桌不存在");
        }
        actor.disband();
        return ApiResponse.ok(Map.of("ok", true, "action", "disband"));
    }

    /** token 校验：常量时间比较，避免时序侧信道。 */
    private void check(String token) {
        String expected = adminToken == null ? "" : adminToken;
        boolean ok = token != null && expected.length() > 0
                && java.security.MessageDigest.isEqual(
                        token.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!ok) {
            throw new SecurityException("无权限");
        }
    }
}
