package com.tgpoker.web;

import com.tgpoker.mapper.HandPlayerMapper;
import com.tgpoker.model.AppUser;
import com.tgpoker.service.ActivityService;
import com.tgpoker.service.UserService;
import com.tgpoker.service.WalletService;
import com.tgpoker.util.Maps;
import com.tgpoker.util.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
 * 我的：资料、数据面板、最近牌局、修改昵称/绑定邀请码。
 *
 * <p>数据面板按玩法（LONG / SHORT）分开统计，口径与桌内账单一致：
 * 总输赢 = 牌局输赢 + 保险净额；VPIP = 翻前主动入池手数 ÷ 总手数。</p>
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;
    private final WalletService walletService;
    private final HandPlayerMapper handPlayerMapper;
    private final ActivityService activityService;
    private final com.tgpoker.mapper.NiuniuResultMapper niuniuResultMapper;

    @GetMapping
    public ApiResponse<Map<String, Object>> me(@RequestAttribute("userId") Long userId) {
        AppUser u = userService.get(userId);
        long balance = walletService.balance(userId);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("nick", u == null ? "" : u.getNick());
        m.put("avatar", u == null ? "" : u.getAvatar());
        m.put("balance", balance);
        m.put("balanceText", Money.fmt(balance));
        m.put("inviterId", u == null ? null : u.getInviterId());
        m.put("vipLevel", u == null || u.getVipLevel() == null ? 0 : u.getVipLevel());
        m.put("insuranceOptIn", u == null || u.getInsuranceOptIn() == null || u.getInsuranceOptIn() != 0);
        m.put("inviteCode", activityService.inviteCode(userId));

        Map<String, Object> longStats = statsOf(userId, "LONG");
        m.put("hands", longStats.get("hands"));
        m.put("net", longStats.get("netValue"));
        m.put("netText", longStats.get("netText"));
        m.put("avgText", longStats.get("avgText"));
        m.put("vpip", longStats.get("vpip"));
        m.put("pfr", longStats.get("pfr"));
        return ApiResponse.ok(m);
    }

    /**
     * 数据面板：长牌 / 短牌 两个 Tab，口径与桌内账单一致。
     * 总输赢 = 牌局输赢 + 保险净额。
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestAttribute("userId") Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("LONG", statsOf(userId, "LONG"));
        m.put("SHORT", statsOf(userId, "SHORT"));
        m.put("NIUNIU", niuniuStats(userId));
        return ApiResponse.ok(m);
    }

    /** 牛牛战绩：局数、总输赢、胜率、坐庄局数。 */
    private Map<String, Object> niuniuStats(long userId) {
        Map<String, Object> raw = niuniuResultMapper.stats(userId);
        long rounds = Maps.num(raw, "rounds");
        long net = Maps.num(raw, "net");
        long wins = Maps.num(raw, "win_rounds");
        long bankerRounds = Maps.num(raw, "banker_rounds");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("variant", "NIUNIU");
        m.put("rounds", rounds);
        m.put("hands", rounds);
        m.put("net", net);
        m.put("netText", Money.fmtSigned(net));
        m.put("avgText", rounds == 0 ? "0" : Money.fmtSigned(net / rounds));
        m.put("winRate", rounds == 0 ? 0 : Math.round(wins * 100f / rounds));
        m.put("bankerRounds", bankerRounds);
        m.put("insuranceNetText", "0");
        return m;
    }

    private Map<String, Object> statsOf(long userId, String variant) {
        Map<String, Object> raw = handPlayerMapper.stats(userId, variant);
        long hands = Maps.num(raw, "hands");
        long net = Maps.num(raw, "net");
        long ins = Maps.num(raw, "insurance_net");
        long vpipHands = Maps.num(raw, "vpip_hands");
        long pfrHands = Maps.num(raw, "pfr_hands");
        long total = net + ins;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("variant", variant);
        m.put("hands", hands);
        m.put("net", total);
        m.put("netValue", total);
        m.put("netText", Money.fmtSigned(total));
        m.put("insuranceNet", ins);
        m.put("insuranceNetText", Money.fmtSigned(ins));
        m.put("avgText", hands == 0 ? "0" : Money.fmtSigned(total / hands));
        m.put("vpip", hands == 0 ? 0 : Math.round(vpipHands * 100f / hands));
        m.put("pfr", hands == 0 ? 0 : Math.round(pfrHands * 100f / hands));
        return m;
    }

    @GetMapping("/hands")
    public ApiResponse<List<Map<String, Object>>> hands(@RequestAttribute("userId") Long userId,
                                                        @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(Maps.camelAll(handPlayerMapper.recent(userId, Math.min(limit, 50))));
    }

    /** 开关保险：关闭后不进报价、不能投保。 */
    @PostMapping("/insurance-opt")
    public ApiResponse<Map<String, Object>> insuranceOpt(@RequestAttribute("userId") Long userId,
                                                         @RequestBody Map<String, Object> body) {
        boolean on = body.get("on") == null || Boolean.parseBoolean(String.valueOf(body.get("on")));
        userService.setInsuranceOptIn(userId, on);
        return ApiResponse.ok(Map.of("insuranceOptIn", on));
    }

    /** 修改昵称 / 绑定邀请码。 */    @PostMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(@RequestAttribute("userId") Long userId,
                                                    @RequestBody Map<String, Object> body) {
        AppUser u = userService.get(userId);
        if (u == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (body.get("nick") != null) {
            String nick = String.valueOf(body.get("nick")).trim();
            if (nick.length() > 20) {
                throw new IllegalArgumentException("昵称过长");
            }
            u.setNick(nick);
        }
        if (body.get("inviteCode") != null && u.getInviterId() == null) {
            String code = String.valueOf(body.get("inviteCode")).trim().toUpperCase();
            if (code.startsWith("KK")) {
                try {
                    long inviter = Long.parseLong(code.substring(2), 36);
                    if (inviter != userId) {
                        u.setInviterId(inviter);
                    }
                } catch (NumberFormatException ignore) {
                    // 无效邀请码
                }
            }
        }
        userService.update(u);
        return ApiResponse.ok(Map.of("ok", true));
    }
}
