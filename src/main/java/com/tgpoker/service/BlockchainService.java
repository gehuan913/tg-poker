package com.tgpoker.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tgpoker.mapper.AccountMapper;
import com.tgpoker.mapper.DepositAddressMapper;
import com.tgpoker.mapper.DepositOrderMapper;
import com.tgpoker.mapper.WithdrawOrderMapper;
import com.tgpoker.model.Account;
import com.tgpoker.model.DepositAddress;
import com.tgpoker.model.DepositOrder;
import com.tgpoker.model.WithdrawOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 充提服务。
 *
 * <p>四条硬要求（文档 §6.1）：</p>
 * <ol>
 *   <li><b>一人一址</b>：TRON 没有 memo，共享地址必然错账；</li>
 *   <li><b>回调验签防重放</b>：HMAC + 时间戳，且 tx_hash 唯一；</li>
 *   <li><b>严禁人工补单</b>：链上事件直接入账，中间没有人能插一条账；</li>
 *   <li><b>T+1 全量对账</b>：链上余额 vs 账本总余额，差额即冻结提现。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainService {

    public static final long MIN_DEPOSIT = 10_000_000L;
    public static final long MIN_WITHDRAW = 10_000_000L;
    public static final long WITHDRAW_FEE = 1_000_000L;

    private final BlockchainGateway gateway;
    private final DepositAddressMapper addressMapper;
    private final DepositOrderMapper depositMapper;
    private final WithdrawOrderMapper withdrawMapper;
    private final AccountMapper accountMapper;
    private final WalletService walletService;

    @Value("${app.blockchain.ready:false}")
    private boolean ready;

    public boolean ready() {
        return ready;
    }

    /** 每个用户固定一个充值地址，重复调用返回同一个。 */
    @Transactional
    public String ensureAddress(long userId) {
        QueryWrapper<DepositAddress> q = new QueryWrapper<>();
        q.eq("user_id", userId).last("LIMIT 1");
        DepositAddress existing = addressMapper.selectOne(q);
        if (existing != null) {
            return existing.getAddress();
        }
        String addr = gateway.allocateAddress(userId);
        DepositAddress da = new DepositAddress();
        da.setAddress(addr);
        da.setUserId(userId);
        da.setChain("TRON");
        da.setAsset("USDT");
        da.setAllocatedAt(LocalDateTime.now());
        try {
            addressMapper.insert(da);
        } catch (DuplicateKeyException e) {
            // 并发下另一个线程刚插入，读回来即可
            DepositAddress again = addressMapper.selectById(addr);
            return again == null ? addr : again.getAddress();
        }
        return addr;
    }

    /**
     * 链上入账。tx_hash 唯一索引保证同一笔交易永远只入账一次——
     * 这是"没人能手动补单"的技术基础。
     */
    @Transactional
    public boolean creditDeposit(long userId, String address, String txHash, long amount, int confirmations) {
        if (amount <= 0 || txHash == null || txHash.isBlank()) {
            return false;
        }
        QueryWrapper<DepositOrder> q = new QueryWrapper<>();
        q.eq("tx_hash", txHash).last("LIMIT 1");
        if (depositMapper.selectOne(q) != null) {
            log.info("重复充值回调已忽略 tx={}", txHash);
            return false;
        }
        DepositOrder o = new DepositOrder();
        o.setUserId(userId);
        o.setAddress(address);
        o.setTxHash(txHash);
        o.setAmount(amount);
        o.setConfirmations(confirmations);
        o.setStatus("CREDITED");
        o.setCreditedAt(LocalDateTime.now());
        o.setCreatedAt(LocalDateTime.now());
        try {
            depositMapper.insert(o);
        } catch (DuplicateKeyException e) {
            return false;
        }
        walletService.deposit(userId, amount, "dep-" + txHash);
        log.info("充值入账 user={} amount={} tx={}", userId, amount, txHash);
        return true;
    }

    /** 提现申请：先扣款（含手续费）再建工单，避免"审核通过却扣不到钱"。 */
    @Transactional
    public Map<String, Object> requestWithdraw(long userId, String address, long amount) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("请填写 TRON 收款地址");
        }
        if (amount < MIN_WITHDRAW) {
            throw new IllegalArgumentException("最小提现 " + com.tgpoker.util.Money.fmt(MIN_WITHDRAW) + " USDT");
        }
        long total = amount + WITHDRAW_FEE;
        long after = walletService.withdraw(userId, total, "wd-" + UUID.randomUUID());

        WithdrawOrder o = new WithdrawOrder();
        o.setUserId(userId);
        o.setAddress(address.trim());
        o.setAmount(amount);
        o.setFee(WITHDRAW_FEE);
        o.setStatus("PENDING_REVIEW");
        o.setMemo("等待风控审核");
        o.setCreatedAt(LocalDateTime.now());
        withdrawMapper.insert(o);

        if (ready) {
            try {
                String tx = gateway.createWithdrawal(String.valueOf(o.getId()), o.getAddress(), amount);
                o.setTxHash(tx);
                o.setStatus("PROCESSING");
                o.setMemo("已提交区块链平台");
                withdrawMapper.updateById(o);
            } catch (Exception e) {
                log.error("提现提交失败 order={}", o.getId(), e);
                o.setMemo("提交失败：" + e.getMessage());
                withdrawMapper.updateById(o);
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.getId());
        m.put("balance", after);
        m.put("balanceText", com.tgpoker.util.Money.fmt(after));
        m.put("fee", WITHDRAW_FEE);
        m.put("feeText", com.tgpoker.util.Money.fmt(WITHDRAW_FEE));
        m.put("status", o.getStatus());
        m.put("ready", ready);
        m.put("note", ready ? "已提交区块链平台" : "当前为模拟模式，仅记账，不发起链上交易");
        return m;
    }

    public List<WithdrawOrder> withdrawOrders(long userId) {
        QueryWrapper<WithdrawOrder> q = new QueryWrapper<>();
        q.eq("user_id", userId).orderByDesc("id").last("LIMIT 30");
        return withdrawMapper.selectList(q);
    }

    public List<DepositOrder> depositOrders(long userId) {
        QueryWrapper<DepositOrder> q = new QueryWrapper<>();
        q.eq("user_id", userId).orderByDesc("id").last("LIMIT 30");
        return depositMapper.selectList(q);
    }

    /** 开发用：模拟一笔链上充值。 */
    public boolean devFaucet(long userId, long amount) {
        String addr = ensureAddress(userId);
        String tx = "MOCK-" + UUID.randomUUID().toString().replace("-", "");
        return creditDeposit(userId, addr, tx, amount, 19);
    }

    /**
     * 对账：账本总余额 vs 平台余额。
     * 差额超过阈值就应冻结提现，而不是"先放着看看"。
     */
    public Map<String, Object> reconcile(long platformBalance) {
        long ledgerTotal = 0;
        for (Account a : accountMapper.selectList(null)) {
            ledgerTotal += a.getBalance() == null ? 0 : a.getBalance();
        }
        long diff = platformBalance - ledgerTotal;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ledgerTotal", ledgerTotal);
        m.put("ledgerTotalText", com.tgpoker.util.Money.fmt(ledgerTotal));
        m.put("platformBalance", platformBalance);
        m.put("platformBalanceText", com.tgpoker.util.Money.fmt(platformBalance));
        m.put("diff", diff);
        m.put("diffText", com.tgpoker.util.Money.fmtSigned(diff));
        m.put("healthy", Math.abs(diff) < 1_000_000L);
        return m;
    }
}
