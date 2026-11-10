package com.tgpoker.service;

/**
 * 区块链平台适配层。
 *
 * <p>公司已有 USDT-TRC20 一条龙平台，本站<b>不自建节点、不托管私钥、不做归集</b>。
 * 这一层只定义六个能力：分配地址、查充值、发起提现、查提现、查余额、回调验签。
 * 等接口给了，换一个实现类即可，业务代码不动。</p>
 */
public interface BlockchainGateway {

    String name();

    /** 为每个用户分配独立地址（TRON 没有 memo，共享地址必然错账）。 */
    String allocateAddress(long userId);

    /** 发起提现，返回链上 txHash；平台侧异步签名广播。 */
    String createWithdrawal(String orderId, String address, long amountMicro);

    /**
     * 回调验签：HMAC-SHA256(secret, timestamp + "." + body)。
     * 时间戳偏差超过 5 分钟直接拒绝，防重放。
     */
    boolean verifyCallback(String signature, String timestamp, String body);
}
