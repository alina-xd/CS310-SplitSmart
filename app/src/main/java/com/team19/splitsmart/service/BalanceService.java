package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.util.Callback;

import java.util.Map;

/**
 * C9 Balance Service. Owner: Feature 4.
 * Sign convention used everywhere: a positive amount means the other member owes the user;
 * a negative amount means the user owes the other member.
 */
public class BalanceService {

    private final TransactionRepository txRepo;

    public BalanceService() {
        this(new TransactionRepository());
    }

    public BalanceService(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    public void getSummary(String groupId, String userId, Callback<BalanceSummary> callback) {
        // TODO (Feature 4)
        throw new UnsupportedOperationException("Not implemented yet (Feature 4)");
    }

    /** Positive if otherId owes userId, negative if userId owes otherId. */
    public void getPairBalance(String groupId, String userId, String otherId, Callback<Long> callback) {
        // TODO (Feature 4)
        throw new UnsupportedOperationException("Not implemented yet (Feature 4)");
    }

    private void applyExpense(Expense e, String userId, Map<String, Long> pairs) {
        // TODO (Feature 4)
        throw new UnsupportedOperationException("Not implemented yet (Feature 4)");
    }

    private void applySettlement(Settlement s, String userId, Map<String, Long> pairs) {
        // TODO (Feature 4)
        throw new UnsupportedOperationException("Not implemented yet (Feature 4)");
    }
}
