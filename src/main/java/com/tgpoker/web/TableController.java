package com.tgpoker.web;

import com.tgpoker.model.InsuranceBetRecord;
import com.tgpoker.mapper.InsuranceBetMapper;
import com.tgpoker.room.InsuranceOdds;
import com.tgpoker.room.TableActor;
import com.tgpoker.room.TableLedgerRow;
import com.tgpoker.room.TableManager;
import com.tgpoker.service.RedPacketService;
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
 * 牌桌相关接口。
 *
 * <p>线上玩法走 WebSocket（{@code /ws/table}），这里的 HTTP 接口主要承担三件事：</p>
 * <ol>
 *   <li>大厅列表与首屏快照（弱网/调试时兜底）；</li>
 *   <li>桌内三 Tab：账单、保险、红包；</li>
 *   <li>脚本化回归：入座/行动/开局可以直接用 curl 跑通一手牌。</li>
 * </ol>
 *
 * <p>注意：入座、行动都是投递给牌桌线程异步执行的，接口返回 ok 不代表动作已被规则接受，
 * 真正的错误通过 WebSocket 的 error 消息回推。</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TableController {

    private final TableManager tableManager;
    private final RedPacketService redPacketService;
    private final InsuranceBetMapper insuranceBetMapper;

    @GetMapping("/lobby/tables")
    public ApiResponse<List<TableActor.LobbyItem>> tables(@RequestParam(required = false) Long clubId,
                                                          @RequestParam(required = false) String variant) {
        return ApiResponse.ok(tableManager.lobby(clubId, variant));
    }

    @GetMapping("/table/{tableId}")
    public ApiResponse<Object> view(@PathVariable String tableId,
                                    @RequestAttribute("userId") Long userId) {
        TableActor actor = tableManager.get(tableId);
        if (actor == null) {
            throw new IllegalStateException("牌桌不存在");
        }
        return ApiResponse.ok(actor.viewFor(userId));
    }

    @PostMapping("/table/{tableId}/sit")
    public ApiResponse<Void> sit(@PathVariable String tableId,
                                 @RequestAttribute("userId") Long userId,
                                 @RequestBody Map<String, Object> body) {
        TableActor actor = tableManager.get(tableId);
        if (actor == null) {
            throw new IllegalStateException("牌桌不存在");
        }
        long buyIn = Long.parseLong(String.valueOf(body.getOrDefault("buyIn", "0")));
        int seat = body.get("seat") == null ? -1 : Integer.parseInt(String.valueOf(body.get("seat")));
        String nick = body.get("nick") == null ? "玩家" : String.valueOf(body.get("nick"));
        String avatar = body.get("avatar") == null ? "" : String.valueOf(body.get("avatar"));
        actor.sit(userId, nick, avatar, buyIn, seat);
        return ApiResponse.ok(null);
    }

    @PostMapping("/table/{tableId}/rebuy")
    public ApiResponse<Void> rebuy(@PathVariable String tableId,
                                   @RequestAttribute("userId") Long userId,
                                   @RequestBody Map<String, Object> body) {
        TableActor actor = tableManager.get(tableId);
        if (actor != null) {
            actor.rebuy(userId, Long.parseLong(String.valueOf(body.getOrDefault("amount", "0"))));
        }
        return ApiResponse.ok(null);
    }

    @PostMapping("/table/{tableId}/leave")
    public ApiResponse<Void> leave(@PathVariable String tableId,
                                   @RequestAttribute("userId") Long userId) {
        TableActor actor = tableManager.get(tableId);
        if (actor != null) {
            actor.leave(userId);
        }
        return ApiResponse.ok(null);
    }

    @PostMapping("/table/{tableId}/start")
    public ApiResponse<Void> start(@PathVariable String tableId) {
        TableActor actor = tableManager.get(tableId);
        if (actor != null) {
            actor.startNow();
        }
        return ApiResponse.ok(null);
    }

    /**
     * HTTP 行动入口。线上走 WebSocket，这个接口主要给本地调试和脚本回归用。
     */
    @PostMapping("/table/{tableId}/action")
    public ApiResponse<Void> action(@PathVariable String tableId,
                                    @RequestAttribute("userId") Long userId,
                                    @RequestBody Map<String, Object> body) {
        TableActor actor = tableManager.get(tableId);
        if (actor == null) {
            throw new IllegalStateException("牌桌不存在");
        }
        String act = String.valueOf(body.getOrDefault("act", "CHECK"));
        long amt = Long.parseLong(String.valueOf(body.getOrDefault("amt", "0")));
        actor.act(userId, com.tgpoker.engine.ActionType.valueOf(act), amt);
        return ApiResponse.ok(null);
    }

    // ---------------------------------------------------------------- 桌内三 Tab

    /** 账单：昵称 / 买入 / 手数 / 入池率 / 输赢。买入只对本人返回。 */
    @GetMapping("/table/{tableId}/ledger")
    public ApiResponse<List<TableLedgerRow>> ledger(@PathVariable String tableId,
                                                    @RequestAttribute("userId") Long userId) {
        TableActor actor = tableManager.get(tableId);
        if (actor == null) {
            throw new IllegalStateException("牌桌不存在");
        }
        return ApiResponse.ok(actor.ledger(userId));
    }

    /** 保险流水：本桌最近的保险单（桌内公开，反串通）。 */
    @GetMapping("/table/{tableId}/insurance")
    public ApiResponse<List<Map<String, Object>>> insurance(@PathVariable String tableId) {
        TableActor actor = tableManager.get(tableId);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        if (actor == null) {
            return ApiResponse.ok(out);
        }
        for (var io : actor.insuranceHistory()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("handNo", io.handNo);
            m.put("nick", io.nick);
            m.put("street", "转牌");
            m.put("outs", io.outs);
            m.put("odds", io.odds);
            m.put("premiumText", Money.fmt(io.premiumMain + io.premiumTie));
            m.put("coverageText", Money.fmt(io.coverage));
            m.put("payoutText", Money.fmt(io.payout));
            m.put("result", io.result);
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    /** 保险报价：当前是否可买、outs、赔率、应付保费。 */
    @GetMapping("/table/{tableId}/insurance/quote")
    public ApiResponse<Map<String, Object>> insuranceQuote(@PathVariable String tableId,
                                                           @RequestAttribute("userId") Long userId) {
        TableActor actor = tableManager.get(tableId);
        if (actor == null) {
            throw new IllegalStateException("牌桌不存在");
        }
        InsuranceOdds.Quote q = actor.quoteInsurance(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", q.available);
        m.put("outs", q.outs);
        m.put("odds", q.odds);
        m.put("oddsText", q.oddsText());
        m.put("maxCoverage", q.maxCoverage);
        m.put("maxCoverageText", Money.fmt(q.maxCoverage));
        m.put("premium", q.premium);
        m.put("premiumText", Money.fmt(q.premium));
        m.put("reason", q.reason);
        return ApiResponse.ok(m);
    }

    @PostMapping("/table/{tableId}/insurance")
    public ApiResponse<Void> buyInsurance(@PathVariable String tableId,
                                          @RequestAttribute("userId") Long userId,
                                          @RequestBody Map<String, Object> body) {
        TableActor actor = tableManager.get(tableId);
        if (actor == null) {
            throw new IllegalStateException("牌桌不存在");
        }
        long coverage = Long.parseLong(String.valueOf(body.getOrDefault("coverage", "0")));
        actor.buyInsurance(userId, coverage);
        return ApiResponse.ok(null);
    }

    /** 桌内发红包：与大厅红包共用同一套资金通道。 */
    @PostMapping("/table/{tableId}/redpacket")
    public ApiResponse<Map<String, Object>> redPacket(@PathVariable String tableId,
                                                      @RequestAttribute("userId") Long userId,
                                                      @RequestBody Map<String, Object> body) {
        long receiverId = Long.parseLong(String.valueOf(body.getOrDefault("receiverId", "0")));
        long amount = Long.parseLong(String.valueOf(body.getOrDefault("amount", "0")));
        String memo = body.get("memo") == null ? "" : String.valueOf(body.get("memo"));
        return ApiResponse.ok(redPacketService.send(userId, receiverId, amount, memo, "TABLE", tableId));
    }

    /** 我的保险记录（跨桌）。 */
    @GetMapping("/me/insurances")
    public ApiResponse<List<InsuranceBetRecord>> myInsurances(@RequestAttribute("userId") Long userId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<InsuranceBetRecord> q =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        q.eq("buyer_user_id", userId).orderByDesc("id").last("LIMIT 30");
        return ApiResponse.ok(insuranceBetMapper.selectList(q));
    }
}
