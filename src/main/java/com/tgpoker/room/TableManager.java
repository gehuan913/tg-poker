package com.tgpoker.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgpoker.service.ClubService;
import com.tgpoker.service.HandService;
import com.tgpoker.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 牌桌管理与连接广播。
 *
 * <p>客户端通过 {@code /ws/table?tableId=xx&token=yy&role=SEATED|SPECTATOR} 订阅。
 * <b>role 必须显式声明</b>：不能靠"有没有座位号"推断，否则一次越权就会把所有人底牌
 * 推给旁观者。旁观者额外做 0.5s 批量合并，控制 fanout。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TableManager implements TableEvents {

    private static final long SPECTATOR_INTERVAL_MS = 500;

    private final WalletService walletService;
    private final HandService handService;
    private final ClubService clubService;
    private final com.tgpoker.service.UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, TableActor> tables = new LinkedHashMap<>();
    private final Map<String, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionUser = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRole = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pendingFlush = new ConcurrentHashMap<>();

    private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(4, r -> {
        Thread t = new Thread(r, "table-timer");
        t.setDaemon(true);
        return t;
    });

    private long defaultClubId;

    @PostConstruct
    public void init() {
        defaultClubId = clubService.ensureDefaultClub().getId();
        // 长牌：0.2/0.4、1/2、5/10
        createTable(new TableConfig("T61510", "永旺娱乐(长牌)", 200_000L, 400_000L,
                10_000_000L, 100_000_000L, 7).withVariant("LONG").withClub(defaultClubId, true));
        createTable(new TableConfig("T61502", "进阶(长牌)", 1_000_000L, 2_000_000L,
                40_000_000L, 400_000_000L, 7).withVariant("LONG").withClub(defaultClubId, true));
        // 三级 + Straddle：0.1/0.2 + UTG 0.4（对应产品文档 0.1/0.2/0.4(0.4)）
        createTable(new TableConfig("T61398", "进阶(长牌·Straddle)", 100_000L, 200_000L,
                20_000_000L, 200_000_000L, 7).withVariant("LONG")
                .withClub(defaultClubId, true).withStraddle(true));
        createTable(new TableConfig("T61516", "高手(长牌)", 10_000_000L, 20_000_000L,
                200_000_000L, 2_000_000_000L, 7).withVariant("LONG").withClub(defaultClubId, true));
        // 短牌：前注制，按钮 2 倍
        createTable(new TableConfig("S61506", "永旺娱乐(短牌)", 0L, 400_000L,
                10_000_000L, 100_000_000L, 7).withVariant("SHORT")
                .withAnte(200_000L, "BUTTON_DOUBLE").withClub(defaultClubId, true));
        createTable(new TableConfig("S61390", "短牌(小注)", 0L, 200_000L,
                10_000_000L, 60_000_000L, 7).withVariant("SHORT")
                .withAnte(100_000L, "BUTTON_DOUBLE").withClub(defaultClubId, true));
        createTable(new TableConfig("S61391", "三倍前注(短牌)", 0L, 10_000_000L,
                200_000_000L, 2_000_000_000L, 7).withVariant("SHORT")
                .withAnte(10_000_000L, "BUTTON_DOUBLE").withClub(defaultClubId, true));
        log.info("牌桌初始化完成，共 {} 桌，默认俱乐部 {}", tables.size(), defaultClubId);
    }

    private void createTable(TableConfig cfg) {
        tables.put(cfg.id, new TableActor(cfg, this, timer));
    }

    public long defaultClubId() {
        return defaultClubId;
    }

    /** 大厅列表，可按俱乐部和玩法过滤。 */
    public List<TableActor.LobbyItem> lobby(Long clubId, String variant) {
        List<TableActor.LobbyItem> list = new ArrayList<>();
        for (TableActor a : tables.values()) {
            if (clubId != null && clubId > 0 && a.getConfig().clubId != clubId) {
                continue;
            }
            if (variant != null && !variant.isBlank() && !"ALL".equalsIgnoreCase(variant)
                    && !a.getConfig().variant.equalsIgnoreCase(variant)) {
                continue;
            }
            list.add(a.summary());
        }
        return list;
    }

    public TableActor get(String tableId) {
        return tables.get(tableId);
    }

    public Map<String, TableActor> all() {
        return tables;
    }

    // ---------------------------------------------------------------- 连接

    /**
     * 生效角色：<b>必须由服务端根据真实座位判定</b>，不能相信客户端传来的 role。
     * 客户端声明只能用来"主动降级为旁观"，不能用来升级。
     */
    private String effectiveRole(TableActor actor, long userId) {
        boolean seated = actor != null && actor.getState().seatOf(userId) != null;
        return seated ? "SEATED" : "SPECTATOR";
    }

    public void register(String tableId, long userId, String role, WebSocketSession session) {
        boolean watchOnly = "SPECTATOR".equalsIgnoreCase(role);
        sessions.computeIfAbsent(tableId, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
        sessionUser.put(session.getId(), userId);
        sessionRole.put(session.getId(), watchOnly ? "SPECTATOR" : "AUTO");
        log.debug("连接加入 table={} user={} role={} 当前 {} 人",
                tableId, userId, sessionRole.get(session.getId()), sessions.get(tableId).size());
        // 首次直接推一份，别等下一次变更
        sendView(tableId, userId, effectiveRole(tables.get(tableId), userId), session);
    }

    public void unregister(String tableId, WebSocketSession session) {
        Map<String, WebSocketSession> map = sessions.get(tableId);
        if (map != null) {
            map.remove(session.getId());
        }
        sessionUser.remove(session.getId());
        sessionRole.remove(session.getId());
        lastSentAt.remove(session.getId());
        pendingFlush.remove(session.getId());
    }

    public Long userOf(WebSocketSession session) {
        return sessionUser.get(session.getId());
    }

    public String roleOf(WebSocketSession session) {
        return sessionRole.getOrDefault(session.getId(), "SPECTATOR");
    }

    public int spectatorCount(String tableId) {
        Map<String, WebSocketSession> map = sessions.get(tableId);
        if (map == null) {
            return 0;
        }
        TableActor actor = tables.get(tableId);
        int n = 0;
        for (String sid : map.keySet()) {
            Long uid = sessionUser.get(sid);
            boolean watchOnly = "SPECTATOR".equals(sessionRole.get(sid));
            boolean seated = actor != null && uid != null && actor.getState().seatOf(uid) != null;
            if (watchOnly || !seated) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 广播

    @Override
    public void onUpdate(TableActor actor) {
        String tableId = actor.getConfig().id;
        Map<String, WebSocketSession> map = sessions.get(tableId);
        if (map == null || map.isEmpty()) {
            return;
        }
        int spectators = spectatorCount(tableId);
        long now = System.currentTimeMillis();

        for (WebSocketSession s : map.values()) {
            Long uid = sessionUser.get(s.getId());
            if (uid == null) {
                continue;
            }
            String role = "SPECTATOR".equals(sessionRole.get(s.getId()))
                    ? "SPECTATOR" : effectiveRole(actor, uid);
            String json;
            try {
                TableView v = actor.viewFor(uid, role);
                v.spectators = spectators;
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("t", "table");
                msg.put("data", v);
                json = objectMapper.writeValueAsString(msg);
            } catch (Exception e) {
                log.warn("构造视图失败 user={}", uid, e);
                continue;
            }

            if ("SPECTATOR".equals(role)) {
                Long last = lastSentAt.get(s.getId());
                if (last != null && now - last < SPECTATOR_INTERVAL_MS) {
                    // 0.5s 内只推一次：留一个待发标记，到点补最新状态
                    if (!pendingFlush.containsKey(s.getId())) {
                        pendingFlush.put(s.getId(), true);
                        timer.schedule(() -> flushSpectator(actor, s), SPECTATOR_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    }
                    continue;
                }
            }
            lastSentAt.put(s.getId(), now);
            final String payload = json;
            timer.execute(() -> send(s, payload));
        }
    }

    /** 旁观者节流补发：保证最后一个状态一定到达。 */
    private void flushSpectator(TableActor actor, WebSocketSession s) {
        pendingFlush.remove(s.getId());
        if (s == null || !s.isOpen()) {
            return;
        }
        Long uid = sessionUser.get(s.getId());
        if (uid == null) {
            return;
        }
        try {
            TableView v = actor.viewFor(uid, "SPECTATOR");
            v.spectators = spectatorCount(actor.getConfig().id);
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("t", "table");
            msg.put("data", v);
            lastSentAt.put(s.getId(), System.currentTimeMillis());
            send(s, objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.debug("旁观补发失败: {}", e.getMessage());
        }
    }

    private void sendView(String tableId, long userId, String role, WebSocketSession session) {
        TableActor actor = tables.get(tableId);
        if (actor == null) {
            return;
        }
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            TableView v = actor.viewFor(userId, role);
            v.spectators = Math.max(0, spectatorCount(tableId) - ("SPECTATOR".equals(role) ? 1 : 0));
            msg.put("t", "table");
            msg.put("data", v);
            send(session, objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.warn("推送初始视图失败", e);
        }
    }

    private void send(WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.debug("发送失败，连接可能已关闭: {}", e.getMessage());
        }
    }

    @Override
    public void onError(long userId, String message) {
        sessionUser.forEach((sid, uid) -> {
            if (uid != null && uid == userId) {
                sessions.values().forEach(m -> {
                    WebSocketSession s = m.get(sid);
                    if (s != null) {
                        try {
                            Map<String, Object> msg = new LinkedHashMap<>();
                            msg.put("t", "error");
                            msg.put("message", message);
                            send(s, objectMapper.writeValueAsString(msg));
                        } catch (Exception ignore) {
                            // ignore
                        }
                    }
                });
            }
        });
    }

    // ---------------------------------------------------------------- 资金回调

    @Override
    public boolean onBuyIn(long userId, long amount, String bizId) {
        return walletService.buyIn(userId, amount, bizId);
    }

    @Override
    public void onCashOut(long userId, long amount, long tableStack, String memo) {
        walletService.cashOut(userId, amount, "cashout-" + System.currentTimeMillis() + "-" + userId);
    }

    @Override
    public void onHandSettled(TableActor actor, HandOutcome outcome) {
        try {
            handService.save(outcome);
        } catch (Exception e) {
            log.error("手牌落库失败 {}", outcome.handId, e);
        }
    }

    @Override
    public boolean onInsurancePremium(long userId, long amount, String bizId) {
        return walletService.insurancePremium(userId, amount, bizId);
    }

    @Override
    public void onInsurancePayout(long userId, long amount, String bizId) {
        walletService.insurancePayout(userId, amount, bizId);
    }

    @Override
    public boolean onTreasuryReserve(long clubId, long amount) {
        return clubService.canReserve(clubId, amount);
    }

    @Override
    public void onTreasurySettle(long clubId, long delta) {
        try {
            clubService.settle(clubId, delta);
        } catch (Exception e) {
            log.error("金库结算失败 club={} delta={}", clubId, delta, e);
        }
    }

    @Override
    public boolean insuranceOptIn(long userId) {
        return userService.isInsuranceOptIn(userId);
    }
}
