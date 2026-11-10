package com.tgpoker.engine;

import java.util.ArrayList;
import java.util.List;

/** 一张牌桌的完整状态。只被 TableActor 的单线程访问，因此不需要同步。 */
public class TableState {

    public String tableId;
    public String name;
    public Variant variant = Variant.LONG;

    public long smallBlind;
    public long bigBlind;
    public long minBuyIn;
    public long maxBuyIn;

    /** 前注（短牌必用）：每人前注，按钮位 2 倍。 */
    public long ante;
    /** NONE / EVERYONE / BUTTON_DOUBLE。 */
    public String anteMode = "NONE";

    /** 是否启用 Straddle（UTG 位置强制下 2BB 的第三盲注）。 */
    public boolean straddle;
    /** Straddle 金额，通常为 2×BB。 */
    public long straddleAmount;
    /** 本手 Straddle 的座位号，-1 表示本手没有。 */
    public int straddleSeat = -1;

    public int maxSeats = 6;
    public int buttonSeat = -1;
    public final List<SeatState> seats = new ArrayList<>();

    public Stage stage = Stage.WAITING;
    public final List<Card> board = new ArrayList<>();

    /** 底池（含本街已下注）。 */
    public long pot;
    /** 本街最高下注额。 */
    public long currentBet;
    /** 最小加注幅度。 */
    public long minRaise;

    public int actingSeat = -1;
    public long deadline;
    public int lastAggressor = -1;

    public Deck deck;
    public String handId;
    public long handNo;

    /** 可验证发牌的三种材料：服务端种子、玩家种子、nonce。 */
    public String serverSeed;
    public String serverSeedHash;
    public String lastServerSeed;
    /** 本手参与洗牌的玩家种子（按座位顺序），空列表表示纯服务端种子。 */
    public final List<String> clientSeeds = new ArrayList<>();
    /** 存证标识，如 565#F1721C1DEA，玩家可直接复制核对。 */
    public String verifyCode;

    public boolean showdown;
    public long seq;

    /** 保险窗口：全员 All-in 且已到转牌时挂起，超时或买完再发河牌。 */
    public boolean insurancePending;
    public boolean insuranceEnabled;

    /** 本手赢家座位（可能多人平分）。保险结算要用：反超方是否真的赢了。 */
    public final List<Integer> winners = new ArrayList<>();

    /** 给前端展示的牌桌日志（最近若干条）。 */
    public final List<String> log = new ArrayList<>();

    public TableState(int maxSeats) {
        this.maxSeats = maxSeats;
        for (int i = 0; i < maxSeats; i++) {
            SeatState s = new SeatState();
            s.seat = i;
            seats.add(s);
        }
    }

    public void logMsg(String msg) {
        log.add(msg);
        while (log.size() > 30) {
            log.remove(0);
        }
    }

    public SeatState seatOf(long userId) {
        for (SeatState s : seats) {
            if (s.seated && s.userId == userId) {
                return s;
            }
        }
        return null;
    }

    public int countSeated() {
        int n = 0;
        for (SeatState s : seats) {
            if (s.seated && s.stack > 0 && !s.sittingOut) {
                n++;
            }
        }
        return n;
    }

    public List<Integer> activeSeats() {
        List<Integer> list = new ArrayList<>();
        for (SeatState s : seats) {
            if (s.seated && !s.sittingOut && s.stack > 0) {
                list.add(s.seat);
            }
        }
        return list;
    }

    public String boardString() {
        StringBuilder sb = new StringBuilder();
        for (Card c : board) {
            sb.append(c.code());
        }
        return sb.toString();
    }

    /** 玩家种子的拼接形式，参与 deriveSeed。 */
    public String clientSeedConcat() {
        return String.join(",", clientSeeds);
    }
}
