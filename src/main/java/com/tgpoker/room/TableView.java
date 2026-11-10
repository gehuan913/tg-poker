package com.tgpoker.room;

import java.util.ArrayList;
import java.util.List;

/**
 * 发给客户端的牌桌视图（按查看者裁剪）。
 *
 * <p>同一个牌桌状态，对不同人下发不同内容：</p>
 * <ul>
 *   <li>座位玩家：能看到自己的底牌；别人只有摊牌或 All-in 亮牌时才可见；</li>
 *   <li>旁观者：任何人的底牌都为 null，摊牌也只下发胜方（技术方案 §8.5）。</li>
 * </ul>
 *
 * <p>这个对象是"快照"，不是增量；前端拿它整表重绘，避免自己维护 diff 出错。</p>
 */
public class TableView {

    /** 牌桌 ID，如 T61510。 */
    public String tableId;

    /** 牌桌名称，如"永旺娱乐(长牌)"。 */
    public String name;

    /** 玩法：LONG / SHORT。 */
    public String variant;

    /** 玩法中文名：长牌 / 短牌。 */
    public String variantLabel;

    /** 所属俱乐部 ID。 */
    public long clubId;

    /** 小盲（micro-USDT）；短牌前注制时为 0。 */
    public long smallBlind;

    /** 大盲（micro-USDT）。 */
    public long bigBlind;

    /** 前注金额（micro-USDT），短牌必填。 */
    public long ante;

    /** 前注模式：NONE / EVERYONE / BUTTON_DOUBLE。 */
    public String anteMode;

    /** 是否启用 Straddle（UTG 强制 2BB）。 */
    public boolean straddle;

    /** Straddle 金额（micro-USDT），未启用为 0。 */
    public long straddleAmount;

    /** 本手 Straddle 的座位号，-1 表示没有。 */
    public int straddleSeat = -1;

    /** 桌内展示文案，如"短牌前注 0.2"。 */
    public String anteLabel;

    /** 最小买入（micro-USDT）。 */
    public long minBuyIn;

    /** 最大买入（micro-USDT）。 */
    public long maxBuyIn;

    /** 座位总数。 */
    public int maxSeats;

    /** 阶段：WAITING / PREFLOP / FLOP / TURN / RIVER / SHOWDOWN / SETTLED。 */
    public String stage;

    /** 手牌 ID。 */
    public String handId;

    /** 手号（第几手）。 */
    public long handNo;

    /** 按钮位座位号，-1 表示未定。 */
    public int buttonSeat;

    /** 当前行动座位号，-1 表示没人行动（如 All-in runout / 保险窗口）。 */
    public int actingSeat;

    /** 我的座位号，旁观者为 -1。 */
    public int mySeat = -1;

    /** 我的角色：SEATED / SPECTATOR。服务端按真实座位判定，客户端无法提权。 */
    public String role = "SPECTATOR";

    /** 底池（micro-USDT）。 */
    public long pot;

    /** 本街最高下注额。 */
    public long currentBet;

    /** 最小加注幅度。 */
    public long minRaise;

    /** 当前行动截止时间（毫秒时间戳），前端用 serverTime 校正时钟。 */
    public long deadline;

    /** 服务端当前时间，用于计算 offset，避免手机时间不准导致倒计时飘。 */
    public long serverTime;

    /** 是否已摊牌。 */
    public boolean showdown;

    /** 是否满足开局条件（至少 2 人）。 */
    public boolean canStart;

    /** 本桌是否开启保险。 */
    public boolean insuranceEnabled;

    /** 本手服务端种子的 SHA256（开局公示，结算前只给 hash）。 */
    public String serverSeedHash;

    /** 上一手揭示的服务端种子，供玩家复算。 */
    public String lastServerSeed;

    /** 存证标识，如 1#9E18D6BB2C，可直接复制核对。 */
    public String verifyCode;

    /** 本手玩家种子（结算后才公开，否则等于提前泄露熵源）。 */
    public List<String> clientSeeds = new ArrayList<>();

    /** 公牌，按顺序的牌码，如 ["2s","5s","Ah"]。 */
    public List<String> board = new ArrayList<>();

    /** 我的底牌；旁观者为空。 */
    public List<String> myCards = new ArrayList<>();

    /** 各座位视图。 */
    public List<SeatView> seats = new ArrayList<>();

    /** 牌桌日志（最近 30 条）。 */
    public List<String> log = new ArrayList<>();

    /** 桌内聊天（最近 30 条，随快照下发，重连不丢）。 */
    public List<ChatMsg> chat = new ArrayList<>();

    /** 牌桌已运行分钟数（顶栏显示 00:29:52 用）。 */
    public int runningMinutes;

    /** 状态序号，每次变更 +1，用于排查丢帧。 */
    public long seq;

    /** 单次行动倒计时（秒）。 */
    public int actionSeconds;

    /** 旁观人数（只给数字，不给身份）。 */
    public int spectators;

    /** 当前可买的保险报价，null 表示当前不能投保。 */
    public InsuranceQuoteView insurance;

    /** 本手已成交的保险单 + 最近历史（桌内公开，反串通）。 */
    public List<InsuranceOrder> insurances = new ArrayList<>();
}
