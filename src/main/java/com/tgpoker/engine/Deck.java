package com.tgpoker.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 牌堆 + 可验证洗牌。
 *
 * <p>洗牌算法：HMAC-SHA256 计数器模式做 DRBG，再用 Fisher-Yates 洗牌。给定同一组
 * {@code serverSeed / clientSeed / nonce / variant}，任何人可以复算出完全相同的牌序，
 * 这就是「可验证公平」的基础（文档 §9）。</p>
 *
 * <p>短牌只剩 36 张（6 以上），所以牌堆必须按 variant 构造，验证脚本也要支持两套。</p>
 */
public class Deck {

    private final List<Card> cards;
    private int idx = 0;

    public Deck(byte[] seed) {
        this(seed, Variant.LONG);
    }

    public Deck(byte[] seed, Variant variant) {
        cards = new ArrayList<>(variant.deckSize());
        for (int s = 0; s < 4; s++) {
            for (int r = variant.minRank(); r <= 14; r++) {
                cards.add(new Card(r, s));
            }
        }
        shuffle(new Drbg(seed));
    }

    /** 只用于测试与验证脚本：给定种子与变体，返回完整牌序。 */
    public static List<Card> order(byte[] seed, Variant variant) {
        Deck d = new Deck(seed, variant);
        return new ArrayList<>(d.cards);
    }

    private void shuffle(Drbg drbg) {
        for (int i = cards.size() - 1; i > 0; i--) {
            int j = drbg.nextInt(i + 1);
            Collections.swap(cards, i, j);
        }
    }

    public Card next() {
        return cards.get(idx++);
    }

    public int remaining() {
        return cards.size() - idx;
    }

    public int size() {
        return cards.size();
    }

    /** 剩余牌堆快照，用于存证与复算对比。 */
    public List<Card> remainingCards() {
        return new ArrayList<>(cards.subList(idx, cards.size()));
    }

    public static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hmacHex(String key, String msg) {
        byte[] mac = hmacSha256(key.getBytes(StandardCharsets.UTF_8), msg.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : mac) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] hmacSha256(byte[] key, byte[] msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(msg);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 由种子派生洗牌种子：HMAC_SHA256(serverSeed, clientSeed + ":" + nonce)，
     * 与文档 §9.3 一致，便于第三方用脚本独立复算。
     *
     * <p>clientSeed 是"所有玩家种子按座位顺序用 , 连接"的结果——只要有一个玩家提交的是
     * 真随机数，运营方就无法提前枚举出有利牌序（防 grinding attack）。</p>
     */
    public static byte[] deriveSeed(String serverSeed, String clientSeed, long nonce) {
        String msg = (clientSeed == null ? "" : clientSeed) + ":" + nonce;
        return hmacSha256(serverSeed.getBytes(StandardCharsets.UTF_8), msg.getBytes(StandardCharsets.UTF_8));
    }

    /** HMAC-SHA256 计数器模式 DRBG。 */
    public static class Drbg {

        private final byte[] seed;
        private long counter = 0;
        private byte[] buf = new byte[0];
        private int pos = 0;

        public Drbg(byte[] seed) {
            this.seed = seed;
        }

        private int nextByte() {
            if (pos >= buf.length) {
                buf = hmacSha256(seed, longToBytes(counter++));
                pos = 0;
            }
            return buf[pos++] & 0xFF;
        }

        /** 无偏取模（拒绝采样），避免 Fisher-Yates 出现分布偏差。 */
        public int nextInt(int bound) {
            int bits;
            int val;
            do {
                bits = ((nextByte() << 24) | (nextByte() << 16) | (nextByte() << 8) | nextByte()) >>> 1;
                val = bits % bound;
            } while (bits - val + (bound - 1) < 0);
            return val;
        }

        private static byte[] longToBytes(long v) {
            byte[] b = new byte[8];
            for (int i = 7; i >= 0; i--) {
                b[i] = (byte) (v & 0xFF);
                v >>>= 8;
            }
            return b;
        }
    }
}
