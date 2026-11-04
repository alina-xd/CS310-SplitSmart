package com.team19.splitsmart.service.split;

import com.team19.splitsmart.util.MoneyUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exact-amount split (FR-2.4.2). inputs are dollars per participant. Every participant needs an
 * amount ($0.00 is allowed, negatives are not), and the amounts must add up exactly to the
 * total. Owner: Part C.
 */
public class ExactAmountSplit implements SplitStrategy {

    @Override
    public Map<String, Long> computeShares(long totalCents, Map<String, Double> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Select at least one participant");
        }
        Map<String, Long> shares = new LinkedHashMap<>();
        long sum = 0;
        for (Map.Entry<String, Double> entry : inputs.entrySet()) {
            Double dollars = entry.getValue();
            if (dollars == null || dollars.isNaN() || dollars.isInfinite()) {
                throw new IllegalArgumentException("Enter an amount for every participant");
            }
            if (dollars < 0) {
                throw new IllegalArgumentException("Amounts can't be negative");
            }
            long cents = Math.round(dollars * 100);
            shares.put(entry.getKey(), cents);
            sum += cents;
        }
        if (sum != totalCents) {
            throw new IllegalArgumentException("Shares add up to " + MoneyUtil.format(sum)
                    + " but the expense is " + MoneyUtil.format(totalCents));
        }
        return shares;
    }
}
