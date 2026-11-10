package com.tgpoker.niuniu;

import com.tgpoker.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** 牛牛桌管理。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NiuniuManager {

    private final WalletService walletService;
    private final com.tgpoker.mapper.NiuniuResultMapper resultMapper;

    private final Map<String, NiuniuRoom> rooms = new LinkedHashMap<>();

    private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "niuniu-timer");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void init() {
        // 每局结算后把"局 × 人"的战绩落库，供"我的-牛牛"数据面板统计
        java.util.function.Consumer<java.util.List<com.tgpoker.model.NiuniuResult>> recorder = rows -> {
            for (com.tgpoker.model.NiuniuResult r : rows) {
                try {
                    resultMapper.insert(r);
                } catch (Exception e) {
                    log.warn("牛牛战绩写入失败 user={} round={}", r.getUserId(), r.getRoundNo());
                }
            }
        };
        rooms.put("N01", new NiuniuRoom("N01", "牛牛(0.2/0.4)", 10_000_000L, 100_000_000L, 5_000_000L,
                walletService, timer, recorder));
        rooms.put("N02", new NiuniuRoom("N02", "牛牛(1/2)", 40_000_000L, 400_000_000L, 20_000_000L,
                walletService, timer, recorder));
        log.info("牛牛桌初始化完成，共 {} 桌", rooms.size());
    }

    public NiuniuRoom get(String tableId) {
        return rooms.get(tableId);
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (NiuniuRoom r : rooms.values()) {
            NiuniuRoom.View v = r.view(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tableId", v.tableId);
            m.put("name", v.name);
            m.put("stage", v.stage);
            int seated = 0;
            for (Map<String, Object> s : v.seats) {
                if (Boolean.TRUE.equals(s.get("seated"))) {
                    seated++;
                }
            }
            m.put("seated", seated);
            m.put("maxSeats", NiuniuRoom.MAX_SEATS);
            m.put("roundNo", v.roundNo);
            m.put("log", v.log);
            out.add(m);
        }
        return out;
    }
}
