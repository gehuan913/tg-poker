package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户。
 *
 * <p>主键直接用 Telegram 的 user.id，这样同一个 TG 账号换设备、重装都还是同一个人，
 * 不需要再做 unionId 之类的映射。</p>
 */
@Data
@TableName("app_user")
public class AppUser {

    /** 用户 ID，等于 Telegram user.id。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** Telegram user.id，冗余保存，便于排查数据问题。 */
    private Long tgId;

    /** Telegram username，可能为空。 */
    private String username;

    /** 昵称，取 first_name + last_name，可被用户修改。 */
    private String nick;

    /** 头像 URL，来自 Telegram photo_url。 */
    private String avatar;

    /** 邀请人用户 ID，用于推荐红包结算。 */
    private Long inviterId;

    /** VIP 等级，0=普通。 */
    private Integer vipLevel;

    /** 是否开启保险：1=开，0=关。关闭后不报价、不能投保。 */
    private Integer insuranceOptIn;

    /** 注册时间（UTC）。 */
    private LocalDateTime createdAt;
}
