package com.team19.splitsmart.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.model.SplitMethod;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.service.split.EqualSplit;
import com.team19.splitsmart.service.split.ExactAmountSplit;
import com.team19.splitsmart.service.split.PercentageSplit;
import com.team19.splitsmart.service.split.SplitStrategy;
import com.team19.splitsmart.service.split.SplitStrategyFactory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Part C white-box tests (author: Dawi Yoon). Each test is written from the source code of one
 * Part C class and aims to execute every branch in it (branch coverage). The comment above each
 * test lists the branches it covers.
 *
 * Run: ./gradlew testDebugUnitTest --tests "com.team19.splitsmart.service.PartCWhiteBoxTest"
 */
public class PartCWhiteBoxTest {

    /** Builds an ordered uid -> value map from pairs: "a", 1.0, "b", 2.0, ... */
    private static Map<String, Double> in(Object... pairs) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Object v = pairs[i + 1];
            map.put((String) pairs[i], v == null ? null : ((Number) v).doubleValue());
        }
        return map;
    }

    private static long sum(Map<String, Long> shares) {
        long total = 0;
        for (long v : shares.values()) {
            total += v;
        }
        return total;
    }

    /**
     * WB-C1 EqualSplit.computeShares. Branches: inputs null (throw), inputs empty (throw),
     * totalCents < 0 (throw), and inside the loop both sides of "i < extra" (participants that
     * do and don't get the extra cent), plus extra == 0 (even division, loop never adds a cent).
     */
    @Test
    public void wbC1_equalSplit_coversGuardsAndExtraCentBranch() {
        EqualSplit split = new EqualSplit();

        assertThrows(IllegalArgumentException.class, () -> split.computeShares(1000, null));
        assertThrows(IllegalArgumentException.class, () -> split.computeShares(1000, in()));
        assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(-1, in("a", 0, "b", 0)));

        // 1001 / 4 = 250 rem 1: only the first participant takes the "i < extra" true branch.
        Map<String, Long> uneven = split.computeShares(1001, in("a", 0, "b", 0, "c", 0, "d", 0));
        assertEquals(Arrays.asList(251L, 250L, 250L, 250L), new ArrayList<>(uneven.values()));

        // extra == 0: every participant takes the false branch.
        Map<String, Long> even = split.computeShares(900, in("a", 0, "b", 0, "c", 0));
        assertEquals(Arrays.asList(300L, 300L, 300L), new ArrayList<>(even.values()));

        // Single participant and a $0.00 total are both allowed by the code.
        assertEquals(1234L, (long) split.computeShares(1234, in("a", 0)).get("a"));
        assertEquals(0, sum(split.computeShares(0, in("a", 0, "b", 0))));
    }

    /**
     * WB-C2 ExactAmountSplit.computeShares. Branches: inputs null / empty (throw), value null,
     * NaN and infinite (each a separate condition of the same "||"), value < 0 (throw),
     * sum != total in both directions (throw), and the success path where Math.round fixes
     * binary-double error (0.29 * 100 = 28.999...).
     */
    @Test
    public void wbC2_exactSplit_coversEveryRejectBranchAndRounding() {
        ExactAmountSplit split = new ExactAmountSplit();

        assertThrows(IllegalArgumentException.class, () -> split.computeShares(100, null));
        assertThrows(IllegalArgumentException.class, () -> split.computeShares(100, in()));
        assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(100, in("a", null, "b", 1)));
        assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(100, in("a", Double.NaN, "b", 1)));
        assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(100, in("a", Double.POSITIVE_INFINITY, "b", 1)));
        assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(100, in("a", -0.01, "b", 1.01)));

        IllegalArgumentException under = assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(3000, in("a", 10, "b", 10)));
        assertEquals("Shares add up to $20.00 but the expense is $30.00", under.getMessage());
        IllegalArgumentException over = assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(3000, in("a", 20, "b", 10.01)));
        assertEquals("Shares add up to $30.01 but the expense is $30.00", over.getMessage());

        Map<String, Long> ok = split.computeShares(100, in("a", 0.29, "b", 0.71, "c", 0));
        assertEquals(29L, (long) ok.get("a"));
        assertEquals(71L, (long) ok.get("b"));
        assertEquals(0L, (long) ok.get("c"));
    }

    /**
     * WB-C3 PercentageSplit.computeShares. Branches: null/empty inputs, negative total, null /
     * NaN / infinite / negative percent, |sum - 100| > tolerance (throw), "percent > 0" true and
     * false when choosing who may take leftover cents, and the leftover loop running zero and
     * several times.
     *
     * Regression: percentages within the 0.0001 tolerance but slightly above 100 used to be
     * divided by exactly 100, so on a large total the shares added up to MORE than the expense.
     * The fix divides by the exact percentage sum instead; the last assertion fails on the old code.
     */
    @Test
    public void wbC3_percentageSplit_coversGuardsLeftoverLoopAndToleranceRegression() {
        PercentageSplit split = new PercentageSplit();

        assertThrows(IllegalArgumentException.class, () -> split.computeShares(100, null));
        assertThrows(IllegalArgumentException.class, () -> split.computeShares(100, in()));
        assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(-1, in("a", 100)));
        assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(100, in("a", null, "b", 100)));
        assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(100, in("a", Double.NaN, "b", 100)));
        assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(100, in("a", Double.NEGATIVE_INFINITY, "b", 100)));
        assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(100, in("a", -10, "b", 110)));
        IllegalArgumentException off = assertThrows(IllegalArgumentException.class,
                () -> split.computeShares(100, in("a", 50, "b", 49.9)));
        assertEquals("Percentages add up to 99.9% but must add up to 100%", off.getMessage());

        // Leftover loop runs 0 times: 25/75 of $40.00 divides exactly.
        Map<String, Long> exact = split.computeShares(4000, in("a", 25, "b", 75));
        assertEquals(1000L, (long) exact.get("a"));
        assertEquals(3000L, (long) exact.get("b"));

        // Leftover loop runs twice; the 0% participant is skipped ("percent > 0" false).
        Map<String, Long> thirds = split.computeShares(100,
                in("zero", 0, "a", 100.0 / 3, "b", 100.0 / 3, "c", 100.0 / 3));
        assertEquals(0L, (long) thirds.get("zero"));
        assertEquals(Arrays.asList(0L, 34L, 33L, 33L), new ArrayList<>(thirds.values()));

        // Regression: 50.00005 + 50.00004 = 100.00009 is accepted (within tolerance).
        long bigTotal = 1_000_000_000L; // $10,000,000.00
        Map<String, Long> big = split.computeShares(bigTotal, in("a", 50.00005, "b", 50.00004));
        assertEquals(bigTotal, sum(big));
    }

    /**
     * WB-C4 MonthlySummaryCalculator.compute. Branches: transactions null, month null, a
     * Settlement (instanceof Expense false), createdDate null, createdDate from another month,
     * prefix boundary ("2026-1" must not match "2026-10-.." or "2026-11-.."), category null
     * (falls back to OTHER), and the counted-expense path. Also checks all 5 categories are
     * always present and that the category totals add up to the total (FR-5.2).
     */
    @Test
    public void wbC4_monthlySummary_coversEverySkipBranchAndCategoryFallback() {
        MonthlySummary empty = MonthlySummaryCalculator.compute(null, "2026-11");
        assertEquals(0, empty.getExpenseCount());
        assertEquals(5, empty.getCategoryTotals().size());
        assertEquals(0, MonthlySummaryCalculator.compute(new ArrayList<>(), null).getExpenseCount());

        List<Transaction> list = new ArrayList<>();
        list.add(expense("2026-11-01", 1500, Category.FOOD));
        list.add(expense("2026-11-30", 2500, null)); // null category -> OTHER
        list.add(expense(null, 9999, Category.FOOD)); // no date: skipped
        list.add(expense("2026-10-31", 7777, Category.HOUSING)); // other month: skipped
        Settlement settlement = new Settlement();
        settlement.setCreatedDate("2026-11-15");
        settlement.setAmountCents(4444);
        list.add(settlement); // settlement: skipped

        MonthlySummary nov = MonthlySummaryCalculator.compute(list, "2026-11");
        assertEquals(2, nov.getExpenseCount());
        assertEquals(4000, nov.getTotalCents());
        assertEquals(1500L, (long) nov.getCategoryTotals().get(Category.FOOD));
        assertEquals(2500L, (long) nov.getCategoryTotals().get(Category.OTHER));
        assertEquals(0L, (long) nov.getCategoryTotals().get(Category.HOUSING));
        long categorySum = 0;
        for (long v : nov.getCategoryTotals().values()) {
            categorySum += v;
        }
        assertEquals(nov.getTotalCents(), categorySum);

        // "2026-1" is a prefix of "2026-10" and "2026-11" but is not a real month: no matches.
        assertEquals(0, MonthlySummaryCalculator.compute(list, "2026-1").getExpenseCount());
    }

    /**
     * WB-C5 SplitStrategyFactory.getStrategy. Branches: each case of the switch (EXACT_AMOUNT,
     * PERCENTAGE, EQUAL/default). Then, for each returned strategy, the shares always add up to
     * the total over many totals (FR-2.5), which exercises the success path of all three.
     */
    @Test
    public void wbC5_factory_coversEverySwitchCase_andSharesAlwaysAddUp() {
        SplitStrategyFactory factory = new SplitStrategyFactory();
        assertTrue(factory.getStrategy(SplitMethod.EQUAL) instanceof EqualSplit);
        assertTrue(factory.getStrategy(SplitMethod.EXACT_AMOUNT) instanceof ExactAmountSplit);
        assertTrue(factory.getStrategy(SplitMethod.PERCENTAGE) instanceof PercentageSplit);
        assertSame(EqualSplit.class, factory.getStrategy(SplitMethod.EQUAL).getClass());

        SplitStrategy equal = factory.getStrategy(SplitMethod.EQUAL);
        SplitStrategy percent = factory.getStrategy(SplitMethod.PERCENTAGE);
        SplitStrategy exact = factory.getStrategy(SplitMethod.EXACT_AMOUNT);
        for (long total = 1; total <= 10_000; total += 37) {
            assertEquals(total, sum(equal.computeShares(total, in("a", 0, "b", 0, "c", 0))));
            assertEquals(total, sum(percent.computeShares(total, in("a", 12.5, "b", 37.5, "c", 50))));
            long first = total / 3;
            assertEquals(total, sum(exact.computeShares(total,
                    in("a", first / 100.0, "b", (total - first) / 100.0))));
        }
    }

    private static Expense expense(String date, long cents, Category category) {
        Expense e = new Expense();
        e.setCreatedDate(date);
        e.setAmountCents(cents);
        e.setCategory(category);
        return e;
    }
}
