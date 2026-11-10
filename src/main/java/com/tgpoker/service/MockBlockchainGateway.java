package com.tgpoker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 开发期的区块链网关桩实现。
 *
 * <p>真实环境把 {@code blockchain.mock=false} 打开并换成公司平台实现即可。
 * 桩实现生成的地址只是"看起来像 T 开头"的确定性字符串，<b>不能用于真实充值</b>，
 * 所以接口层用 {@code ready=false} 明确告诉前端当前是模拟模式。</p>
 */
@Slf4j
@Component
public class MockBlockchainGateway implements BlockchainGateway {

    @Value("${app.blockchain.callback-secret:dev-callback-secret}")
    private String callbackSecret;

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public String allocateAddress(long userId) {
        // 确定性伪地址：真实环境由平台分配并返回
        String raw = "TRON" + userId;
        StringBuilder sb = new StringBuilder("T");
        for (byte b : sha256(raw).getBytes(StandardCharsets.UTF_8)) {
            sb.append("123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
                    .charAt(Math.floorMod(b, 58)));
            if (sb.length() >= 34) {
                break;
            }
        }
        return sb.toString();
    }

    @Override
    public String createWithdrawal(String orderId, String address, long amountMicro) {
        // 真实环境：调平台 /v1/withdraw，返回平台受理号；txHash 异步回调补
        return "MOCK-" + orderId;
    }

    @Override
    public boolean verifyCallback(String signature, String timestamp, String body) {
        if (signature == null || timestamp == null) {
            return false;
        }
        try {
            long ts = Long.parseLong(timestamp);
            if (Math.abs(System.currentTimeMillis() / 1000 - ts) > 300) {
                log.warn("回调时间戳偏差过大 ts={}", ts);
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        String expected = hmac(timestamp + "." + body);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(callbackSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append((char) ('a' + Math.floorMod(b, 26)));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
