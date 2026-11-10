package com.tgpoker.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Telegram Mini App initData 验签。
 *
 * <p><b>两条校验路径</b></p>
 * <ol>
 *   <li><b>hash</b>（HMAC-SHA256，给持有 bot token 的后端用）：
 *       {@code secret_key = HMAC_SHA256(key="WebAppData", msg=bot_token)}，
 *       再对 data_check_string 做 HMAC 并转 hex 比对。</li>
 *   <li><b>signature</b>（Ed25519，部分客户端只给这个，例如 Telegram Web）：
 *       data_check_string 前加 {@code "{bot_id}:WebAppData\n"}，用 Telegram 公布的
 *       公钥验签，签名是 base64url 编码。</li>
 * </ol>
 *
 * <p>两条路径的 data_check_string 都是：除 {@code hash} / {@code signature} 外的键值对，
 * 按 key 升序，{@code k=v} 用 {@code "\n"} 连接。</p>
 *
 * <p>官方文档写成 {@code HMAC_SHA256(<bot_token>, "WebAppData")}，极易被读成
 * 「key 是 bot_token、消息是 WebAppData」——那是反的。以官方 Python 示例
 * {@code hmac.new(b"WebAppData", bot_token.encode(), sha256)} 为准：
 * <b>"WebAppData" 是 key，bot_token 是消息</b>。写反的话本地怎么测都不报错，
 * 一上线所有真实用户登录都会 401。</p>
 */
public final class TelegramInitData {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WEB_APP_DATA = "WebAppData";
    /** 默认允许的时钟漂移：1 小时。超出即视为重放。 */
    public static final long DEFAULT_MAX_AGE_SECONDS = 3600L;
    /**
     * Telegram 生产环境的 Ed25519 公钥（官方文档公布的固定常量，没有 API 可取）。
     * 测试环境另有一把：40055058a4ee38156a06562e52eece92a771bcd8346a8c4615cb7376eddf72ec
     */
    private static final String TG_PUBLIC_KEY_HEX =
            "e7bf03a2fa4602af4580703d88dda5bb59f32ed8b02a56c187fe7d34caed242d";

    private TelegramInitData() {
    }

    /** 验签失败的具体原因，用于日志与前端提示。 */
    public enum FailReason {
        OK("通过"),
        EMPTY("initData 为空"),
        NO_HASH("initData 既没有 hash 也没有 signature"),
        AUTH_DATE_MISSING("initData 不含 auth_date"),
        AUTH_DATE_EXPIRED("initData 已过期（auth_date 与本机时间相差超过允许值）"),
        HASH_MISMATCH("hash/signature 校验不通过（bot-token 与打开 Mini App 的 Bot 不一致，或校验串被改动）");

        private final String text;

        FailReason(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    /** 只验签名，不校验时效（单元测试用）。 */
    public static boolean verify(String initData, String botToken) {
        return verify(initData, botToken, -1);
    }

    /**
     * 验签 + 时效校验。
     *
     * @param maxAgeSeconds 允许的最大 auth_date 偏差，单位秒；<=0 表示不校验时效
     */
    public static boolean verify(String initData, String botToken, long maxAgeSeconds) {
        return check(initData, botToken, maxAgeSeconds) == FailReason.OK;
    }

    /** 验签 + 时效校验，返回具体失败原因，便于日志定位。 */
    public static FailReason check(String initData, String botToken, long maxAgeSeconds) {
        if (initData == null || initData.isEmpty()) {
            return FailReason.EMPTY;
        }
        if (botToken == null || botToken.isEmpty()) {
            return FailReason.HASH_MISMATCH;
        }
        Map<String, String> map = parse(initData);
        String hash = map.get("hash");
        String signature = map.get("signature");
        boolean hasHash = hash != null && !hash.isEmpty();
        boolean hasSignature = signature != null && !signature.isEmpty();
        if (!hasHash && !hasSignature) {
            return FailReason.NO_HASH;
        }
        if (maxAgeSeconds > 0) {
            long ts = parseLong(map.get("auth_date"));
            if (ts <= 0) {
                return FailReason.AUTH_DATE_MISSING;
            }
            long diff = Math.abs(System.currentTimeMillis() / 1000 - ts);
            if (diff > maxAgeSeconds) {
                return FailReason.AUTH_DATE_EXPIRED;
            }
        }

        // hash 的 data_check_string：官方示例只排除 hash 本身（signature 要参与），
        // 但存在把 signature 一起排除的实现，两种都试一遍，兼容不同客户端。
        String dcsWithSignature = joinSorted(minus(map, "hash"));
        String dcsWithoutSignature = joinSorted(minus(map, "hash", "signature"));

        // 路径一：hash（HMAC-SHA256，secret_key = HMAC("WebAppData", bot_token)）
        if (hasHash) {
            byte[] secret = hmac(WEB_APP_DATA.getBytes(StandardCharsets.UTF_8),
                    botToken.getBytes(StandardCharsets.UTF_8));
            if (hashMatches(secret, dcsWithSignature, hash)
                    || hashMatches(secret, dcsWithoutSignature, hash)) {
                return FailReason.OK;
            }
            // hash 存在但不匹配时不再降级走 signature，直接判失败（避免掩盖 token 配错）
            return FailReason.HASH_MISMATCH;
        }

        // 路径二：signature（Ed25519，Telegram Web 等客户端只给这个）
        // data_check_string 前面要加 "bot_id:WebAppData\n"，且必须排除 signature 自身
        long botId = botIdOf(botToken);
        if (botId <= 0) {
            return FailReason.HASH_MISMATCH;
        }
        String signed = botId + ":" + WEB_APP_DATA + "\n" + dcsWithoutSignature;
        return verifyEd25519(signed, signature) ? FailReason.OK : FailReason.HASH_MISMATCH;
    }

    private static boolean hashMatches(byte[] secret, String dataCheckString, String hash) {
        String computed = hex(hmac(secret, dataCheckString.getBytes(StandardCharsets.UTF_8)));
        return constantTimeEquals(computed, hash);
    }

    private static Map<String, String> minus(Map<String, String> src, String... keys) {
        Map<String, String> out = new HashMap<>(src);
        for (String k : keys) {
            out.remove(k);
        }
        return out;
    }

    private static String joinSorted(Map<String, String> data) {
        List<String> keys = new ArrayList<>(data.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(k).append('=').append(data.get(k));
        }
        return sb.toString();
    }

    /** bot token 形如 "机器人ID:密钥"，冒号前那串数字就是 bot_id。 */
    private static long botIdOf(String botToken) {
        int i = botToken == null ? -1 : botToken.indexOf(':');
        return i <= 0 ? 0 : parseLong(botToken.substring(0, i));
    }

    /** 用 Telegram 公布的 Ed25519 公钥验签（生产环境公钥，测试环境另有一把）。 */
    private static boolean verifyEd25519(String dataCheckString, String signatureBase64Url) {
        try {
            byte[] raw = hexToBytes(TG_PUBLIC_KEY_HEX);
            // Ed25519 的 X.509 SubjectPublicKeyInfo 前缀固定，拼上 32 字节公钥即可
            byte[] prefix = hexToBytes("302a300506032b6570032100");
            byte[] spki = new byte[prefix.length + raw.length];
            System.arraycopy(prefix, 0, spki, 0, prefix.length);
            System.arraycopy(raw, 0, spki, prefix.length, raw.length);

            PublicKey key = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(spki));
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(key);
            sig.update(dataCheckString.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getUrlDecoder().decode(signatureBase64Url));
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int n = hex.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    /**
     * 失败时的定位信息：只输出字段名、时间差和 hash 前 8 位，
     * 不落完整 initData，避免把用户资料写进日志。
     */
    public static String diagnose(String initData, String botToken, long maxAgeSeconds) {
        if (initData == null || initData.isEmpty()) {
            return "initData 为空";
        }
        Map<String, String> map = parse(initData);
        long ts = parseLong(map.get("auth_date"));
        long diff = ts <= 0 ? -1 : System.currentTimeMillis() / 1000 - ts;
        String hash = map.get("hash");
        String signature = map.get("signature");
        StringBuilder sb = new StringBuilder();
        sb.append("字段=").append(map.keySet())
                .append(", auth_date=").append(ts)
                .append(", 与本机相差=").append(diff).append("秒(允许").append(maxAgeSeconds).append(")");
        if (hash == null || hash.isEmpty()) {
            sb.append(", hash=无");
        } else {
            sb.append(", 收到 hash=").append(hash.substring(0, Math.min(8, hash.length())));
            if (botToken != null && !botToken.isEmpty()) {
                byte[] secret = hmac(WEB_APP_DATA.getBytes(StandardCharsets.UTF_8),
                        botToken.getBytes(StandardCharsets.UTF_8));
                sb.append(", 本机算(含sig)=").append(hex(hmac(secret,
                                joinSorted(minus(map, "hash")).getBytes(StandardCharsets.UTF_8)))
                                .substring(0, 8))
                        .append(", 本机算(不含sig)=").append(hex(hmac(secret,
                                joinSorted(minus(map, "hash", "signature"))
                                        .getBytes(StandardCharsets.UTF_8))).substring(0, 8));
            }
        }
        sb.append(", signature=").append(signature == null || signature.isEmpty()
                ? "无" : signature.substring(0, Math.min(8, signature.length())) + "...");
        return sb.toString();
    }

    /** 取 auth_date，用于判断是否过期（建议 1 小时内）。 */
    public static long authDate(String initData) {
        return parseLong(parse(initData).get("auth_date"));
    }

    /** 解析 user 字段。 */
    public static JsonNode user(String initData) {
        String v = parse(initData).get("user");
        if (v == null) {
            return null;
        }
        try {
            return MAPPER.readTree(v);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 start_param（深链参数）。 */
    public static String startParam(String initData) {
        return parse(initData).getOrDefault("start_param", "");
    }

    public static Map<String, String> parse(String initData) {
        Map<String, String> map = new HashMap<>();
        if (initData == null) {
            return map;
        }
        for (String pair : initData.split("&")) {
            int i = pair.indexOf('=');
            if (i <= 0) {
                continue;
            }
            String k = pair.substring(0, i);
            String v = pair.substring(i + 1);
            try {
                v = URLDecoder.decode(v, StandardCharsets.UTF_8.name());
            } catch (Exception ignore) {
                // 保持原值
            }
            map.put(k, v);
        }
        return map;
    }

    private static long parseLong(String v) {
        try {
            return v == null ? 0 : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static byte[] hmac(byte[] key, byte[] msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(msg);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
