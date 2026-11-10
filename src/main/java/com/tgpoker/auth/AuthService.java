package com.tgpoker.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.tgpoker.model.AppUser;
import com.tgpoker.service.ClubService;
import com.tgpoker.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录与令牌。
 *
 * <p>生产环境必须走 initData 验签（含 auth_date 时效校验）。{@code app.dev-mode=true}
 * 时允许浏览器用 devId 模拟用户，方便本地把牌局跑起来——<b>上线前必须关掉</b>，
 * 否则任何人都能伪造任意 userId 登录。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final ClubService clubService;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    /**
     * 允许写在 app.tg.bot-token（推荐）或旧的 app.bot-token（兼容），
     * 两处任一非空即可。之前只读 app.bot-token，而配置写在 app.tg 下，导致线上验签必失败。
     */
    @Value("${app.tg.bot-token:${app.bot-token:}}")
    private String botToken;

    @Value("${app.auth.max-age-seconds:3600}")
    private long maxAgeSeconds;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> tokens = new ConcurrentHashMap<>();
    private final Map<String, Long> expireAt = new ConcurrentHashMap<>();

    public static class LoginResult {
        public String token;
        public long userId;
        public String nick;
        public String avatar;
        public boolean devMode;
    }

    public LoginResult login(String initData, Long devId, String devName) {
        JsonNode tgUser = null;
        long userId;
        String nick;
        String username = null;
        String avatar = null;

        boolean haveInitData = initData != null && !initData.isEmpty();
        if (haveInitData) {
            TelegramInitData.FailReason reason =
                    TelegramInitData.check(initData, botToken, maxAgeSeconds);
            if (reason != TelegramInitData.FailReason.OK) {
                // 带了 initData 却验不过，说明是伪造或过期。即使 dev-mode 也不能放行，
                // 否则"浏览器调试开关"就变成了线上任意用户登录后门。
                log.warn("initData 验签失败：{} | {}", reason.getText(),
                        TelegramInitData.diagnose(initData, botToken, maxAgeSeconds));
                throw new IllegalStateException("Telegram 身份校验失败：" + reason.getText());
            }
            tgUser = TelegramInitData.user(initData);
        }

        if (tgUser != null && tgUser.has("id")) {
            userId = tgUser.get("id").asLong();
            nick = tgUser.hasNonNull("first_name") ? tgUser.get("first_name").asText() : ("玩家" + (userId % 10000));
            if (tgUser.hasNonNull("last_name")) {
                nick = nick + " " + tgUser.get("last_name").asText();
            }
            username = tgUser.hasNonNull("username") ? tgUser.get("username").asText() : null;
            avatar = tgUser.hasNonNull("photo_url") ? tgUser.get("photo_url").asText() : null;
        } else {
            if (!devMode) {
                throw new IllegalStateException("缺少有效的 Telegram 身份");
            }
            // 本地模拟：用浏览器传来的稳定 id
            long id = devId == null || devId <= 0 ? 900000000L + random.nextInt(99999) : devId;
            userId = id;
            nick = (devName == null || devName.isBlank()) ? ("游客" + (id % 10000)) : devName;
        }

        AppUser u = userService.getOrCreate(userId, nick, username, avatar);

        // 所有用户默认加入主俱乐部，否则大厅看不到俱乐部与牌局记录
        try {
            clubService.join(clubService.ensureDefaultClub().getId(), userId, "MEMBER");
        } catch (Exception e) {
            log.debug("加入默认俱乐部失败 user={}: {}", userId, e.getMessage());
        }

        String token = newToken();
        tokens.put(token, userId);
        expireAt.put(token, System.currentTimeMillis() + 7L * 24 * 3600 * 1000);

        LoginResult r = new LoginResult();
        r.token = token;
        r.userId = userId;
        r.nick = u.getNick();
        r.avatar = u.getAvatar() == null ? "" : u.getAvatar();
        r.devMode = devMode;
        return r;
    }

    public Long resolve(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Long uid = tokens.get(token);
        if (uid == null) {
            return null;
        }
        Long exp = expireAt.get(token);
        if (exp != null && exp < System.currentTimeMillis()) {
            tokens.remove(token);
            expireAt.remove(token);
            return null;
        }
        return uid;
    }

    private String newToken() {
        byte[] b = new byte[24];
        random.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}
