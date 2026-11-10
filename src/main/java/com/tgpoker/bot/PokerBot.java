package com.tgpoker.bot;

import com.tgpoker.service.WalletService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButtonWebApp;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 德州扑克 Bot。
 *
 * <p>职责很轻：只做入口。用户 /start 后发一张带 WebApp 按钮的欢迎卡，
 * 点按钮在 Telegram 内打开 Mini App。牌局逻辑全在 WebSocket 里，Bot 不参与。</p>
 */
@Slf4j
public class PokerBot implements LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient client;
    private final String miniAppUrl;
    private final String groupUrl;
    private final String channelUrl;
    private final String botUsername;
    private final String welcomeImageUrl;
    private final WalletService walletService;

    /**
     * @param http            统一装配的 HTTP 客户端（含超时与代理），长轮询与发消息共用
     * @param botToken        BotFather 发放的 token
     * @param botUsername     Bot 用户名（不带 @），用于生成邀请链接
     * @param miniAppUrl      Mini App 的 HTTPS 地址，空则不发 WebApp 按钮
     * @param groupUrl        观战群组链接
     * @param channelUrl      官方频道链接
     * @param welcomeImageUrl 欢迎卡顶部配图（HTTPS），空则发纯文本
     * @param walletService   查余额，用于欢迎卡展示资产
     */
    public PokerBot(OkHttpClient http, String botToken, String botUsername,
                    String miniAppUrl, String groupUrl, String channelUrl,
                    String welcomeImageUrl, WalletService walletService) {
        this.client = new OkHttpTelegramClient(http, botToken);
        this.miniAppUrl = miniAppUrl;
        this.groupUrl = groupUrl;
        this.channelUrl = channelUrl;
        this.botUsername = botUsername == null ? "" : botUsername.trim();
        this.welcomeImageUrl = welcomeImageUrl == null ? "" : welcomeImageUrl.trim();
        this.walletService = walletService;
    }

    /** 设置命令列表与菜单按钮。失败只打日志，不影响服务启动。 */
    public void initMenu() {
        try {
            client.execute(SetMyCommands.builder()
                    .commands(List.of(
                            BotCommand.builder().command("start").description("开始").build(),
                            BotCommand.builder().command("balance").description("查询余额").build(),
                            BotCommand.builder().command("help").description("玩法说明").build(),
                            BotCommand.builder().command("invite").description("我的邀请码").build(),
                            BotCommand.builder().command("fair").description("发牌怎么验证").build()
                    ))
                    .scope(BotCommandScopeDefault.builder().build())
                    .build());
        } catch (Exception e) {
            log.warn("设置 Bot 命令失败: {}", e.getMessage());
        }

        if (isHttps(miniAppUrl)) {
            try {
                client.execute(SetChatMenuButton.builder()
                        .menuButton(MenuButtonWebApp.builder()
                                .text("开始游戏")
                                .webAppInfo(new WebAppInfo(miniAppUrl))
                                .build())
                        .build());
                log.info("Bot 菜单按钮已指向 {}", miniAppUrl);
            } catch (Exception e) {
                log.warn("设置菜单按钮失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText();
                long chatId = update.getMessage().getChatId();
                User from = update.getMessage().getFrom();

                if (text.startsWith("/help")) {
                    sendText(chatId, "玩法：长牌 52 张 / 短牌 36 张（前注制，按钮 2 倍）/ 牛牛 1 庄 3 闲。\n"
                            + "上桌从钱包扣买入，离桌自动退回。\n"
                            + "发牌可验证：开局公示 serverSeed 的 SHA256，玩家种子参与洗牌，结算后揭示 seed，可离线复算。\n"
                            + "输入 /fair 看验证方法，/invite 拿邀请码。");
                } else if (text.startsWith("/invite")) {
                    long uid = from == null ? 0 : from.getId();
                    String code = inviteCode(uid);
                    String link = botUsername.isEmpty()
                            ? code
                            : "https://t.me/" + botUsername + "?startapp=" + code;
                    sendText(chatId, "我的邀请码：" + code
                            + "\n邀请链接：" + link
                            + "\n好友通过链接进入并打过一手牌，你可领 1 USDT 推荐红包。");
                } else if (text.startsWith("/fair")) {
                    sendText(chatId, "可验证发牌三步：\n"
                            + "1. 牌桌右上角复制存证号（如 565#F1721C1DEA）\n"
                            + "2. 结算后点「可存证」拿到 serverSeed 与玩家种子\n"
                            + "3. 下载 verify.js 离线复算整副牌序：node verify.js serverSeed \"clientSeeds\" nonce LONG\n"
                            + "算出的牌序与记录不一致，说明平台作弊。");
                } else if (text.startsWith("/balance")) {
                    long uid = from == null ? 0 : from.getId();
                    sendText(chatId, "💰 当前资产：<b>" + usdt(uid) + " USDT</b>\n"
                            + "完整账本明细在 Mini App 的「我的」页面。");
                } else {
                    sendWelcome(chatId, from);
                }
                return;
            }

            if (update.hasMessage() && update.getMessage().getWebAppData() != null) {
                sendText(update.getMessage().getChatId(), "已收到小程序回传数据。");
            }
        } catch (Exception e) {
            log.error("处理 update 失败", e);
        }
    }

    /**
     * 欢迎卡：配了 welcome-image-url 就发图（图下带说明），否则发纯文本。
     * 按钮排成一行，「开始游戏」是 WebApp 按钮，「观战群组」是普通链接。
     */
    private void sendWelcome(long chatId, User from) {
        long uid = from == null ? 0 : from.getId();
        String nick = from == null ? "" : displayName(from);

        String text = "👋 欢迎来到 <b>KK Poker</b>！\n\n"
                + "KKPoker 融合 Web3 技术，打造集真人对局与实时结算于一体的德州扑克竞技平台。\n\n"
                + "🎰 纯随机洗牌，实时智能结算\n"
                + "📱 无需下载，一秒登录，不限机型\n"
                + "🛡 实时音视频互动，全真人绿色生态\n\n"
                + "玩德州选择 KKPoker，让每一次竞技，都回归公平与热爱。\n\n"
                + "👥 <b>欢迎加入 KKPoker！</b>\n"
                + "📛 昵称：" + escape(nick) + "\n"
                + "🆔 ID：" + uid + "\n"
                + "💰 资产：" + usdt(uid) + " USDT";

        List<InlineKeyboardButton> line = new ArrayList<>();
        if (isHttps(miniAppUrl)) {
            line.add(InlineKeyboardButton.builder()
                    .text("🎮 开始游戏")
                    .webApp(new WebAppInfo(miniAppUrl))
                    .build());
        }
        if (isHttps(groupUrl)) {
            line.add(InlineKeyboardButton.builder()
                    .text("👀 观战群组").url(groupUrl).build());
        }
        if (isHttps(channelUrl)) {
            line.add(InlineKeyboardButton.builder()
                    .text("📢 官方频道").url(channelUrl).build());
        }
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (!line.isEmpty()) {
            rows.add(new InlineKeyboardRow(line));
        }
        InlineKeyboardMarkup markup = rows.isEmpty() ? null : new InlineKeyboardMarkup(rows);

        if (isHttps(welcomeImageUrl)) {
            try {
                client.execute(SendPhoto.builder()
                        .chatId(chatId)
                        .photo(new InputFile(welcomeImageUrl))
                        .caption(text)
                        .parseMode("HTML")
                        .replyMarkup(markup)
                        .build());
                return;
            } catch (Exception e) {
                log.warn("发送欢迎图失败，降级为纯文本: {}", e.getMessage());
            }
        }

        try {
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .parseMode("HTML")
                    .replyMarkup(markup)
                    .build());
        } catch (Exception e) {
            log.warn("发送欢迎消息失败: {}", e.getMessage());
        }
    }

    /** first_name + last_name，去掉首尾空格。 */
    private String displayName(User u) {
        String first = u.getFirstName() == null ? "" : u.getFirstName();
        String last = u.getLastName() == null ? "" : u.getLastName();
        return (first + " " + last).trim();
    }

    /** micro-USDT → 展示用 USDT（两位小数）。查库失败不影响发消息。 */
    private String usdt(long userId) {
        long micro = 0L;
        try {
            if (userId > 0) {
                micro = walletService.balance(userId);
            }
        } catch (Exception e) {
            log.debug("查余额失败 user={}: {}", userId, e.getMessage());
        }
        return String.format(java.util.Locale.ROOT, "%.2f", micro / 1_000_000.0);
    }

    private void sendText(long chatId, String text) {
        try {
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .parseMode("HTML")
                    .build());
        } catch (Exception e) {
            log.warn("发送消息失败: {}", e.getMessage());
        }
    }

    private boolean isHttps(String url) {
        return url != null && url.startsWith("https://");
    }

    /** 与 ActivityService.inviteCode 保持一致：KK + base36(userId)。 */
    private String inviteCode(long userId) {
        return "KK" + Long.toString(userId, 36).toUpperCase();
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
