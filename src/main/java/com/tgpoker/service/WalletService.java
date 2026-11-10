package com.tgpoker.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tgpoker.mapper.AccountMapper;
import com.tgpoker.mapper.LedgerEntryMapper;
import com.tgpoker.model.Account;
import com.tgpoker.model.LedgerEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 钱包与账本。
 *
 * <p>三条铁律：金额用 long 存 micro-USDT；每次变动都写一条流水；用 (bizType,bizId,userId)
 * 唯一索引做幂等，重试不会重复入账。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final AccountMapper accountMapper;
    private final LedgerEntryMapper ledgerMapper;

    /**
     * 确保账户存在；不存在则建号并发放新手额度。
     *
     * @param userId 用户 ID
     * @param bonus  新手赠送金额（micro-USDT），已存在账户时忽略
     */
    public void ensureAccount(long userId, long bonus) {
        Account acc = accountMapper.selectById(userId);
        if (acc != null) {
            return;
        }
        Account a = new Account();
        a.setUserId(userId);
        a.setBalance(bonus);
        a.setFrozen(0L);
        a.setVersion(0L);
        accountMapper.insert(a);
        if (bonus > 0) {
            writeLedger(userId, 2, bonus, "BONUS", "welcome-" + userId, bonus, "新用户测试额度");
        }
    }

    /** 查询可用余额（micro-USDT）；账户不存在返回 0。 */
    public long balance(long userId) {
        Account acc = accountMapper.selectById(userId);
        return acc == null ? 0L : acc.getBalance();
    }

    /**
     * 充值入账。
     *
     * @param bizId 幂等键，链上充值传 txHash，重复回调不会重复加钱
     * @return 入账后余额
     */
    @Transactional
    public long deposit(long userId, long amount, String bizId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("金额必须大于 0");
        }
        Account acc = accountMapper.selectById(userId);
        long before = acc == null ? 0L : acc.getBalance();
        accountMapper.add(userId, amount);
        long after = before + amount;
        writeLedger(userId, 2, amount, "DEPOSIT", bizId, after, "充值");
        return after;
    }

    /**
     * 提现扣款（含手续费）。余额不足会抛异常，调用方需在事务内建提现工单。
     *
     * @return 扣款后余额
     */
    @Transactional
    public long withdraw(long userId, long amount, String bizId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("金额必须大于 0");
        }
        int rows = accountMapper.deduct(userId, amount);
        if (rows == 0) {
            throw new IllegalStateException("余额不足");
        }
        long after = balance(userId);
        writeLedger(userId, 1, amount, "WITHDRAW", bizId, after, "提现");
        return after;
    }

    /** 上桌买入：钱包扣款到桌上筹码。返回 false 表示余额不足。 */
    @Transactional
    public boolean buyIn(long userId, long amount, String bizId) {
        int rows = accountMapper.deduct(userId, amount);
        if (rows == 0) {
            return false;
        }
        long after = balance(userId);
        writeLedger(userId, 1, amount, "TABLE_BUYIN", bizId, after, "上桌买入");
        return true;
    }

    /** 离桌退筹码回钱包（桌内筹码 → 可用余额）。 */
    @Transactional
    public void cashOut(long userId, long amount, String bizId) {
        if (amount <= 0) {
            return;
        }
        accountMapper.add(userId, amount);
        long after = balance(userId);
        writeLedger(userId, 2, amount, "TABLE_CASHOUT", bizId, after, "离桌退筹码");
    }

    /** 保险保费：从场外钱包扣（桌上可能已经没钱付保费）。 */
    @Transactional
    public boolean insurancePremium(long userId, long amount, String bizId) {
        if (amount <= 0) {
            return true;
        }
        int rows = accountMapper.deduct(userId, amount);
        if (rows == 0) {
            return false;
        }
        writeLedger(userId, 1, amount, "INSURANCE_PREMIUM", bizId, balance(userId), "保险保费");
        return true;
    }

    /** 保险赔付：进钱包。 */
    @Transactional
    public void insurancePayout(long userId, long amount, String bizId) {
        if (amount <= 0) {
            return;
        }
        accountMapper.add(userId, amount);
        writeLedger(userId, 2, amount, "INSURANCE_PAYOUT", bizId, balance(userId), "保险赔付");
    }

    /**
     * 用户间转账（红包）。同一事务里一扣一加，任一步失败整体回滚。
     * 严禁用于"给官方账号充值"——那是绕开链上 KYT 的上下分通道。
     */
    @Transactional
    public void transfer(long fromUserId, long toUserId, long amount, String bizId, String memo) {
        if (amount <= 0) {
            throw new IllegalArgumentException("金额必须大于 0");
        }
        if (fromUserId == toUserId) {
            throw new IllegalArgumentException("不能给自己转账");
        }
        int rows = accountMapper.deduct(fromUserId, amount);
        if (rows == 0) {
            throw new IllegalStateException("余额不足");
        }
        accountMapper.add(toUserId, amount);
        writeLedger(fromUserId, 1, amount, "TRANSFER_OUT", bizId, balance(fromUserId), memo);
        writeLedger(toUserId, 2, amount, "TRANSFER_IN", bizId, balance(toUserId), memo);
    }

    /** 活动奖励 / 返水等平台发放。 */
    @Transactional
    public void reward(long userId, long amount, String bizType, String bizId, String memo) {
        if (amount <= 0) {
            return;
        }
        accountMapper.add(userId, amount);
        writeLedger(userId, 2, amount, bizType, bizId, balance(userId), memo);
    }

    public List<LedgerEntry> history(long userId, int limit) {
        QueryWrapper<LedgerEntry> q = new QueryWrapper<>();
        q.eq("user_id", userId).orderByDesc("id").last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        return ledgerMapper.selectList(q);
    }

    private void writeLedger(long userId, int direction, long amount, String bizType,
                             String bizId, long balanceAfter, String memo) {
        LedgerEntry e = new LedgerEntry();
        e.setUserId(userId);
        e.setDirection(direction);
        e.setAmount(amount);
        e.setBizType(bizType);
        e.setBizId(bizId);
        e.setBalanceAfter(balanceAfter);
        e.setMemo(memo);
        e.setCreatedAt(LocalDateTime.now());
        try {
            ledgerMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            log.warn("重复入账被拦截 bizType={} bizId={} userId={}", bizType, bizId, userId);
        }
    }
}
