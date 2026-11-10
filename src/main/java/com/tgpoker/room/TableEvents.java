package com.tgpoker.room;

/** 牌桌事件回调，由 TableManager 实现。 */
public interface TableEvents {

    /** 状态变化，需要广播。 */
    void onUpdate(TableActor actor);

    /** 一手牌结束，落库。 */
    void onHandSettled(TableActor actor, HandOutcome outcome);

    /** 上桌扣款。返回 false 表示余额不足。必须在牌桌线程里同步完成，避免扣了钱没座位。 */
    boolean onBuyIn(long userId, long amount, String bizId);

    /** 离桌退筹码回钱包。 */
    void onCashOut(long userId, long amount, long tableStack, String memo);

    /** 只发给某个人的错误提示。 */
    void onError(long userId, String message);

    /** 支付保费：从场外钱包扣（桌上可能已经没钱付保费）。 */
    boolean onInsurancePremium(long userId, long amount, String bizId);

    /** 保险赔付：进用户钱包。 */
    void onInsurancePayout(long userId, long amount, String bizId);

    /** 检查俱乐部金库能否覆盖这笔保额（保证金 ≥ 1 个底池）。 */
    boolean onTreasuryReserve(long clubId, long amount);

    /** 金库结算：正数入账（保费），负数出账（赔付）。 */
    void onTreasurySettle(long clubId, long delta);

    /** 玩家是否开启了保险（未开启不报价、不准投保）。 */
    boolean insuranceOptIn(long userId);
}
