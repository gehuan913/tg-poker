package com.tgpoker.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 支付密码哈希。
 *
 * <p>用 PBKDF2-HMAC-SHA256（12 万次迭代 + 16 字节随机盐），不引入额外依赖。
 * 存库格式：{@code pbkdf2$迭代次数$盐Base64$哈希Base64}。</p>
 *
 * <p>为什么不用 MD5/SHA256 直接存：支付密码只有 6 位，离线爆破一秒几百万条，
 * 必须用慢哈希 + 盐。</p>
 */
public final class PasswordHasher {

    private static final String ALG = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /** 生成哈希串，用于写库。 */
    public static String hash(String raw) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] dk = pbkdf2(raw, salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(dk);
    }

    /** 校验，常数时间比较，避免时序侧信道。 */
    public static boolean verify(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        try {
            int iter = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(raw, salt, iter);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String raw, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance(ALG).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希失败", e);
        }
    }

    /** 密码规则：6 位数字，不接受全相同或连续数字。 */
    public static boolean isValidFormat(String pwd) {
        if (pwd == null || !pwd.matches("\\d{6}")) {
            return false;
        }
        boolean allSame = pwd.chars().distinct().count() == 1;
        boolean sequential = true;
        for (int i = 1; i < pwd.length(); i++) {
            if (pwd.charAt(i) - pwd.charAt(i - 1) != 1) {
                sequential = false;
                break;
            }
        }
        return !allSame && !sequential;
    }

    /** 仅用于日志脱敏，不参与校验。 */
    public static String mask() {
        return new String("******".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
