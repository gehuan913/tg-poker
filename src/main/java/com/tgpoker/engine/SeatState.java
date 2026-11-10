package com.tgpoker.engine;

/** 座位状态。 */
public class SeatState {

    public int seat;
    public long userId;
    public String nick = "";
    public String avatar = "";

    public boolean seated;
    public boolean sittingOut;

    /** 本手牌是否参与。 */
    public boolean inHand;
    /** 桌上筹码（不含本街已下注部分）。 */
    public long stack;
    /** 本街已下注。 */
    public long bet;
    /** 本手牌累计投入，用于边池分层。 */
    public long contributed;

    public boolean folded;
    public boolean allIn;
    /** 本街是否已经行动过。 */
    public boolean hasActed;

    public Card[] hole = new Card[2];
    public boolean showCards;

    public String lastAction = "";
    public long lastAmount;

    // ---- 桌内公开统计（对应竞品"账单"Tab） ----
    /** 本次上桌累计买入，用于算桌内输赢。 */
    public long buyInTotal;
    /** 本次上桌打过的完整手数。 */
    public int handsPlayed;
    /** 翻前主动入池手数（VPIP 分子）。 */
    public int vpipHands;
    /** 翻前主动加注/全下手数（PFR 分子）。 */
    public int pfrHands;

    /** 本手是否入池 / 是否翻前加注，结算时累加到上面的计数器。 */
    public boolean vpipThisHand;
    public boolean pfrThisHand;

    public void resetForHand() {
        bet = 0;
        contributed = 0;
        folded = !seated || sittingOut || stack <= 0;
        allIn = false;
        hasActed = false;
        hole = new Card[2];
        showCards = false;
        lastAction = "";
        lastAmount = 0;
        inHand = !folded;
        vpipThisHand = false;
        pfrThisHand = false;
    }

    public boolean canAct() {
        return inHand && !folded && !allIn && stack > 0;
    }

    /** 桌内输赢 = 当前筹码 − 累计买入。 */
    public long tableNet() {
        return stack - buyInTotal;
    }

    public int vpipPercent() {
        return handsPlayed == 0 ? 0 : Math.round(vpipHands * 100f / handsPlayed);
    }

    public int pfrPercent() {
        return handsPlayed == 0 ? 0 : Math.round(pfrHands * 100f / handsPlayed);
    }
}
