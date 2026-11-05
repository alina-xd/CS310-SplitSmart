package com.team19.splitsmart.service;

import static org.junit.Assert.*;

import com.team19.splitsmart.data.model.*;
import com.team19.splitsmart.data.repository.*;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.DateUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.*;

public class BalanceAndSettlementTest {
    private Ledger ledger;
    private BalanceService balances;
    private SettlementService settlements;
    private Group group;

    @Before
    public void setUp() {
        ledger = new Ledger();
        group = new Group("Trip", "", "A");
        group.setGroupId("trip");
        group.addMember("B");
        group.addMember("C");
        group.addMember("D");
        balances = new BalanceService(ledger);
        settlements =
                new SettlementService(
                        ledger,
                        balances,
                        new GroupRepository(null) {
                            @Override
                            public void findById(String id, Callback<Group> cb) {
                                cb.onSuccess("trip".equals(id) ? group : null);
                            }
                        });
    }

    @Test
    public void fixtureBalancesForEveryMember() {
        fixture();
        assertSummary("A", 866, 0, "B", 200L, "C", 666L);
        assertSummary("B", 0, 533, "A", -200L, "C", -333L);
        assertSummary("C", 333, 666, "A", -666L, "B", 333L);
        assertSummary("D", 0, 0);
    }

    @Test
    public void reciprocalDebtsCancelWithinOnePair() {
        ledger.add(expense("e1", "B", 1, "A", 1000L, "B", 1000L));
        ledger.add(expense("e2", "B", 2, "A", 500L, "B", 500L));
        ledger.add(expense("e3", "A", 3, "A", 400L, "B", 400L));
        assertSummary("A", 0, 1100, "B", -1100L);
    }

    @Test
    public void payAndReceiveAreNeverNetted() {
        ledger.add(expense("e1", "A", 1, "A", 1000L, "B", 1000L, "C", 500L));
        ledger.add(expense("e2", "D", 2, "A", 400L, "D", 400L));
        assertSummary("A", 1500, 400, "B", 1000L, "C", 500L, "D", -400L);
    }

    @Test
    public void debtsAreNotTransferredThroughThirdParty() {
        ledger.add(expense("e1", "B", 1, "A", 1000L, "B", 1000L));
        ledger.add(expense("e2", "C", 2, "B", 1000L, "C", 1000L));
        assertEquals(0L, pair("A", "C"));
        assertEquals(-1000L, pair("A", "B"));
        assertEquals(-1000L, pair("B", "C"));
    }

    @Test
    public void otherGroupsDoNotChangeTrip() {
        fixture();
        Expense rent = expense("rent", "B", 1, "A", 50000L, "B", 50000L, "C", 50000L);
        rent.setGroupId("home");
        ledger.add(rent);
        assertEquals(200L, pair("A", "B"));
        assertEquals(
                50000L, BalanceService.compute("home", "A", ledger.all()).getTotalToPayCents());
    }

    @Test
    public void zeroSharesAndPayerOwnShareDoNotCreateDebt() {
        ledger.add(expense("e", "A", 1, "A", 1000L, "B", 0L));
        assertSummary("A", 0, 0);
        assertSummary("B", 0, 0);
        assertEquals(0L, pair("A", "A"));
    }

    @Test
    public void maximumHasCorrectDirection() {
        fixture();
        Result<Long> r = new Result<>();
        settlements.getMaxAllowed("trip", "B", "A", r);
        assertEquals(Long.valueOf(200), r.value());
        settlements.getMaxAllowed("trip", "A", "B", r);
        assertEquals(Long.valueOf(0), r.value());
    }

    @Test
    public void payerCanRecordPartialPaymentAndReceiveDateAndSequence() {
        debt1100();
        Settlement s = settlement(null, "A", "B", "A", 500, 99);
        Result<Void> r = new Result<>();
        settlements.createSettlement(s, r);
        r.value();
        assertEquals(-600, pair("A", "B"));
        assertEquals(DateUtil.today(), s.getCreatedDate());
        assertEquals(2L, s.getSequenceNo());
        assertNotNull(s.getId());
    }

    @Test
    public void recipientCanRecordFullRepayment() {
        debt1100();
        Settlement s = settlement(null, "A", "B", "B", 1100, 0);
        Result<Void> r = new Result<>();
        settlements.createSettlement(s, r);
        r.value();
        assertSummary("A", 0, 0);
        assertSummary("B", 0, 0);
    }

    @Test
    public void zeroAndNegativeAreRejectedWithoutWrites() {
        debt1100();
        for (long cents : new long[] {0, -1})
            rejectCreate(settlement(null, "A", "B", "A", cents, 0), "amount");
        assertEquals(1, ledger.rows.size());
    }

    @Test
    public void overpaymentShowsMaximum() {
        debt1100();
        Result<Void> r = new Result<>();
        settlements.createSettlement(settlement(null, "A", "B", "A", 1101, 0), r);
        assertEquals("They only owe $11.00", r.error.getMessage());
        assertEquals(1, ledger.rows.size());
    }

    @Test
    public void wrongDirectionAndNoDebtRejected() {
        debt1100();
        rejectCreate(settlement(null, "B", "A", "A", 1, 0), "amount");
        rejectCreate(settlement(null, "C", "D", "C", 1, 0), "amount");
    }

    @Test
    public void identicalOrMissingPartiesRejected() {
        rejectCreate(settlement(null, "A", "A", "A", 1, 0), "member");
        rejectCreate(settlement(null, null, "A", "A", 1, 0), "member");
    }

    @Test
    public void outsiderPartiesAndUninvolvedCreatorRejected() {
        rejectCreate(settlement(null, "A", "outsider", "A", 1, 0), "member");
        rejectCreate(settlement(null, "A", "B", "C", 1, 0), "permission");
    }

    @Test
    public void missingGroupFails() {
        Settlement s = settlement(null, "A", "B", "A", 1, 0);
        s.setGroupId("missing");
        Result<Void> r = new Result<>();
        settlements.createSettlement(s, r);
        assertTrue(r.error instanceof IllegalStateException);
    }

    @Test
    public void createCannotOverwriteExistingId() {
        rejectCreate(settlement("existing", "A", "B", "A", 1, 0), "permission");
    }

    @Test
    public void editingRestoresOwnAmountAndPreservesMetadata() {
        debt1100();
        ledger.add(settlement("s", "A", "B", "A", 500, 2));
        Settlement edited = settlement("s", "A", "B", "forged", 1000, 99);
        edited.setCreatedDate("2099-01-01");
        Result<Void> r = new Result<>();
        settlements.updateSettlement(edited, "A", r);
        r.value();
        assertEquals(-100L, pair("A", "B"));
        assertEquals("2026-10-01", edited.getCreatedDate());
        assertEquals(2L, edited.getSequenceNo());
        assertEquals("A", edited.getCreatorId());
    }

    @Test
    public void editLimitUsesUnclampedBalanceAfterLaterOppositeExpense() {
        debt1100();
        ledger.add(settlement("s", "A", "B", "A", 500, 2));
        ledger.add(expense("reverse", "A", 3, "A", 800L, "B", 800L)); // A now receives 200
        Result<Void> r = new Result<>();
        settlements.updateSettlement(settlement("s", "A", "B", "A", 400, 2), "A", r);
        assertEquals("They only owe $3.00", r.error.getMessage());
    }

    @Test
    public void nonCreatorCannotEditOrDeleteEvenWithForgedCreator() {
        debt1100();
        ledger.add(settlement("s", "A", "B", "A", 500, 2));
        Settlement forged = settlement("s", "A", "B", "B", 1, 0);
        Result<Void> r = new Result<>();
        settlements.updateSettlement(forged, "B", r);
        r.field("permission");
        settlements.deleteSettlement(forged, "B", r);
        r.field("permission");
        assertEquals(2, ledger.rows.size());
    }

    @Test
    public void editingCannotChangeParties() {
        ledger.add(settlement("s", "A", "B", "A", 500, 2));
        Result<Void> r = new Result<>();
        settlements.updateSettlement(settlement("s", "A", "C", "A", 1, 2), "A", r);
        r.field("member");
    }

    @Test
    public void deletedRecordCannotBeEditedOrDeletedAgain() {
        Settlement s = settlement("missing", "A", "B", "A", 1, 1);
        Result<Void> r = new Result<>();
        settlements.updateSettlement(s, "A", r);
        assertTrue(r.error instanceof IllegalStateException);
        settlements.deleteSettlement(s, "A", r);
        assertTrue(r.error instanceof IllegalStateException);
    }

    @Test
    public void locksUseSequenceNotDateAndOnlyRelatedPayerParticipantPairs() {
        fixture();
        assertTrue(locked("dinner"));
        assertTrue(locked("taxi"));
        assertFalse(locked("movie"));
        ledger.rows.get("payment").setCreatedDate("2000-01-01");
        assertTrue(locked("dinner"));
    }

    @Test
    public void locksWorkInEitherSettlementDirection() {
        ledger.add(expense("e", "A", 1, "A", 500L, "B", 500L));
        ledger.add(settlement("s", "A", "B", "A", 100, 2));
        assertTrue(locked("e"));
    }

    @Test
    public void earlierOrSameSequenceSettlementDoesNotLock() {
        ledger.add(expense("e", "A", 2, "A", 500L, "B", 500L));
        ledger.add(settlement("s", "B", "A", "B", 100, 1));
        assertFalse(locked("e"));
        ledger.rows.get("s").setSequenceNo(2);
        assertFalse(locked("e"));
    }

    @Test
    public void zeroShareOrSettlementBetweenTwoNonPayersDoesNotLock() {
        ledger.add(expense("e", "A", 1, "A", 500L, "B", 0L, "C", 500L));
        ledger.add(settlement("s", "B", "A", "B", 100, 2));
        assertFalse(locked("e"));
        ledger.add(settlement("s2", "C", "B", "C", 100, 3));
        assertFalse(locked("e"));
    }

    @Test
    public void deletingSettlementRestoresDebtAndUnlocksExpenses() {
        fixture();
        Result<Void> r = new Result<>();
        settlements.deleteSettlement((Settlement) ledger.rows.get("payment"), "B", r);
        r.value();
        assertEquals(500L, pair("A", "B"));
        assertFalse(locked("dinner"));
        assertFalse(locked("taxi"));
    }

    @Test
    public void anotherCoveringSettlementKeepsLock() {
        fixture();
        ledger.add(settlement("another", "B", "A", "A", 1, 5));
        Result<Void> r = new Result<>();
        settlements.deleteSettlement((Settlement) ledger.rows.get("payment"), "B", r);
        r.value();
        assertTrue(locked("dinner"));
    }

    @Test
    public void repositoryFailurePropagatesWithoutWrite() {
        ledger.failure = new IllegalStateException("offline");
        Result<BalanceSummary> b = new Result<>();
        balances.getSummary("trip", "A", b);
        assertSame(ledger.failure, b.error);
        Result<Boolean> lock = new Result<>();
        settlements.isLocked(expense("e", "A", 1, "A", 1L, "B", 1L), lock);
        assertSame(ledger.failure, lock.error);
        Result<Void> create = new Result<>();
        settlements.createSettlement(settlement(null, "A", "B", "A", 1, 0), create);
        assertSame(ledger.failure, create.error);
        assertTrue(ledger.rows.isEmpty());
    }

    @Test
    public void balanceOverflowFailsInsteadOfWrapping() {
        ledger.add(expense("e1", "A", 1, "B", Long.MAX_VALUE));
        ledger.add(expense("e2", "A", 2, "B", 1L));
        Result<BalanceSummary> r = new Result<>();
        balances.getSummary("trip", "A", r);
        r.field("amount");
    }

    @Test
    public void summaryDefensivelyCopiesPairs() {
        List<PairBalance> pairs = new ArrayList<>();
        pairs.add(new PairBalance("B", 100));
        BalanceSummary summary = new BalanceSummary(0, 100, pairs);
        pairs.clear();
        assertEquals(1, summary.getPairBalances().size());
    }

    @Test
    public void balanceIsAntisymmetricForEveryPair() {
        fixture();
        for (String a : group.getMemberIds())
            for (String b : group.getMemberIds()) assertEquals(pair(a, b), -pair(b, a));
    }

    private void fixture() {
        ledger.add(expense("dinner", "A", 1, "A", 1000L, "B", 1000L, "C", 1000L));
        ledger.add(expense("taxi", "B", 2, "A", 500L, "B", 1500L));
        ledger.add(expense("movie", "C", 3, "A", 334L, "B", 333L, "C", 333L));
        ledger.add(settlement("payment", "B", "A", "B", 300, 4));
    }

    private void debt1100() {
        ledger.add(expense("debt", "B", 1, "A", 1100L, "B", 1100L));
    }

    private long pair(String a, String b) {
        Result<Long> r = new Result<>();
        balances.getPairBalance("trip", a, b, r);
        return r.value();
    }

    private boolean locked(String id) {
        Result<Boolean> r = new Result<>();
        settlements.isLocked((Expense) ledger.rows.get(id), r);
        return r.value();
    }

    private void rejectCreate(Settlement s, String field) {
        Result<Void> r = new Result<>();
        settlements.createSettlement(s, r);
        r.field(field);
    }

    private void assertSummary(String uid, long receive, long pay, Object... pairs) {
        Result<BalanceSummary> r = new Result<>();
        balances.getSummary("trip", uid, r);
        BalanceSummary s = r.value();
        assertEquals(receive, s.getTotalToReceiveCents());
        assertEquals(pay, s.getTotalToPayCents());
        Map<String, Long> actual = new HashMap<>();
        for (PairBalance p : s.getPairBalances())
            actual.put(p.getOtherUserId(), p.getAmountCents());
        Map<String, Long> expected = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            expected.put((String) pairs[i], (Long) pairs[i + 1]);
        assertEquals(expected, actual);
    }

    static Expense expense(String id, String payer, long seq, Object... shares) {
        Expense e = new Expense();
        e.setId(id);
        e.setGroupId("trip");
        e.setCreatorId(payer);
        e.setPayerId(payer);
        e.setSequenceNo(seq);
        e.setTitle(id);
        e.setCreatedDate("2026-10-01");
        Map<String, Long> map = new LinkedHashMap<>();
        long total = 0;
        for (int i = 0; i < shares.length; i += 2) {
            map.put((String) shares[i], (Long) shares[i + 1]);
            total += (Long) shares[i + 1];
        }
        e.setShares(map);
        e.setAmountCents(total);
        return e;
    }

    static Settlement settlement(
            String id, String payer, String recipient, String creator, long amount, long seq) {
        Settlement s = new Settlement();
        s.setId(id);
        s.setGroupId("trip");
        s.setPayerId(payer);
        s.setRecipientId(recipient);
        s.setCreatorId(creator);
        s.setAmountCents(amount);
        s.setSequenceNo(seq);
        s.setCreatedDate("2026-10-01");
        return s;
    }

    static class Result<T> implements Callback<T> {
        T result;
        Exception error;
        boolean called;

        public void onSuccess(T result) {
            this.result = result;
            error = null;
            called = true;
        }

        public void onFailure(Exception e) {
            error = e;
            called = true;
        }

        T value() {
            assertTrue("callback not called", called);
            if (error != null) throw new AssertionError(error);
            return result;
        }

        void field(String name) {
            assertTrue(String.valueOf(error), error instanceof ValidationException);
            assertEquals(name, ((ValidationException) error).getField());
        }
    }

    static class Ledger extends TransactionRepository {
        final Map<String, Transaction> rows = new LinkedHashMap<>();
        Exception failure;

        Ledger() {
            super(null);
        }

        void add(Transaction t) {
            rows.put(t.getId(), t);
        }

        List<Transaction> all() {
            return new ArrayList<>(rows.values());
        }

        @Override
        public void findByGroup(String group, Callback<List<Transaction>> cb) {
            if (failure != null) cb.onFailure(failure);
            else cb.onSuccess(all());
        }

        @Override
        public void findById(String group, String id, Callback<Transaction> cb) {
            if (failure != null) cb.onFailure(failure);
            else cb.onSuccess(rows.get(id));
        }

        @Override
        public void save(Transaction t, Callback<Transaction> cb) {
            t.setId("saved-" + rows.size());
            add(t);
            cb.onSuccess(t);
        }

        @Override
        public void update(Transaction t, Callback<Void> cb) {
            add(t);
            cb.onSuccess(null);
        }

        @Override
        public void delete(Transaction t, Callback<Void> cb) {
            rows.remove(t.getId());
            cb.onSuccess(null);
        }

        @Override
        public void nextSequenceNo(String group, Callback<Long> cb) {
            long seq = 0;
            for (Transaction t : rows.values()) seq = Math.max(seq, t.getSequenceNo());
            cb.onSuccess(seq + 1);
        }
    }
}
