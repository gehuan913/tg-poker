package com.tgpoker.web;

import com.tgpoker.room.TableActor;
import com.tgpoker.room.TableManager;
import com.tgpoker.service.FairService;
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

/** 可验证公平：承诺 / 提交玩家种子 / 揭示 / 复算。 */
@RestController
@RequestMapping("/api/fair")
@RequiredArgsConstructor
public class FairController {

    private final TableManager tableManager;
    private final FairService fairService;

    /** 当前手的承诺（hash + 存证号），不含种子。 */
    @GetMapping("/table/{tableId}")
    public ApiResponse<Map<String, Object>> commitment(@PathVariable String tableId) {
        TableActor actor = tableManager.get(tableId);
        if (actor == null) {
            throw new IllegalArgumentException("牌桌不存在");
        }
        com.tgpoker.engine.TableState st = actor.getState();
        return ApiResponse.ok(fairService.commitment(st.tableId, st.handId, st.handNo,
                st.serverSeedHash, st.verifyCode, st.clientSeeds));
    }

    /**
     * 提交自己的种子，下一手参与洗牌。
     * 最后提交的人无法预知他人种子 —— 这是防"运营方提前挑牌序"的关键。
     */
    @PostMapping("/client-seed")
    public ApiResponse<Void> submitSeed(@RequestAttribute("userId") Long userId,
                                        @RequestBody Map<String, Object> body) {
        String tableId = String.valueOf(body.getOrDefault("tableId", ""));
        String seed = String.valueOf(body.getOrDefault("seed", ""));
        TableActor actor = tableManager.get(tableId);
        if (actor == null) {
            throw new IllegalArgumentException("牌桌不存在");
        }
        actor.submitClientSeed(userId, seed);
        return ApiResponse.ok(null);
    }

    @GetMapping("/hand/{handId}")
    public ApiResponse<Map<String, Object>> info(@PathVariable String handId) {
        return ApiResponse.ok(fairService.info(handId));
    }

    @GetMapping("/hand/{handId}/verify")
    public ApiResponse<Map<String, Object>> verify(@PathVariable String handId) {
        return ApiResponse.ok(fairService.verify(handId));
    }

    /**
     * L3 自助取证：返回完整牌序与所有人底牌，仅限参与本手的玩家，每日 10 次。
     */
    @GetMapping("/hand/{handId}/bundle")
    public ApiResponse<Map<String, Object>> bundle(@PathVariable String handId,
                                                   @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(fairService.disputeBundle(handId, userId));
    }

    /** 供前端展示的算法说明，避免玩家只能"信平台"。 */
    @GetMapping("/algorithm")
    public ApiResponse<Map<String, Object>> algorithm() {
        return ApiResponse.ok(Map.of(
                "steps", List.of(
                        "serverSeed = 32 字节随机数，开局前公示 SHA256(serverSeed)",
                        "clientSeeds = 每位玩家提交的随机种子，按座位顺序用逗号连接",
                        "seed = HMAC_SHA256(serverSeed, clientSeeds + \":\" + nonce)",
                        "deck = FisherYates(标准牌序, HMAC-DRBG(seed))",
                        "结算后揭示 serverSeed，任何人可复算整副牌序"
                ),
                "verifyScript", "/verify.js",
                "note", "站内按钮只是方便；真正可信的是 verify.js 能被第三方离线跑通"
        ));
    }
}
