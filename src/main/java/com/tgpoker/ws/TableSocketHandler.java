package com.tgpoker.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgpoker.engine.ActionType;
import com.tgpoker.room.InsuranceOdds;
import com.tgpoker.room.TableActor;
import com.tgpoker.room.TableManager;
import com.tgpoker.util.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 牌桌长连接。
 *
 * <p>连上来只是「订阅」，不会自动占座；断线也不自动弃牌，座位保留一段时间，
 * 重连后立刻收到全量视图（相当于 snapshot）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TableSocketHandler extends TextWebSocketHandler {

    private final TableManager tableManager;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String tableId = (String) session.getAttributes().get("tableId");
        Long userId = (Long) session.getAttributes().get("userId");
        String role = (String) session.getAttributes().get("role");
        if (tableId == null || userId == null) {
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (Exception ignore) {
                // ignore
            }
            return;
        }
        tableManager.register(tableId, userId, role == null ? "SPECTATOR" : role, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String tableId = (String) session.getAttributes().get("tableId");
        Long userId = (Long) session.getAttributes().get("userId");
        TableActor actor = tableManager.get(tableId);
        if (actor == null) {
            return;
        }

        try {
            JsonNode n = mapper.readTree(message.getPayload());
            String t = n.path("t").asText("");

            switch (t) {
                case "ping":
                    sendRaw(session, "{\"t\":\"pong\"}");
                    break;
                case "action": {
                    ActionType type = ActionType.valueOf(n.path("act").asText("CHECK"));
                    long amt = n.path("amt").asLong(0);
                    actor.act(userId, type, amt);
                    break;
                }
                case "sit": {
                    long buyIn = n.path("buyIn").asLong(0);
                    int seat = n.path("seat").asInt(-1);
                    String nick = n.path("nick").asText("玩家");
                    String avatar = n.path("avatar").asText("");
                    actor.sit(userId, nick, avatar, buyIn, seat);
                    break;
                }
                case "rebuy": {
                    actor.rebuy(userId, n.path("amount").asLong(0));
                    break;
                }
                case "leave":
                    actor.leave(userId);
                    break;
                case "start":
                    actor.startNow();
                    break;
                case "clientSeed":
                    actor.submitClientSeed(userId, n.path("seed").asText(""));
                    break;
                case "chat":
                    actor.chat(userId, n.path("text").asText(""));
                    break;
                case "insuranceQuote": {
                    InsuranceOdds.Quote q = actor.quoteInsurance(userId);
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("t", "insuranceQuote");
                    msg.put("available", q.available);
                    msg.put("outs", q.outs);
                    msg.put("odds", q.odds);
                    msg.put("maxCoverage", q.maxCoverage);
                    msg.put("premium", q.premium);
                    msg.put("premiumText", Money.fmt(q.premium));
                    msg.put("reason", q.reason);
                    sendRaw(session, mapper.writeValueAsString(msg));
                    break;
                }
                case "insurance":
                    actor.buyInsurance(userId, n.path("coverage").asLong(0));
                    break;
                default:
                    log.debug("未知消息类型 {}", t);
            }
        } catch (IllegalArgumentException e) {
            sendRaw(session, "{\"t\":\"error\",\"message\":\"非法动作\"}");
        } catch (Exception e) {
            log.warn("处理消息失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String tableId = (String) session.getAttributes().get("tableId");
        if (tableId != null) {
            tableManager.unregister(tableId, session);
        }
    }

    private void sendRaw(WebSocketSession session, String json) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception ignore) {
            // ignore
        }
    }
}
