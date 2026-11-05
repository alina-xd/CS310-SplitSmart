package com.team19.splitsmart.service.split;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Percentage split (FR-2.4.3). inputs are percents per participant (0 is allowed, negatives are
 * not) and must add up to 100. Each share is floor(total * percent / 100) cents; the leftover
 * cents then go one at a time, in map order, to participants with a percent above 0, so the
 * shares add up exactly to the total (FR-2.5). Owner: Part C.
 */
public class PercentageSplit implements SplitStrategy {

    private static final double TOLERANCE = 0.0001;

    @Override
    public Map<String, Long> computeShares(long totalCents, Map<String, Double> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Select at least one participant");
        }
        if (totalCents < 0) {
            throw new IllegalArgumentException("The amount can't be negative");
        }
        double percentSum = 0;
        BigDecimal exactPercentSum = BigDecimal.ZERO;
        for (Double percent : inputs.values()) {
            if (percent == null || percent.isNaN() || percent.isInfinite()) {
                throw new IllegalArgumentException("Enter a percentage for every participant");
            }
            if (percent < 0) {
                throw new IllegalArgumentException("Percentages can't be negative");
            }
            percentSum += percent;
            exactPercentSum = exactPercentSum.add(BigDecimal.valueOf(percent));
        }
        if (Math.abs(percentSum - 100) > TOLERANCE) {
            throw new IllegalArgumentException("Percentages add up to "
                    + BigDecimal.valueOf(percentSum).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                    + "% but must add up to 100%");
        }

        Map<String, Long> shares = new LinkedHashMap<>();
        List<String> canTakeExtra = new ArrayList<>();
        long assigned = 0;
        BigDecimal total = BigDecimal.valueOf(totalCents);
        for (Map.Entry<String, Double> entry : inputs.entrySet()) {
            // BigDecimal avoids double errors like 0.29 * 100 = 28.999...
            long cents = total.multiply(BigDecimal.valueOf(entry.getValue()))
                    // Normalize the accepted tolerance so even large totals conserve every cent.
                    .divide(exactPercentSum, 0, RoundingMode.FLOOR)
                    .longValueExact();
            shares.put(entry.getKey(), cents);
            assigned += cents;
            if (entry.getValue() > 0) {
                canTakeExtra.add(entry.getKey());
            }
        }

        long leftover = totalCents - assigned;
        for (int i = 0; leftover > 0 && !canTakeExtra.isEmpty(); i = (i + 1) % canTakeExtra.size()) {
            String uid = canTakeExtra.get(i);
            shares.put(uid, shares.get(uid) + 1);
            leftover--;
        }
        return shares;
    }
}
