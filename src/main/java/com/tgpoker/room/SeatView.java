package com.tgpoker.room;

import java.util.List;

/**
 * 座位视图。
 *
 * <p>底牌下发规则集中在这里说明，避免各处再写一遍：</p>
 * <ul>
 *   <li>{@code mine}：本人可见；</li>
 *   <li>摊牌（showdown && showCards）或 All-in 亮牌：同桌可见；</li>
 *   <li>旁观者：永远拿不到，摊牌也只给胜方。</li>
 * </ul>
 */
public class SeatView {

    /** 座位号，从 0 开始。 */
    public int seat;

    /** 用户 ID；空位为 null。 */
    public Long userId;

    /** 昵称。 */
    public String nick = "";

    /** 头像 URL。 */
    public String avatar = "";

    /** 是否有人坐。 */
    public boolean seated;

    /** 是否暂离（坐出）。 */
    public boolean sittingOut;

    /** 本手是否已弃牌。 */
    public boolean folded;

    /** 本手是否 All-in。 */
    public boolean allIn;

    /** 本街是否已行动（用于前端置灰）。 */
    public boolean hasActed;

    /** 是否按钮位。 */
    public boolean dealer;

    /** 是否当前行动人。 */
    public boolean acting;

    /** 桌上筹码（不含本街已下注）。 */
    public long stack;

    /** 本街已下注。 */
    public long bet;

    /** 最近一次动作文案，如"跟注"。 */
    public String lastAction = "";

    /** 最近一次动作金额。 */
    public long lastAmount;

    /** 底牌；按上面的规则裁剪，无权查看时为 null。 */
    public List<String> cards;

    /** 是否已亮牌（All-in 或摊牌），前端据此决定牌背是否翻开。 */
    public boolean revealed;

    /** 本手是否参与（用于账单展示）。 */
    public boolean inHand;
}
