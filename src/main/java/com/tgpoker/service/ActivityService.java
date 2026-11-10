package com.tgpoker.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tgpoker.engine.Card;
import com.tgpoker.engine.HandEvaluator;
import com.tgpoker.engine.Variant;
import com.tgpoker.mapper.ActivityClaimMapper;
import com.tgpoker.mapper.ActivityMapper;
import com.tgpoker.mapper.AppUserMapper;
import com.tgpoker.mapper.HandPlayerMapper;
import com.tgpoker.model.Activity;
import com.tgpoker.model.ActivityClaim;
import com.tgpoker.model.AppUser;
import com.tgpoker.util.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动：VIP 群 / 大牌奖励 / 推荐红包 / 幸运骰子。
 *
 * <p>四个活动全部配置化（activity 表），代码里只有"领取规则"四段分支，
 * 上线新活动只加一行配置，不改代码——这是文档 §10 的要求。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityMapper activityMapper;
    private final ActivityClaimMapper claimMapper;
    private final HandPlayerMapper handPlayerMapper;
    private final WalletService walletService;
    private final AppUserMapper userMapper;

    private static final SecureRandom RND = new SecureRandom();

    /** 活动列表；claimed 表示今日是否已领（用于按钮置灰）。 */
    public List<Map<String, Object>> list(long userId) {
        QueryWrapper<Activity> q = new QueryWrapper<>();
        q.eq("enabled", 1).orderByAsc("sort_no");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Activity a : activityMapper.selectList(q)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", a.getCode());
            m.put("name", a.getName());
            m.put("icon", a.getIcon());
            m.put("description", a.getDescription());
            m.put("link", a.getLink());
            m.put("rewardAmount", a.getRewardAmount());
            m.put("rewardText", Money.fmt(a.getRewardAmount() == null ? 0 : a.getRewardAmount()));
            m.put("claimed", claimMapper.countToday(userId, a.getCode()) > 0);
            out.add(m);
        }
        return out;
    }

    /**
     * 领取活动奖励。
     *
     * @param code  活动编码
     * @param bizId 业务幂等键：大牌奖励用手牌 ID，推荐红包用被邀请人 ID，每日活动用日期
     */
    /**
     * 领取活动奖励。
     *
     * <p>四段规则：</p>
     * <ul>
     *   <li>VIP_GROUP：只返回链接，不发钱；</li>
     *   <li>BIG_HAND：最近 20 手有四条及以上才发 2U；</li>
     *   <li>REFERRAL：被邀请人必须先打过一手牌才发 1U；</li>
     *   <li>LUCKY_DICE：每日一次随机 0.1~1U。</li>
     * </ul>
     *
     * @param code  活动编码
     * @param bizId 业务幂等键：大牌奖励用手牌 ID，推荐红包用被邀请人 ID，每日活动留空
     */
    @Transactional
    public Map<String, Object> claim(long userId, String code, String bizId) {
        Activity a = findByCode(code);
        if (a == null) {
            throw new IllegalArgumentException("活动不存在");
        }
        String key = (bizId == null || bizId.isBlank()) ? defaultBizId(code) : bizId;
        if (claimMapper.countByBiz(userId, code, key) > 0) {
            throw new IllegalStateException("该奖励已领取");
        }

        long reward = 0;
        String message;
        switch (code) {
            case "VIP_GROUP":
                message = "VIP 群链接已复制，请在浏览器打开";
                break;
            case "BIG_HAND":
                reward = bigHandReward(userId, key);
                message = reward > 0 ? "大牌奖励已发放" : "最近没有达到奖励标准的大牌";
                break;
            case "REFERRAL":
                reward = referralReward(userId, key);
                message = reward > 0 ? "推荐奖励已发放" : "该好友还没打过牌，暂时不能领取";
                break;
            case "LUCKY_DICE":
                reward = 100_000L + RND.nextInt(900_000);
                message = "掷出 " + Money.fmt(reward) + " USDT";
                break;
            default:
                reward = a.getRewardAmount() == null ? 0 : a.getRewardAmount();
                message = "奖励已发放";
        }

        ActivityClaim c = new ActivityClaim();
        c.setUserId(userId);
        c.setActivityCode(code);
        c.setAmount(reward);
        c.setBizId(key);
        c.setCreatedAt(LocalDateTime.now());
        claimMapper.insert(c);

        if (reward > 0) {
            walletService.reward(userId, reward, "ACTIVITY", code + "-" + key, a.getName());
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reward", reward);
        m.put("rewardText", Money.fmt(reward));
        m.put("message", message);
        m.put("link", a.getLink());
        return m;
    }

    private String defaultBizId(String code) {
        return "LUCKY_DICE".equals(code) ? LocalDate.now().toString() : "default";
    }

    /** 大牌奖励：最近 20 手里有"四条及以上"才发。 */
    private long bigHandReward(long userId, String handId) {
        List<Map<String, Object>> rows = handPlayerMapper.recentCards(userId, 20);
        for (Map<String, Object> raw : rows) {
            Map<String, Object> r = com.tgpoker.util.Maps.camel(raw);
            if (handId != null && !handId.isBlank() && !handId.equals(r.get("handId"))) {
                continue;
            }
            String hole = (String) r.get("holeCards");
            String board = (String) r.get("board");
            if (hole == null || board == null) {
                continue;
            }
            long score = scoreOf(hole, board);
            if (score > 0 && HandEvaluator.category(score) >= HandEvaluator.QUADS) {
                return 2_000_000L;
            }
        }
        return 0;
    }

    private long scoreOf(String hole, String board) {
        try {
            List<Card> all = new ArrayList<>();
            for (int i = 0; i + 1 < hole.length(); i += 2) {
                all.add(Card.parse(hole.substring(i, i + 2)));
            }
            for (int i = 0; i + 1 < board.length(); i += 2) {
                all.add(Card.parse(board.substring(i, i + 2)));
            }
            if (all.size() < 5) {
                return 0;
            }
            Card[] arr = all.toArray(new Card[0]);
            return HandEvaluator.best(arr, arr.length, Variant.LONG);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 推荐红包：被邀请人必须打过至少 1 手牌。 */
    private long referralReward(long userId, String inviteeId) {
        long invitee;
        try {
            invitee = Long.parseLong(inviteeId);
        } catch (NumberFormatException e) {
            return 0;
        }
        AppUser u = userMapper.selectById(invitee);
        if (u == null || u.getInviterId() == null || u.getInviterId() != userId) {
            return 0;
        }
        Map<String, Object> st = handPlayerMapper.stats(invitee, "LONG");
        long hands = st == null ? 0 : ((Number) st.getOrDefault("hands", 0)).longValue();
        return hands > 0 ? 1_000_000L : 0;
    }

    /** 按编码查活动配置，不存在返回 null。 */
    public Activity findByCode(String code) {
        QueryWrapper<Activity> q = new QueryWrapper<>();
        q.eq("code", code).last("LIMIT 1");
        return activityMapper.selectOne(q);
    }

    /** 生成邀请码：直接用 userId 的 base36，简单可读。 */
    public String inviteCode(long userId) {
        return "KK" + Long.toString(userId, 36).toUpperCase();
    }
}
