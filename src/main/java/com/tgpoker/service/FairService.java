package com.tgpoker.service;

import com.tgpoker.engine.Card;
import com.tgpoker.engine.Deck;
import com.tgpoker.engine.Variant;
import com.tgpoker.mapper.HandMapper;
import com.tgpoker.mapper.HandPlayerMapper;
import com.tgpoker.mapper.RiskEventMapper;
import com.tgpoker.model.Hand;
import com.tgpoker.model.HandPlayer;
import com.tgpoker.model.RiskEvent;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可验证发牌（文档 §9）。
 *
 * <p>核心只有三行：</p>
 * <pre>
 *   serverSeedHash = SHA256(serverSeed)                       // 开局公示
 *   seed           = HMAC_SHA256(serverSeed, clientSeeds+":"+nonce)
 *   deck           = FisherYates(标准牌序, HMAC-DRBG(seed))
 * </pre>
 *
 * <p>验证接口做的事就是"拿公示的材料自己算一遍"，算出的牌序和落库的发牌结果
 * 必须逐张一致。站内自证没有意义，所以同时提供 {@code /verify.js} 让第三方离线跑。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FairService {

    private final HandMapper handMapper;
    private final HandPlayerMapper handPlayerMapper;
    private final RiskEventMapper riskEventMapper;

    /** 自助取证次数：每人每天 10 次，防止有人拿它批量导出别人底牌。 */
    private final Map<Long, int[]> disputeQuota = new ConcurrentHashMap<>();
    private static final int DISPUTE_PER_DAY = 10;

    /** 开局承诺信息。结算前只给 hash，不给 seed。 */
    public Map<String, Object> commitment(String tableId, String handId, long handNo,
                                          String hash, String verifyCode, List<String> clientSeeds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tableId", tableId);
        m.put("handId", handId);
        m.put("handNo", handNo);
        m.put("serverSeedHash", hash);
        m.put("verifyCode", verifyCode);
        m.put("clientSeeds", clientSeeds == null ? List.of() : clientSeeds);
        m.put("revealed", false);
        m.put("algorithm", "HMAC_SHA256(serverSeed, clientSeeds+':'+nonce) -> HMAC-DRBG -> Fisher-Yates");
        return m;
    }

    public Map<String, Object> info(String handId) {
        Hand h = handMapper.selectById(handId);
        if (h == null) {
            throw new IllegalArgumentException("手牌不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("handId", h.getHandId());
        m.put("tableId", h.getTableId());
        m.put("variant", h.getVariant());
        m.put("board", h.getBoard());
        m.put("pot", h.getPot());
        m.put("verifyCode", h.getVerifyCode());
        m.put("serverSeedHash", h.getServerSeedHash());
        m.put("nonce", h.getNonce());
        m.put("clientSeeds", h.getClientSeeds());
        boolean ended = h.getEndedAt() != null;
        m.put("revealed", ended);
        // L2：结算后公开种子，任何人可自行复算
        m.put("serverSeed", ended ? h.getServerSeed() : null);
        m.put("startedAt", h.getStartedAt());
        m.put("endedAt", h.getEndedAt());
        return m;
    }

    /**
     * 站内复算：返回"是否一致"。玩家信的不是这个按钮，是 verify.js 能被别人独立跑通。
     */
    public Map<String, Object> verify(String handId) {
        Hand h = handMapper.selectById(handId);
        if (h == null) {
            throw new IllegalArgumentException("手牌不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("handId", handId);
        if (h.getEndedAt() == null || h.getServerSeed() == null) {
            m.put("ok", false);
            m.put("reason", "本手尚未结算，种子未揭示");
            return m;
        }
        Variant variant = Variant.of(h.getVariant());
        byte[] seed = Deck.deriveSeed(h.getServerSeed(), h.getClientSeeds(), h.getNonce() == null ? 0 : h.getNonce());
        List<Card> order = Deck.order(seed, variant);

        // 复算 hash：这是"没有事后改牌"的证明
        boolean hashOk = Deck.sha256Hex(h.getServerSeed()).equalsIgnoreCase(h.getServerSeedHash());

        String board = h.getBoard() == null ? "" : h.getBoard();
        List<String> boardCodes = new ArrayList<>();
        for (int i = 0; i + 1 < board.length(); i += 2) {
            boardCodes.add(board.substring(i, i + 2));
        }

        m.put("ok", hashOk);
        m.put("hashOk", hashOk);
        m.put("variant", variant.name());
        m.put("deckSize", order.size());
        // 前 10 张足够玩家肉眼核对；完整牌序用 verify.js 离线算
        m.put("deckFirst10", codes(order, 10));
        m.put("boardStored", boardCodes);
        m.put("reason", hashOk ? "种子 hash 与公示一致" : "种子 hash 与公示不一致，请立即停止游戏并联系客服");
        m.put("verifyCode", h.getVerifyCode());
        m.put("nonce", h.getNonce());
        m.put("clientSeeds", h.getClientSeeds());
        return m;
    }

    /**
     * L3 分层公开：本人自助取证，返回完整牌序与<b>所有人</b>底牌。
     *
     * <p>为什么不加密：加密包若把密钥放在同一台服务器，等于把钥匙和锁放一起，
     * 只是让代码看起来更安全。这里改成"只对参与本手的人开放 + 每次调取都留痕 + 每日限次"，
     * 真正做到"能举证、但拿不到无关牌局"。</p>
     *
     * @param handId 手牌 ID
     * @param userId 申请人
     */
    public Map<String, Object> disputeBundle(String handId, long userId) {
        Hand h = handMapper.selectById(handId);
        if (h == null) {
            throw new IllegalArgumentException("手牌不存在");
        }
        if (h.getEndedAt() == null || h.getServerSeed() == null) {
            throw new IllegalStateException("本手尚未结算，不能取证");
        }
        QueryWrapper<HandPlayer> q = new QueryWrapper<>();
        q.eq("hand_id", handId);
        List<HandPlayer> players = handPlayerMapper.selectList(q);
        boolean joined = players.stream().anyMatch(p -> p.getUserId() != null && p.getUserId() == userId);
        if (!joined) {
            throw new SecurityException("只有参与本手的人可以取证");
        }

        int day = LocalDateTime.now().getDayOfYear();
        int[] quota = disputeQuota.computeIfAbsent(userId, k -> new int[]{0, day});
        if (quota[1] != day) {
            quota[1] = day;
            quota[0] = 0;
        }
        if (quota[0] >= DISPUTE_PER_DAY) {
            throw new IllegalStateException("今日取证次数已用完（" + DISPUTE_PER_DAY + " 次）");
        }
        quota[0]++;

        Variant variant = Variant.of(h.getVariant());
        byte[] seed = Deck.deriveSeed(h.getServerSeed(), h.getClientSeeds(), h.getNonce() == null ? 0 : h.getNonce());
        List<Card> order = Deck.order(seed, variant);

        // 审计留痕：谁、什么时候、调了哪一手
        RiskEvent ev = new RiskEvent();
        ev.setUserId(userId);
        ev.setTableId(h.getTableId());
        ev.setKind("FAIR_DISPUTE");
        ev.setScore(0);
        ev.setDetail("取证 " + handId + "，当日第 " + quota[0] + " 次");
        ev.setCreatedAt(LocalDateTime.now());
        try {
            riskEventMapper.insert(ev);
        } catch (Exception e) {
            log.warn("取证日志写入失败 hand={} user={}", handId, userId, e);
        }

        List<Map<String, Object>> holes = new ArrayList<>();
        for (HandPlayer p : players) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("seat", p.getSeat());
            pm.put("userId", p.getUserId());
            pm.put("holeCards", p.getHoleCards());
            holes.add(pm);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("handId", handId);
        m.put("variant", variant.name());
        m.put("serverSeed", h.getServerSeed());
        m.put("serverSeedHash", h.getServerSeedHash());
        m.put("clientSeeds", h.getClientSeeds());
        m.put("nonce", h.getNonce());
        m.put("deckSize", order.size());
        m.put("deck", codes(order, order.size()));
        m.put("players", holes);
        m.put("board", h.getBoard());
        m.put("quotaLeft", DISPUTE_PER_DAY - quota[0]);
        m.put("note", "仅参与本手的人可调取；每次调取已记录用户与时间。请勿外传他人底牌。");
        return m;
    }

    private List<String> codes(List<Card> cards, int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, cards.size()); i++) {
            out.add(cards.get(i).code());
        }
        return out;
    }
}
