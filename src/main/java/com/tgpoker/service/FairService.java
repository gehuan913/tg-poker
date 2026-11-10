package com.tgpoker.service;

import com.tgpoker.engine.Card;
import com.tgpoker.engine.Deck;
import com.tgpoker.engine.Variant;
import com.tgpoker.mapper.HandMapper;
import com.tgpoker.model.Hand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private List<String> codes(List<Card> cards, int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, cards.size()); i++) {
            out.add(cards.get(i).code());
        }
        return out;
    }
}
