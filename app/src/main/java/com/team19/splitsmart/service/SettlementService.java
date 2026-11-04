package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.util.Callback;

/** C8 Settlement Service. Owner: Feature 4. */
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
        // TODO (Feature 4): amount > 0, <= amount owed; nextSequenceNo; createdDate; save.
        throw new UnsupportedOperationException("Not implemented yet (Feature 4)");
    }

    public void updateSettlement(Settlement s, String editorId, Callback<Void> callback) {
        // TODO (Feature 4): creator only. When checking the limit, leave out this settlement's
        // original amount, otherwise editing $5 to $6 against $11 owed would fail.
        throw new UnsupportedOperationException("Not implemented yet (Feature 4)");
    }

    public void deleteSettlement(Settlement s, String editorId, Callback<Void> callback) {
        // TODO (Feature 4): creator only.
        throw new UnsupportedOperationException("Not implemented yet (Feature 4)");
    }

    public void getMaxAllowed(String groupId, String payerId, String recipientId, Callback<Long> callback) {
        // TODO (Feature 4)
        throw new UnsupportedOperationException("Not implemented yet (Feature 4)");
    }

    /**
     * True if a later settlement (higher sequenceNo) exists between the expense's payer and one
     * of its participants (FR-4.2). Computed each time, so deleting a settlement releases locks.
     */
    public void isLocked(Expense expense, Callback<Boolean> callback) {
        // TODO (Feature 4)
        throw new UnsupportedOperationException("Not implemented yet (Feature 4)");
    }
}
