package com.team19.splitsmart.service;

/** Balance between the user and one other member. Positive: they owe the user. Negative: the user owes them. */
public class PairBalance {

    private final String otherUserId;
    private final long amountCents;

    public PairBalance(String otherUserId, long amountCents) {
        this.otherUserId = otherUserId;
        this.amountCents = amountCents;
    }

    public String getOtherUserId() {
        return otherUserId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public boolean isSettledUp() {
        return amountCents == 0;
    }

    public boolean userOwes() {
        return amountCents < 0;
    }
}
