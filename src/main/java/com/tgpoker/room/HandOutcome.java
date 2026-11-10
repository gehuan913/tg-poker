package com.tgpoker.room;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 一手牌打完后的结算数据，用于落库。 */
public class HandOutcome {

    public String handId;
    public String tableId;
    public long clubId;
    public String variant;
    public String blinds;
    public long ante;
    public String board;
    public long pot;

    public String serverSeed;
    public String serverSeedHash;
    public String clientSeeds;
    public long nonce;
    public String verifyCode;

    public LocalDateTime startedAt;
    public LocalDateTime endedAt;

    public List<PlayerResult> players = new ArrayList<>();

    /** 本手保险单（已结算）。 */
    public List<InsuranceOrder> insurances = new ArrayList<>();

    public static class PlayerResult {
        public int seat;
        public long userId;
        public String holeCards;
        public long startStack;
        public long endStack;
        public long net;

        /** 翻前是否入池 / 是否加注，用于 VPIP / PFR 口径。 */
        public int vpip;
        public int pfr;
        /** 本手动作序列，JSON 字符串，风控回放用。 */
        public String actions;
        /** 本手保险净额（赔付 − 保费）。 */
        public long insuranceNet;
    }
}
