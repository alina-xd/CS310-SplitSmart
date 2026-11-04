package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.SplitMethod;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.service.split.SplitStrategyFactory;
import com.team19.splitsmart.util.Callback;

import java.util.Map;

/** C7 Expense Service. Owners: Part B (create/edit/delete), Part C (previewShares). */
public class ExpenseService {

    private final TransactionRepository txRepo;
    private final SettlementService settlementService;

    public ExpenseService() {
        this(new TransactionRepository(), new SettlementService());
    }

    public ExpenseService(TransactionRepository txRepo, SettlementService settlementService) {
        this.txRepo = txRepo;
        this.settlementService = settlementService;
    }

    /**
     * Shares in cents for each participant. inputs is keyed by participant uid (use a
     * LinkedHashMap to keep order); values are ignored for EQUAL, dollars for EXACT_AMOUNT and
     * percentages for PERCENTAGE. Pure calculation, no Firebase call.
     */
    public Map<String, Long> previewShares(long totalCents, SplitMethod method, Map<String, Double> inputs) {
        // Part C implements the strategies; this delegation is done, so Part B never edits it.
        return new SplitStrategyFactory().getStrategy(method).computeShares(totalCents, inputs);
    }

    public void createExpense(Expense expense, Callback<Void> callback) {
        // TODO (Part B): validate, nextSequenceNo, set createdDate = DateUtil.today(), save.
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part B)"));
    }

    public void updateExpense(Expense expense, String editorId, Callback<Void> callback) {
        // TODO (Part B): creator only, not locked, keep createdDate and sequenceNo.
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part B)"));
    }

    public void deleteExpense(Expense expense, String editorId, Callback<Void> callback) {
        // TODO (Part B): creator only, not locked.
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part B)"));
    }

    private void validate(Expense expense) throws ValidationException {
        // TODO (Part B)
        throw new UnsupportedOperationException("Not implemented yet (Part B)");
    }
}
