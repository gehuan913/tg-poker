package com.tgpoker.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tgpoker.mapper.AppUserMapper;
import com.tgpoker.mapper.RedPacketMapper;
import com.tgpoker.model.AppUser;
import com.tgpoker.model.RedPacket;
import com.tgpoker.util.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 红包。
 *
 * <p>红包本质是用户间转账，走同一套账本（TRANSFER_IN / TRANSFER_OUT）。
 * <b>严禁给官方账号发红包</b>——那等价于平台内上下分，绕开链上 KYT，
 * 竞品自己踩过这个坑（文档 §2.5）。这里显式拦掉 0 号账号与"财务"类账号。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedPacketService {

    private static final long MIN_AMOUNT = 100_000L;      // 0.1 USDT
    private static final long MAX_AMOUNT = 100_000_000L;  // 100 USDT
    /** 单日单人收红包上限，防拆单洗钱。 */
    private static final long DAILY_RECEIVE_LIMIT = 1_000_000_000L;

    private final RedPacketMapper redPacketMapper;
    private final WalletService walletService;
    private final AppUserMapper userMapper;

    /**
     * 发红包（用户间转账）。
     *
     * <p>校验：不能发给自己、不能发给官方账号、单笔 0.1~100U、对方当日收款有上限。
     * 金额完全走 {@link WalletService#transfer}，这里不再单独维护余额。</p>
     *
     * @param scene   CHAT / TABLE
     * @param tableId 桌内红包所属牌桌，可为 null
     */
    @Transactional
    public Map<String, Object> send(long senderId, long receiverId, long amount,
                                    String memo, String scene, String tableId) {
        if (receiverId <= 0) {
            throw new IllegalArgumentException("请选择收款人");
        }
        if (receiverId == senderId) {
            throw new IllegalArgumentException("不能给自己发红包");
        }
        AppUser receiver = userMapper.selectById(receiverId);
        if (receiver == null) {
            throw new IllegalArgumentException("收款人不存在");
        }
        if ("OFFICIAL".equalsIgnoreCase(receiver.getUsername())
                || receiver.getNick() != null && receiver.getNick().contains("财务")) {
            throw new IllegalStateException("不允许给官方账号转账");
        }
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("单个红包金额需在 " + Money.fmt(MIN_AMOUNT)
                    + " ~ " + Money.fmt(MAX_AMOUNT) + " USDT 之间");
        }

        long receivedToday = receivedToday(receiverId);
        if (receivedToday + amount > DAILY_RECEIVE_LIMIT) {
            throw new IllegalStateException("对方今日收红包已达上限");
        }

        String bizId = "rp-" + System.currentTimeMillis() + "-" + senderId;
        walletService.transfer(senderId, receiverId, amount, bizId,
                memo == null || memo.isBlank() ? "红包" : memo);

        RedPacket rp = new RedPacket();
        rp.setSenderId(senderId);
        rp.setReceiverId(receiverId);
        rp.setAmount(amount);
        rp.setMemo(memo == null ? "" : memo);
        rp.setScene(scene == null ? "CHAT" : scene);
        rp.setTableId(tableId);
        rp.setStatus("CLAIMED");
        rp.setCreatedAt(LocalDateTime.now());
        rp.setClaimedAt(LocalDateTime.now());
        redPacketMapper.insert(rp);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rp.getId());
        m.put("amount", amount);
        m.put("amountText", Money.fmt(amount));
        m.put("receiverNick", receiver.getNick());
        m.put("message", "红包已发送");
        return m;
    }

    /** 我收到的红包（按时间倒序，最多 50 条）。 */
    public List<Map<String, Object>> received(long userId, int limit) {
        QueryWrapper<RedPacket> q = new QueryWrapper<>();
        q.eq("receiver_id", userId).orderByDesc("id").last("LIMIT " + Math.min(limit, 50));
        List<Map<String, Object>> out = new ArrayList<>();
        for (RedPacket rp : redPacketMapper.selectList(q)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rp.getId());
            m.put("amount", rp.getAmount());
            m.put("amountText", Money.fmt(rp.getAmount()));
            m.put("memo", rp.getMemo());
            m.put("scene", rp.getScene());
            m.put("createdAt", rp.getCreatedAt());
            AppUser sender = userMapper.selectById(rp.getSenderId());
            m.put("senderNick", sender == null ? "" : sender.getNick());
            out.add(m);
        }
        return out;
    }

    private long receivedToday(long userId) {
        QueryWrapper<RedPacket> q = new QueryWrapper<>();
        q.eq("receiver_id", userId).ge("created_at", LocalDate.now().atStartOfDay());
        long sum = 0;
        for (RedPacket rp : redPacketMapper.selectList(q)) {
            sum += rp.getAmount() == null ? 0 : rp.getAmount();
        }
        return sum;
    }
}
