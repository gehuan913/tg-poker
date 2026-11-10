package com.tgpoker.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 边池分层。
 *
 * <p>把每个玩家的总投入按金额分层，每层由「投入达到该金额且未弃牌」的人竞争。
 * 弃牌者出的钱留在池里但不参与分配；某层只有一个人有资格时直接退回给他。</p>
 */
public final class PotBuilder {

    private PotBuilder() {
    }

    /**
     * @param contributed 每个座位本手牌的总投入
     * @param folded      每个座位是否已弃牌
     */
    public static List<Pot> build(long[] contributed, boolean[] folded) {
        long[] sorted = contributed.clone();
        java.util.Arrays.sort(sorted);

        List<Long> levels = new ArrayList<>();
        long prevLevel = -1;
        for (long v : sorted) {
            if (v > 0 && v != prevLevel) {
                levels.add(v);
                prevLevel = v;
            }
        }

        List<Pot> pots = new ArrayList<>();
        long prev = 0;
        for (long lv : levels) {
            long amount = 0;
            List<Integer> eligible = new ArrayList<>();
            for (int s = 0; s < contributed.length; s++) {
                long c = contributed[s];
                if (c >= lv) {
                    amount += lv - prev;
                    if (!folded[s]) {
                        eligible.add(s);
                    }
                } else if (c > prev) {
                    // 不足额者的残余部分（理论上不会出现，防御性处理）
                    amount += c - prev;
                }
            }
            if (amount > 0) {
                if (eligible.size() == 1) {
                    // 只有一个人有资格，这一层其实是直接退给他的
                    pots.add(new Pot(amount, eligible));
                } else if (!eligible.isEmpty()) {
                    pots.add(new Pot(amount, eligible));
                } else {
                    // 全弃牌（理论上不会发生），归最后一名未弃牌者，交由上层处理
                    pots.add(new Pot(amount, new ArrayList<>()));
                }
            }
            prev = lv;
        }
        return pots;
    }
}
