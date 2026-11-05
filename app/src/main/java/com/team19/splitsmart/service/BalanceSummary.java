package com.team19.splitsmart.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of BalanceService.getSummary: one entry per other member, plus two separate totals. */
public class BalanceSummary {

    private final long totalToPayCents;
    private final long totalToReceiveCents;
    private final List<PairBalance> pairBalances;

    public BalanceSummary(
            long totalToPayCents, long totalToReceiveCents, List<PairBalance> pairBalances) {
        this.totalToPayCents = totalToPayCents;
        this.totalToReceiveCents = totalToReceiveCents;
        this.pairBalances =
                Collections.unmodifiableList(
                        pairBalances == null ? new ArrayList<>() : new ArrayList<>(pairBalances));
    }

    public long getTotalToPayCents() {
        return totalToPayCents;
    }

    public long getTotalToReceiveCents() {
        return totalToReceiveCents;
    }

    public List<PairBalance> getPairBalances() {
        return pairBalances;
    }
}
