package com.tgpoker.room;

/** 桌内"账单"Tab 的一行：昵称 / 买入 / 手数 / 入池率 / 输赢。 */
public class TableLedgerRow {

    public int seat;
    public long userId;
    public String nick = "";
    public String avatar = "";

    /** 买入金额：按文档 §11.1 只对本人可见，其他人拿 null。 */
    public Long buyIn;
    public String buyInText;

    public int hands;
    public int vpip;
    public int pfr;

    /** 桌内输赢 = 当前筹码 − 累计买入。 */
    public long net;
    public String netText;

    public boolean seated;
}
