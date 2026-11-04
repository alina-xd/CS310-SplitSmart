package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.SplitMethod;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.util.Callback;

import java.util.Map;

/** C7 Expense Service. Owners: Feature 2 (create/edit/delete), Feature 3 (previewShares). */
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
        // TODO (Feature 3): SplitStrategyFactory.getStrategy(method).computeShares(totalCents, inputs)
        throw new UnsupportedOperationException("Not implemented yet (Feature 3)");
    }

    public void createExpense(Expense expense, Callback<Void> callback) {
        // TODO (Feature 2): validate, nextSequenceNo, set createdDate = DateUtil.today(), save.
        throw new UnsupportedOperationException("Not implemented yet (Feature 2)");
    }

    public void updateExpense(Expense expense, String editorId, Callback<Void> callback) {
        // TODO (Feature 2): creator only, not locked, keep createdDate and sequenceNo.
        throw new UnsupportedOperationException("Not implemented yet (Feature 2)");
    }

    public void deleteExpense(Expense expense, String editorId, Callback<Void> callback) {
        // TODO (Feature 2): creator only, not locked.
        throw new UnsupportedOperationException("Not implemented yet (Feature 2)");
    }

    private void validate(Expense expense) throws ValidationException {
        // TODO (Feature 2)
        throw new UnsupportedOperationException("Not implemented yet (Feature 2)");
    }
}
