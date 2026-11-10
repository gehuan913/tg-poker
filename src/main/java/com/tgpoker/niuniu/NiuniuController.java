package com.tgpoker.niuniu;

import com.tgpoker.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 牛牛接口。
 *
 * <p>用 1.5s 轮询而不是 WebSocket：牛牛只有"下注窗口"这一个交互点，
 * 没有德州那种逐动作的行动轮，轮询的成本更低、客户端更简单。</p>
 */
@RestController
@RequestMapping("/api/niuniu")
@RequiredArgsConstructor
public class NiuniuController {

    private final NiuniuManager niuniuManager;

    @GetMapping("/rooms")
    public ApiResponse<List<Map<String, Object>>> rooms() {
        return ApiResponse.ok(niuniuManager.list());
    }

    @GetMapping("/{tableId}")
    public ApiResponse<NiuniuRoom.View> view(@PathVariable String tableId,
                                             @RequestAttribute("userId") Long userId) {
        NiuniuRoom room = niuniuManager.get(tableId);
        if (room == null) {
            throw new IllegalArgumentException("牌桌不存在");
        }
        return ApiResponse.ok(room.view(userId));
    }

    @PostMapping("/{tableId}/sit")
    public ApiResponse<Void> sit(@PathVariable String tableId,
                                 @RequestAttribute("userId") Long userId,
                                 @RequestBody Map<String, Object> body) {
        NiuniuRoom room = niuniuManager.get(tableId);
        if (room == null) {
            throw new IllegalArgumentException("牌桌不存在");
        }
        long buyIn = Long.parseLong(String.valueOf(body.getOrDefault("buyIn", "0")));
        int seat = body.get("seat") == null ? -1 : Integer.parseInt(String.valueOf(body.get("seat")));
        String nick = body.get("nick") == null ? "玩家" : String.valueOf(body.get("nick"));
        String avatar = body.get("avatar") == null ? "" : String.valueOf(body.get("avatar"));
        room.sit(userId, nick, avatar, buyIn, seat);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{tableId}/bet")
    public ApiResponse<Void> bet(@PathVariable String tableId,
                                 @RequestAttribute("userId") Long userId,
                                 @RequestBody Map<String, Object> body) {
        NiuniuRoom room = niuniuManager.get(tableId);
        if (room == null) {
            throw new IllegalArgumentException("牌桌不存在");
        }
        room.bet(userId, Long.parseLong(String.valueOf(body.getOrDefault("amount", "0"))));
        return ApiResponse.ok(null);
    }

    /**
     * 上庄 / 下庄。
     *
     * @param body {"on": true} 申请上庄，false 取消
     */
    @PostMapping("/{tableId}/banker")
    public ApiResponse<Void> banker(@PathVariable String tableId,
                                    @RequestAttribute("userId") Long userId,
                                    @RequestBody Map<String, Object> body) {
        NiuniuRoom room = niuniuManager.get(tableId);
        if (room == null) {
            throw new IllegalArgumentException("牌桌不存在");
        }
        boolean on = body.get("on") == null || Boolean.parseBoolean(String.valueOf(body.get("on")));
        room.applyBanker(userId, on);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{tableId}/leave")
    public ApiResponse<Void> leave(@PathVariable String tableId,
                                   @RequestAttribute("userId") Long userId) {
        NiuniuRoom room = niuniuManager.get(tableId);
        if (room != null) {
            room.leave(userId);
        }
        return ApiResponse.ok(null);
    }
}
