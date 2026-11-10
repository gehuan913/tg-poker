package com.tgpoker.engine;

import java.util.ArrayList;
import java.util.List;

/** 一个底池层。eligible 为有资格分这一层的座位号。 */
public class Pot {

    public long amount;
    public final List<Integer> eligible = new ArrayList<>();

    public Pot(long amount, List<Integer> eligible) {
        this.amount = amount;
        this.eligible.addAll(eligible);
    }
}
