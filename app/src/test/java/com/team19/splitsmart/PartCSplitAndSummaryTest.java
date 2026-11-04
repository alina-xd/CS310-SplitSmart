package com.team19.splitsmart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.service.MonthlySummary;
import com.team19.splitsmart.service.MonthlySummaryCalculator;
import com.team19.splitsmart.service.split.EqualSplit;
import com.team19.splitsmart.service.split.ExactAmountSplit;
import com.team19.splitsmart.service.split.PercentageSplit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Part C "Done when" checks from the README, plus rounding edge cases. */
public class PartCSplitAndSummaryTest {

    private static Map<String, Double> inputs(Object... uidValuePairs) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < uidValuePairs.length; i += 2) {
            Object v = uidValuePairs[i + 1];
            map.put((String) uidValuePairs[i], v == null ? null : ((Number) v).doubleValue());
        }
        return map;
    }

    private static long sum(Map<String, Long> shares) {
        long s = 0;
        for (long v : shares.values()) s += v;
        return s;
    }

    // ---- Equal ----

    @Test
    public void equal_30DollarsThreeWays_10Each() {
        Map<String, Long> s = new EqualSplit().computeShares(3000, inputs("a", 0, "b", 0, "c", 0));
        assertEquals(1000L, (long) s.get("a"));
        assertEquals(1000L, (long) s.get("b"));
        assertEquals(1000L, (long) s.get("c"));
    }

    @Test
    public void equal_10DollarsThreeWays_extraCentToFirst() {
        Map<String, Long> s = new EqualSplit().computeShares(1000, inputs("a", 0, "b", 0, "c", 0));
        assertEquals(334L, (long) s.get("a"));
        assertEquals(333L, (long) s.get("b"));
        assertEquals(333L, (long) s.get("c"));
    }

    @Test
    public void equal_alwaysAddsUp() {
        for (long total = 0; total <= 2000; total += 7) {
            for (int n = 1; n <= 7; n++) {
                Map<String, Double> in = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) in.put("u" + i, 0.0);
                assertEquals(total, sum(new EqualSplit().computeShares(total, in)));
            }
        }
    }

    @Test
    public void equal_noParticipants_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EqualSplit().computeShares(1000, new LinkedHashMap<>()));
    }

    // ---- Exact amount ----

    @Test
    public void exact_5_10_15_for30() {
        Map<String, Long> s = new ExactAmountSplit().computeShares(3000, inputs("a", 5, "b", 10, "c", 15));
        assertEquals(500L, (long) s.get("a"));
        assertEquals(1000L, (long) s.get("b"));
        assertEquals(1500L, (long) s.get("c"));
    }

    @Test
    public void exact_zeroShareAllowed() {
        Map<String, Long> s = new ExactAmountSplit().computeShares(3000, inputs("a", 0, "b", 30));
        assertEquals(0L, (long) s.get("a"));
        assertEquals(3000L, (long) s.get("b"));
    }

    @Test
    public void exact_centsDontDrift() {
        // 0.29 * 100 is 28.999... as a double; rounding must still give 29 cents.
        Map<String, Long> s = new ExactAmountSplit().computeShares(100, inputs("a", 0.29, "b", 0.71));
        assertEquals(29L, (long) s.get("a"));
        assertEquals(71L, (long) s.get("b"));
    }

    @Test
    public void exact_mismatch_rejectedWithMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ExactAmountSplit().computeShares(3000, inputs("a", 5, "b", 10, "c", 10)));
        assertEquals("Shares add up to $25.00 but the expense is $30.00", e.getMessage());
    }

    @Test
    public void exact_negativeOrMissing_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExactAmountSplit().computeShares(1000, inputs("a", -5, "b", 15)));
        assertThrows(IllegalArgumentException.class,
                () -> new ExactAmountSplit().computeShares(1000, inputs("a", null, "b", 10)));
    }

    // ---- Percentage ----

    @Test
    public void percent_20_30_50_of30() {
        Map<String, Long> s = new PercentageSplit().computeShares(3000, inputs("a", 20, "b", 30, "c", 50));
        assertEquals(600L, (long) s.get("a"));
        assertEquals(900L, (long) s.get("b"));
        assertEquals(1500L, (long) s.get("c"));
    }

    @Test
    public void percent_zeroAllowed_andZeroNeverGetsExtraCent() {
        Map<String, Long> s = new PercentageSplit().computeShares(1000,
                inputs("zero", 0, "a", 33.33, "b", 33.33, "c", 33.34));
        assertEquals(0L, (long) s.get("zero"));
        assertEquals(1000, sum(s));
    }

    @Test
    public void percent_thirdsOf10_addUp() {
        Map<String, Long> s = new PercentageSplit().computeShares(1000,
                inputs("a", 100.0 / 3, "b", 100.0 / 3, "c", 100.0 / 3));
        assertEquals(1000, sum(s));
        for (long v : s.values()) assertTrue(v == 333 || v == 334);
    }

    @Test
    public void percent_alwaysAddsUp() {
        double[][] cases = {{12.5, 37.5, 50}, {1, 1, 98}, {33.3, 33.3, 33.4}, {0, 100}, {99.99, 0.01}};
        for (long total = 1; total <= 5000; total += 13) {
            for (double[] pcts : cases) {
                Map<String, Double> in = new LinkedHashMap<>();
                for (int i = 0; i < pcts.length; i++) in.put("u" + i, pcts[i]);
                assertEquals(total, sum(new PercentageSplit().computeShares(total, in)));
            }
        }
    }

    @Test
    public void percent_not100_rejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new PercentageSplit().computeShares(3000, inputs("a", 20, "b", 30, "c", 40)));
        assertEquals("Percentages add up to 90% but must add up to 100%", e.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> new PercentageSplit().computeShares(3000, inputs("a", -10, "b", 110)));
    }

    // ---- Monthly summary (README Trip test data) ----

    private static Expense expense(String date, long cents, Category category) {
        Expense e = new Expense();
        e.setCreatedDate(date);
        e.setAmountCents(cents);
        e.setCategory(category);
        return e;
    }

    private static List<Transaction> tripGroup() {
        List<Transaction> list = new ArrayList<>();
        list.add(expense("2026-10-12", 3000, Category.FOOD));
        list.add(expense("2026-10-20", 2000, Category.TRANSPORTATION));
        list.add(expense("2026-11-02", 1000, Category.ENTERTAINMENT));
        Settlement s = new Settlement();
        s.setCreatedDate("2026-11-02");
        s.setAmountCents(300);
        list.add(s);
        return list;
    }

    @Test
    public void summary_october() {
        MonthlySummary m = MonthlySummaryCalculator.compute(tripGroup(), "2026-10");
        assertEquals(2, m.getExpenseCount());
        assertEquals(5000, m.getTotalCents());
        assertEquals(3000L, (long) m.getCategoryTotals().get(Category.FOOD));
        assertEquals(2000L, (long) m.getCategoryTotals().get(Category.TRANSPORTATION));
        assertEquals(0L, (long) m.getCategoryTotals().get(Category.HOUSING));
        assertEquals(5, m.getCategoryTotals().size());
    }

    @Test
    public void summary_november_excludesSettlement() {
        MonthlySummary m = MonthlySummaryCalculator.compute(tripGroup(), "2026-11");
        assertEquals(1, m.getExpenseCount());
        assertEquals(1000, m.getTotalCents());
        assertEquals(1000L, (long) m.getCategoryTotals().get(Category.ENTERTAINMENT));
    }

    @Test
    public void summary_emptyMonth() {
        MonthlySummary m = MonthlySummaryCalculator.compute(tripGroup(), "2026-01");
        assertEquals(0, m.getExpenseCount());
        assertEquals(0, m.getTotalCents());
        assertEquals(0L, (long) m.getCategoryTotals().get(Category.OTHER));
    }
}
