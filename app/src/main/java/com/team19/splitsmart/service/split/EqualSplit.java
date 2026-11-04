package com.team19.splitsmart.service.split;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Equal split (FR-2.4.1). Only the keys of inputs matter: they are the participants, in order.
 * Each participant gets total / n cents, and the first total % n participants get one extra
 * cent, so $10.00 / 3 gives 334, 333, 333 (FR-2.5). Owner: Part C.
 */
public class EqualSplit implements SplitStrategy {

    @Override
    public Map<String, Long> computeShares(long totalCents, Map<String, Double> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Select at least one participant");
        }
        if (totalCents < 0) {
            throw new IllegalArgumentException("The amount can't be negative");
        }
        int n = inputs.size();
        long base = totalCents / n;
        long extra = totalCents % n;

        Map<String, Long> shares = new LinkedHashMap<>();
        int i = 0;
        for (String uid : inputs.keySet()) {
            shares.put(uid, base + (i < extra ? 1 : 0));
            i++;
        }
        return shares;
    }
}
