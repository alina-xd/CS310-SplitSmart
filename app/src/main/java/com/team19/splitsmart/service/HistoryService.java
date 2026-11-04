package com.team19.splitsmart.service;

import com.google.firebase.firestore.ListenerRegistration;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.repository.TransactionListener;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.util.Callback;

import java.util.List;

/** C10 History & Summary Service. Owner: Feature 5. */
public class HistoryService {

    private final TransactionRepository txRepo;

    public HistoryService() {
        this(new TransactionRepository());
    }

    public HistoryService(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    public void getHistory(String groupId, HistoryFilter filter, Callback<List<Transaction>> callback) {
        // TODO (Feature 5): filter with HistoryFilter.matches, sort by sequenceNo.
        throw new UnsupportedOperationException("Not implemented yet (Feature 5)");
    }

    /** month is "YYYY-MM". Settlements are excluded (FR-5.2). */
    public void getMonthlySummary(String groupId, String month, Callback<MonthlySummary> callback) {
        // TODO (Feature 5)
        throw new UnsupportedOperationException("Not implemented yet (Feature 5)");
    }

    /** Registers a screen for change callbacks. Remove the registration in onStop. */
    public ListenerRegistration listen(String groupId, TransactionListener listener) {
        return txRepo.addListener(groupId, listener);
    }
}
