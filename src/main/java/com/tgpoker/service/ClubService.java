package com.tgpoker.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tgpoker.mapper.ClubMapper;
import com.tgpoker.mapper.ClubMemberMapper;
import com.tgpoker.mapper.ClubTreasuryMapper;
import com.tgpoker.mapper.HandMapper;
import com.tgpoker.model.Club;
import com.tgpoker.model.ClubMember;
import com.tgpoker.model.ClubTreasury;
import com.tgpoker.model.Hand;
import com.tgpoker.util.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 俱乐部与金库。
 *
 * <p>金库是保险的承保方：保证金必须 ≥ 1 个底池才允许开保险，否则玩家赢了保险
 * 却赔不出来。所以金库既要有余额，也要有"最低保证金"这个概念。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClubService {

    private final ClubMapper clubMapper;
    private final ClubMemberMapper memberMapper;
    private final ClubTreasuryMapper treasuryMapper;
    private final HandMapper handMapper;

    private static final String DEFAULT_NAME = "永旺德州";

    /** 默认俱乐部（第一次启动时创建，ID 固定用于内存牌桌）。 */
    @Transactional
    public Club ensureDefaultClub() {
        QueryWrapper<Club> q = new QueryWrapper<>();
        q.eq("name", DEFAULT_NAME).last("LIMIT 1");
        Club c = clubMapper.selectOne(q);
        if (c != null) {
            ensureTreasury(c.getId(), 500_000_000L);
            return c;
        }
        Club nc = new Club();
        nc.setName(DEFAULT_NAME);
        nc.setOwnerId(0L);
        nc.setAvatar("");
        nc.setNotice("欢迎来到永旺德州，禁止任何形式的伙牌与代打。");
        nc.setJoinType("OPEN");
        nc.setMinBuyIn(10_000_000L);
        nc.setMaxBuyIn(400_000_000L);
        nc.setInsuranceEnabled(1);
        nc.setCreatedAt(LocalDateTime.now());
        clubMapper.insert(nc);
        ensureTreasury(nc.getId(), 500_000_000L);
        join(nc.getId(), 0L, "OWNER");
        log.info("默认俱乐部已创建 id={}", nc.getId());
        return nc;
    }

    /** 金库不存在则创建，并给一笔初始保证金（开发期默认 500U）。 */
    public void ensureTreasury(long clubId, long initial) {
        ClubTreasury t = treasuryMapper.selectById(clubId);
        if (t != null) {
            return;
        }
        ClubTreasury nt = new ClubTreasury();
        nt.setClubId(clubId);
        nt.setBalance(initial);
        nt.setMinReserve(0L);
        nt.setVersion(0L);
        treasuryMapper.insert(nt);
    }

    /** 按名称模糊搜索俱乐部，最多 20 条。 */
    public List<Map<String, Object>> search(String keyword) {
        QueryWrapper<Club> q = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.like("name", keyword.trim());
        }
        q.orderByAsc("id").last("LIMIT 20");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Club c : clubMapper.selectList(q)) {
            out.add(toMap(c));
        }
        return out;
    }

    /** 实体转前端展示结构（成员数、金库余额一并带出）。 */
    public Map<String, Object> toMap(Club c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("avatar", c.getAvatar());
        m.put("notice", c.getNotice());
        m.put("joinType", c.getJoinType());
        m.put("members", memberMapper.countMembers(c.getId()));
        m.put("minBuyIn", c.getMinBuyIn());
        m.put("minBuyInText", Money.fmt(c.getMinBuyIn() == null ? 0 : c.getMinBuyIn()));
        m.put("insuranceEnabled", c.getInsuranceEnabled() != null && c.getInsuranceEnabled() == 1);
        ClubTreasury t = treasuryMapper.selectById(c.getId());
        m.put("treasuryText", Money.fmt(t == null || t.getBalance() == null ? 0 : t.getBalance()));
        return m;
    }

    /** 创建俱乐部：创建者自动成为 OWNER，并初始化金库。 */
    @Transactional
    public Club create(long ownerId, String name, String notice, String joinType) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("俱乐部名称不能为空");
        }
        Club c = new Club();
        c.setName(name.trim());
        c.setOwnerId(ownerId);
        c.setAvatar("");
        c.setNotice(notice == null ? "" : notice);
        c.setJoinType(joinType == null ? "OPEN" : joinType);
        c.setMinBuyIn(10_000_000L);
        c.setMaxBuyIn(400_000_000L);
        c.setInsuranceEnabled(1);
        c.setCreatedAt(LocalDateTime.now());
        clubMapper.insert(c);
        ensureTreasury(c.getId(), 0L);
        join(c.getId(), ownerId, "OWNER");
        return c;
    }

    /**
     * 加入俱乐部（幂等：已在里面直接返回）。
     *
     * @param role OWNER / ADMIN / MEMBER
     */
    @Transactional
    public void join(long clubId, long userId, String role) {
        ClubMember existing = memberMapper.find(clubId, userId);
        if (existing != null) {
            return;
        }
        ClubMember m = new ClubMember();
        m.setClubId(clubId);
        m.setUserId(userId);
        m.setRole(role == null ? "MEMBER" : role);
        m.setStatus("ACTIVE");
        m.setJoinedAt(LocalDateTime.now());
        try {
            memberMapper.insert(m);
        } catch (Exception e) {
            log.debug("重复加入俱乐部 club={} user={}", clubId, userId);
        }
    }

    /** 我加入的俱乐部（带角色）。 */
    public List<Map<String, Object>> myClubs(long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ClubMember row : memberMapper.byUser(userId)) {
            Long clubId = row.getClubId();
            if (clubId == null) {
                continue;
            }
            Club c = clubMapper.selectById(clubId);
            if (c == null) {
                continue;
            }
            Map<String, Object> m = toMap(c);
            m.put("role", row.getRole());
            out.add(m);
        }
        return out;
    }

    /** 俱乐部成员列表。 */
    public List<Map<String, Object>> members(long clubId) {
        return com.tgpoker.util.Maps.camelAll(memberMapper.members(clubId));
    }

    /** 俱乐部牌局记录（按手牌维度，最近 N 手）。 */
    public List<Hand> records(long clubId, int limit) {
        QueryWrapper<Hand> q = new QueryWrapper<>();
        q.eq("club_id", clubId).orderByDesc("started_at").last("LIMIT " + Math.min(limit, 50));
        return handMapper.selectList(q);
    }

    /** 金库余额与最低保证金。 */
    public Map<String, Object> treasury(long clubId) {
        ClubTreasury t = treasuryMapper.selectById(clubId);
        Map<String, Object> m = new LinkedHashMap<>();
        long balance = t == null || t.getBalance() == null ? 0 : t.getBalance();
        m.put("balance", balance);
        m.put("balanceText", Money.fmt(balance));
        m.put("minReserve", t == null ? 0 : t.getMinReserve());
        m.put("minReserveText", Money.fmt(t == null || t.getMinReserve() == null ? 0 : t.getMinReserve()));
        return m;
    }

    /** 金库能否覆盖这笔保额（保证金 ≥ 该金额）。 */
    public boolean canReserve(long clubId, long amount) {
        if (clubId <= 0) {
            return true;
        }
        ClubTreasury t = treasuryMapper.selectById(clubId);
        if (t == null) {
            return false;
        }
        long bal = t.getBalance() == null ? 0 : t.getBalance();
        long reserve = t.getMinReserve() == null ? 0 : t.getMinReserve();
        return bal - reserve >= amount;
    }

    /** 金库结算：正数入账（保费），负数出账（赔付）。 */
    @Transactional
    public void settle(long clubId, long delta) {
        if (clubId <= 0 || delta == 0) {
            return;
        }
        int rows = treasuryMapper.apply(clubId, delta);
        if (rows == 0) {
            log.error("金库结算失败 club={} delta={}（余额不足或俱乐部不存在）", clubId, delta);
            throw new IllegalStateException("俱乐部金库余额不足");
        }
    }

    /** 查俱乐部实体，不存在返回 null。 */
    public Club get(long clubId) {
        return clubMapper.selectById(clubId);
    }
}
