package com.tgpoker.web;

import com.tgpoker.engine.Card;
import com.tgpoker.engine.EquityCalculator;
import com.tgpoker.engine.HandEvaluator;
import com.tgpoker.engine.Variant;
import com.tgpoker.room.InsuranceOdds;
import com.tgpoker.service.TeachingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开发/联调工具接口。生产环境应通过网关屏蔽。
 *
 * <p>保险定价、牌型大小这类东西必须能单独验算，否则只能等真钱跑起来才发现错了。</p>
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevController {

    /** 保险报价演练：给出双方底牌与转牌圈公牌，返回 outs / 赔率 / 保费。 */
    @GetMapping("/insurance-quote")
    public ApiResponse<Map<String, Object>> insuranceQuote(@RequestParam String holeA,
                                                           @RequestParam String holeB,
                                                           @RequestParam String board,
                                                           @RequestParam(defaultValue = "LONG") String variant,
                                                           @RequestParam(defaultValue = "100000000") long pot) {
        Variant v = Variant.of(variant);
        Card[] a = TeachingService.parseCards(holeA, 2);
        Card[] b = TeachingService.parseCards(holeB, 2);
        List<Card> bd = new ArrayList<>();
        for (Card c : TeachingService.parseCards(board, 5)) {
            bd.add(c);
        }
        InsuranceOdds.Quote q = InsuranceOdds.quote(v, a, b, bd, pot, 0, 1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", q.available);
        m.put("outs", q.outs);
        m.put("tieOuts", q.tieOuts);
        m.put("odds", q.odds);
        m.put("maxCoverage", q.maxCoverage);
        m.put("premium", q.premium);
        m.put("premiumText", com.tgpoker.util.Money.fmt(q.premium));
        m.put("buyerSeat", q.buyerSeat);
        m.put("behindSeat", q.behindSeat);
        m.put("reason", q.reason);
        return ApiResponse.ok(m);
    }

    /** 牌型比较：用于确认短牌"同花 > 葫芦、三条 > 顺子"没写反。 */
    @GetMapping("/rank")
    public ApiResponse<Map<String, Object>> rank(@RequestParam String cards,
                                                 @RequestParam(defaultValue = "LONG") String variant) {
        Variant v = Variant.of(variant);
        Card[] cs = TeachingService.parseCards(cards, 7);
        long score = HandEvaluator.best(cs, cs.length, v);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", HandEvaluator.category(score));
        m.put("name", HandEvaluator.name(score, v));
        m.put("score", score);
        return ApiResponse.ok(m);
    }

    /** 胜率演练（便于核对与前端计算器一致）。 */
    @GetMapping("/equity")
    public ApiResponse<EquityCalculator.Result> equity(@RequestParam String hole,
                                                       @RequestParam(defaultValue = "1") int opponents,
                                                       @RequestParam(defaultValue = "LONG") String variant) {
        Card[] cs = TeachingService.parseCards(hole, 2);
        return ApiResponse.ok(EquityCalculator.compute(cs, List.of(), opponents, Variant.of(variant), 20000));
    }
}
