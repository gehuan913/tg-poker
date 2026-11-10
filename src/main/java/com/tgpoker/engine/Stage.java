package com.tgpoker.engine;

/**
 * 牌局阶段。
 *
 * <p>正常链路：WAITING → PREFLOP → FLOP → TURN → RIVER → SHOWDOWN → SETTLED → WAITING。</p>
 *
 * <p>两个旁路状态：</p>
 * <ul>
 *   <li>{@link #PAUSED}：管理员暂停，冻结计时器但<b>不冻结底池</b>，恢复后继续；</li>
 *   <li>{@link #DISBANDED}：解散，退还所有筹码并写结算，随后重置为 WAITING。</li>
 * </ul>
 */
public enum Stage {
    WAITING,
    PREFLOP,
    FLOP,
    TURN,
    RIVER,
    SHOWDOWN,
    SETTLED,
    PAUSED,
    DISBANDED
}
