package com.tgpoker.service;

import com.tgpoker.mapper.AppUserMapper;
import com.tgpoker.model.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserMapper userMapper;
    private final WalletService walletService;

    @Value("${app.new-user-bonus:0}")
    private long newUserBonus;

    /**
     * 按 Telegram 用户建号 / 取号。id 用 Telegram user id，保证同一账号稳定。
     */
    public AppUser getOrCreate(long userId, String nick, String username, String avatar) {
        AppUser u = userMapper.selectById(userId);
        if (u != null) {
            // 昵称可能改了，顺手同步
            boolean changed = false;
            if (nick != null && !nick.equals(u.getNick())) {
                u.setNick(nick);
                changed = true;
            }
            if (avatar != null && !avatar.equals(u.getAvatar())) {
                u.setAvatar(avatar);
                changed = true;
            }
            if (changed) {
                userMapper.updateById(u);
            }
            walletService.ensureAccount(userId, 0);
            return u;
        }

        AppUser nu = new AppUser();
        nu.setId(userId);
        nu.setTgId(userId);
        nu.setNick(nick == null ? ("玩家" + (userId % 10000)) : nick);
        nu.setUsername(username);
        nu.setAvatar(avatar == null ? "" : avatar);
        nu.setCreatedAt(LocalDateTime.now());
        userMapper.insert(nu);

        walletService.ensureAccount(userId, newUserBonus);
        return nu;
    }

    /** 按 ID 查用户，不存在返回 null。 */
    public AppUser get(long userId) {
        return userMapper.selectById(userId);
    }

    /** 更新用户资料（昵称、邀请人等）。 */
    public void update(AppUser u) {
        userMapper.updateById(u);
    }

    /**
     * 按昵称 / 用户 ID 搜索，发红包选联系人用。
     *
     * <p>只返回 ID、昵称、头像，不返回余额等敏感字段。</p>
     */
    public java.util.List<AppUser> search(String q, long excludeId, int limit) {
        String raw = q == null ? "" : q.trim();
        if (raw.isEmpty()) {
            return java.util.List.of();
        }
        String like = "%" + raw + "%";
        return userMapper.search(like, raw, excludeId, Math.min(limit, 30));
    }

    /** 是否开启保险：字段为空按开启处理（老数据兼容）。 */
    public boolean isInsuranceOptIn(long userId) {
        AppUser u = userMapper.selectById(userId);
        return u == null || u.getInsuranceOptIn() == null || u.getInsuranceOptIn() != 0;
    }

    /** 开关保险。 */
    public void setInsuranceOptIn(long userId, boolean on) {
        AppUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        u.setInsuranceOptIn(on ? 1 : 0);
        userMapper.updateById(u);
    }
}
