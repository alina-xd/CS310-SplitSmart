package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.util.Callback;

/** C8 Settlement Service. Owner: Part D. */
public class SettlementService {

    private final TransactionRepository txRepo;
    private final BalanceService balanceService;

    public SettlementService() {
        this(new TransactionRepository(), new BalanceService());
    }

    public SettlementService(TransactionRepository txRepo, BalanceService balanceService) {
        this.txRepo = txRepo;
        this.balanceService = balanceService;
    }

    public void createSettlement(Settlement s, Callback<Void> callback) {
        // TODO (Part D): amount > 0, <= amount owed; nextSequenceNo; createdDate; save.
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part D)"));
    }

    public void updateSettlement(Settlement s, String editorId, Callback<Void> callback) {
        // TODO (Part D): creator only. When checking the limit, leave out this settlement's
        // original amount, otherwise editing $5 to $6 against $11 owed would fail.
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part D)"));
    }

    public void deleteSettlement(Settlement s, String editorId, Callback<Void> callback) {
        // TODO (Part D): creator only.
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part D)"));
    }

    public void getMaxAllowed(String groupId, String payerId, String recipientId, Callback<Long> callback) {
        // TODO (Part D)
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part D)"));
    }

    /**
     * True if a later settlement (higher sequenceNo) exists between the expense's payer and one
     * of its participants (FR-4.2). Computed each time, so deleting a settlement releases locks.
     */
    public void isLocked(Expense expense, Callback<Boolean> callback) {
        // TODO (Part D): until this is done it answers "not locked", so Parts B and E can test.
        callback.onSuccess(false);
    }
}
