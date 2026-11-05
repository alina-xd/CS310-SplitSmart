package com.team19.splitsmart.service;

import static org.junit.Assert.*;

import com.team19.splitsmart.data.model.*;
import com.team19.splitsmart.data.repository.GroupRepository;
import com.team19.splitsmart.service.split.PercentageSplit;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.MoneyUtil;

import org.junit.Test;

import java.util.*;

/** Regressions found while integrating D with the other parts. */
public class IntegrationRegressionTest {
    @Test
    public void toleratedPercentSumStillConservesLargeTotal() {
        for (double value : new double[] {50.00009, 49.99991}) {
            Map<String, Double> inputs = new LinkedHashMap<>();
            inputs.put("a", 50.0);
            inputs.put("b", value);
            inputs.put("zero", 0.0);
            Map<String, Long> shares = new PercentageSplit().computeShares(1000000000L, inputs);
            assertEquals(1000000000L, shares.get("a") + shares.get("b"));
            assertEquals(Long.valueOf(0), shares.get("zero"));
        }
    }

    @Test
    public void percentageRoundingConservesCentsAcrossAmounts() {
        Map<String, Double> inputs = new LinkedHashMap<>();
        inputs.put("a", 33.3333);
        inputs.put("b", 33.3333);
        inputs.put("c", 33.3334);
        for (long total : new long[] {1, 2, 10, 999, 1000, 3000, 1000000000}) {
            Map<String, Long> shares = new PercentageSplit().computeShares(total, inputs);
            assertEquals(total, shares.values().stream().mapToLong(Long::longValue).sum());
            for (long share : shares.values()) assertTrue(share >= 0);
        }
    }

    @Test
    public void expensesRejectNegativeSharesAndUnrelatedCreators() {
        BalanceAndSettlementTest.Ledger ledger = new BalanceAndSettlementTest.Ledger();
        Group group = new Group("Trip", "", "A");
        group.setGroupId("trip");
        group.addMember("B");
        GroupRepository groups =
                new GroupRepository(null) {
                    @Override
                    public void findById(String id, Callback<Group> cb) {
                        cb.onSuccess(group);
                    }
                };
        SettlementService settlements =
                new SettlementService(ledger, new BalanceService(ledger), groups);
        ExpenseService service = new ExpenseService(ledger, groups, settlements);
        Expense e = BalanceAndSettlementTest.expense(null, "A", 0, "A", -100L, "B", 200L);
        e.setTitle("Test expense");
        BalanceAndSettlementTest.Result<Void> r = new BalanceAndSettlementTest.Result<>();
        service.createExpense(e, r);
        r.field("split");
        e.setShares(new LinkedHashMap<>());
        e.getShares().put("A", 50L);
        e.getShares().put("B", 50L);
        e.setCreatorId("outside");
        service.createExpense(e, r);
        r.field("permission");
        assertTrue(ledger.rows.isEmpty());
    }

    @Test
    public void expenseCreateCannotOverwriteSavedId() {
        BalanceAndSettlementTest.Ledger ledger = new BalanceAndSettlementTest.Ledger();
        ExpenseService service = new ExpenseService(ledger, new GroupRepository(null), null);
        BalanceAndSettlementTest.Result<Void> r = new BalanceAndSettlementTest.Result<>();
        service.createExpense(
                BalanceAndSettlementTest.expense("existing", "A", 1, "A", 50L, "B", 50L), r);
        r.field("permission");
    }

    @Test
    public void moneyParsingRejectsSubCentAndOverflowInputs() {
        assertEquals(334, MoneyUtil.parseToCents("3.34"));
        for (String bad : new String[] {"1.001", "abc", "99999999999999999999999"}) {
            try {
                MoneyUtil.parseToCents(bad);
                fail(bad);
            } catch (NumberFormatException | ArithmeticException expected) {
            }
        }
    }
}
