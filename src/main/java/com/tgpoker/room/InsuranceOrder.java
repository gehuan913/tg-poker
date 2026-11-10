package com.tgpoker.room;

/** 一手牌里的一张保险单。投保与赔付是两个独立科目。 */
public class InsuranceOrder {

    public long id;
    public String handId;
    public String tableId;
    public int potIndex;

    /** 买保险的人（当时领先的一方）。 */
    public long buyerUserId;
    public int buyerSeat;
    /** 被保险的反超方座位。 */
    public int behindSeat;

    public int outs;
    public double odds;
    public int tieOuts;
    public double tieOdds;

    public long premiumMain;
    public long premiumTie;
    public long coverage;

    /** PENDING / WON（反超成功，庄家赔） / LOST（没反超，保费归庄家）。 */
    public String result = "PENDING";
    public long payout;

    public String nick = "";
    public int handNo;
    public String street = "TURN";
}
