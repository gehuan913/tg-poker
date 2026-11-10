package com.tgpoker.ws;

import com.tgpoker.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** 握手时校验 token，把 userId / tableId 塞进 session attributes。 */
@Component
@RequiredArgsConstructor
public class TableHandshakeInterceptor implements HandshakeInterceptor {

    private final AuthService authService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Map<String, String> q = parseQuery(request.getURI().getRawQuery());
        Long userId = authService.resolve(q.get("token"));
        String tableId = q.get("tableId");
        if (userId == null || tableId == null || tableId.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put("userId", userId);
        attributes.put("tableId", tableId);
        // role 必须显式声明：SEATED / SPECTATOR。缺省按旁观处理（最小权限）。
        String role = q.getOrDefault("role", "SPECTATOR");
        attributes.put("role", "SEATED".equalsIgnoreCase(role) ? "SEATED" : "SPECTATOR");
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null) {
            return map;
        }
        for (String pair : rawQuery.split("&")) {
            int i = pair.indexOf('=');
            if (i <= 0) {
                continue;
            }
            String k = pair.substring(0, i);
            String v = pair.substring(i + 1);
            try {
                v = URLDecoder.decode(v, StandardCharsets.UTF_8.name());
            } catch (Exception ignore) {
                // ignore
            }
            map.put(k, v);
        }
        return map;
    }
}
