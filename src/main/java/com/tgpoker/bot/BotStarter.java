package com.tgpoker.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgpoker.service.WalletService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

/**
 * Bot 启停与 HTTP 客户端装配。
 *
 * <p>两件事必须一起配：长轮询（拉 getUpdates）和发消息（SendMessage）各用一个 OkHttpClient，
 * 只给其中一个配代理，会出现"能收不能发"或反之。</p>
 *
 * <p>本地/国内网络直连 api.telegram.org 常常超时，所以支持
 * {@code app.tg.proxy-host / proxy-port / proxy-type} 三个配置。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotStarter {

    /** 欢迎卡要展示资产，Bot 直接查钱包（只读）。 */
    private final WalletService walletService;

    @Value("${app.tg.enabled:true}")
    private boolean enabled;

    @Value("${app.tg.bot-token:}")
    private String botToken;

    @Value("${app.tg.bot-username:}")
    private String botUsername;

    @Value("${app.tg.mini-app-url:}")
    private String miniAppUrl;

    @Value("${app.tg.group-url:}")
    private String groupUrl;

    @Value("${app.tg.channel-url:}")
    private String channelUrl;

    /** 欢迎卡顶部配图（HTTPS 图片地址），留空则发纯文本。 */
    @Value("${app.tg.welcome-image-url:}")
    private String welcomeImageUrl;

    /** 代理地址，留空表示直连。 */
    @Value("${app.tg.proxy-host:}")
    private String proxyHost;

    /** 代理端口。 */
    @Value("${app.tg.proxy-port:0}")
    private int proxyPort;

    /** 代理类型：HTTP / SOCKS。 */
    @Value("${app.tg.proxy-type:HTTP}")
    private String proxyType;

    private TelegramBotsLongPollingApplication app;
    private PokerBot bot;
    private java.util.concurrent.ScheduledExecutorService retryTimer;
    private final java.util.concurrent.atomic.AtomicInteger retries = new java.util.concurrent.atomic.AtomicInteger();

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("app.tg.enabled=false，Telegram Bot 不启动");
            return;
        }
        if (botToken == null || botToken.isBlank()) {
            log.warn("未配置 app.tg.bot-token，Telegram Bot 不启动（Mini App 仍可访问）");
            return;
        }
        tryStart();
    }

    /** 启动（失败时每分钟重试，最多 10 次；常见原因是开机时代理还没起来）。 */
    private void tryStart() {
        OkHttpClient http = buildHttpClient();
        try {
            bot = new PokerBot(http, botToken, botUsername, miniAppUrl, groupUrl, channelUrl,
                    welcomeImageUrl, walletService);
            // 长轮询也必须走同一个客户端，否则代理只对发消息生效
            app = new TelegramBotsLongPollingApplication(ObjectMapper::new, () -> http);
            app.registerBot(botToken, bot);
            bot.initMenu();
            log.info("Telegram Bot 已启动（长轮询，{}）",
                    proxyHost == null || proxyHost.isBlank() ? "直连" : "代理 " + proxyHost + ":" + proxyPort);
        } catch (Exception e) {
            app = null;
            bot = null;
            log.error("Telegram Bot 启动失败：{}", e.getMessage());
            log.error("排查顺序：1) 能否访问 api.telegram.org；2) token 是否正确；"
                    + "3) 需要代理时配置 app.tg.proxy-host / proxy-port / proxy-type");
            scheduleRetry();
        }
    }

    private void scheduleRetry() {
        int n = retries.incrementAndGet();
        if (n > 10) {
            log.error("Bot 已重试 10 次仍失败，停止重试；配好网络或代理后重启服务即可");
            return;
        }
        if (retryTimer == null) {
            retryTimer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "bot-retry");
                t.setDaemon(true);
                return t;
            });
        }
        log.warn("{} 秒后进行第 {} 次重试", 60, n);
        retryTimer.schedule(this::tryStart, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** 统一构造带超时与可选代理的客户端。 */
    private OkHttpClient buildHttpClient() {
        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(70, TimeUnit.SECONDS)   // 长轮询是 60s，读超时要大于它
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);

        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            Proxy.Type type = "SOCKS".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            b.proxy(new Proxy(type, new InetSocketAddress(proxyHost.trim(), proxyPort)));
        }
        return b.build();
    }

    @PreDestroy
    public void stop() {
        if (retryTimer != null) {
            retryTimer.shutdownNow();
        }
        if (app != null) {
            try {
                app.close();
            } catch (Exception ignore) {
                // 关闭失败不影响进程退出
            }
        }
    }
}
