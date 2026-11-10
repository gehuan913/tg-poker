package com.tgpoker.web;

import com.tgpoker.model.Hand;
import com.tgpoker.service.ClubService;
import com.tgpoker.util.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 俱乐部接口：搜索、我的俱乐部、创建、加入、详情、牌局记录、金库。
 *
 * <p>金库余额对所有成员可见——保险赔付的钱从这里出，账目必须经得起看。</p>
 */
@RestController
@RequestMapping("/api/club")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;

    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(clubService.search(keyword));
    }

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(clubService.myClubs(userId));
    }

    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> create(@RequestAttribute("userId") Long userId,
                                                   @RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", ""));
        String notice = body.get("notice") == null ? "" : String.valueOf(body.get("notice"));
        String joinType = body.get("joinType") == null ? "OPEN" : String.valueOf(body.get("joinType"));
        return ApiResponse.ok(clubService.toMap(clubService.create(userId, name, notice, joinType)));
    }

    @PostMapping("/{clubId}/join")
    public ApiResponse<Map<String, Object>> join(@PathVariable long clubId,
                                                 @RequestAttribute("userId") Long userId) {
        var club = clubService.get(clubId);
        if (club == null) {
            throw new IllegalArgumentException("俱乐部不存在");
        }
        clubService.join(clubId, userId, "MEMBER");
        return ApiResponse.ok(clubService.toMap(club));
    }

    @GetMapping("/{clubId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long clubId,
                                                   @RequestAttribute("userId") Long userId) {
        var club = clubService.get(clubId);
        if (club == null) {
            throw new IllegalArgumentException("俱乐部不存在");
        }
        Map<String, Object> m = clubService.toMap(club);
        m.put("treasury", clubService.treasury(clubId));
        m.put("members", clubService.members(clubId));
        return ApiResponse.ok(m);
    }

    /** 俱乐部牌局记录（按手牌维度，不是按人）。 */
    @GetMapping("/{clubId}/records")
    public ApiResponse<List<Map<String, Object>>> records(@PathVariable long clubId,
                                                          @RequestParam(defaultValue = "20") int limit) {
        List<Hand> hands = clubService.records(clubId, limit);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Hand h : hands) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("handId", h.getHandId());
            m.put("tableId", h.getTableId());
            m.put("variant", h.getVariant());
            m.put("blinds", h.getBlinds());
            m.put("board", h.getBoard());
            m.put("potText", Money.fmt(h.getPot() == null ? 0 : h.getPot()));
            m.put("verifyCode", h.getVerifyCode());
            m.put("startedAt", h.getStartedAt());
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/{clubId}/treasury")
    public ApiResponse<Map<String, Object>> treasury(@PathVariable long clubId) {
        return ApiResponse.ok(clubService.treasury(clubId));
    }
}
