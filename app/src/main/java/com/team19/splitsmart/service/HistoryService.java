package com.team19.splitsmart.service;

import com.google.firebase.firestore.ListenerRegistration;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.repository.TransactionListener;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.util.Callback;

import java.util.List;

/** C10 History & Summary Service. Owner: Part E (getHistory). getMonthlySummary is done (Part C: MonthlySummaryCalculator). */
public class HistoryService {

    private final TransactionRepository txRepo;

    public HistoryService() {
        this(new TransactionRepository());
    }

    public HistoryService(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    public void getHistory(String groupId, HistoryFilter filter, Callback<List<Transaction>> callback) {
        // TODO (Part E): filter with HistoryFilter.matches, sort by sequenceNo.
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part E)"));
    }

    /** month is "YYYY-MM". Settlements are excluded (FR-5.2). */
    public void getMonthlySummary(String groupId, String month, Callback<MonthlySummary> callback) {
        // Done: Part C writes MonthlySummaryCalculator.compute and never edits this file.
        txRepo.findByGroup(groupId, new Callback<List<Transaction>>() {
            @Override
            public void onSuccess(List<Transaction> transactions) {
                try {
                    callback.onSuccess(MonthlySummaryCalculator.compute(transactions, month));
                } catch (RuntimeException e) {
                    callback.onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    /** One transaction (Expense or Settlement), or null if it doesn't exist. Shared read (done). */
    public void getTransaction(String groupId, String transactionId, Callback<Transaction> callback) {
        txRepo.findById(groupId, transactionId, callback);
    }

    /** Registers a screen for change callbacks. Remove the registration in onStop. */
    public ListenerRegistration listen(String groupId, TransactionListener listener) {
        return txRepo.addListener(groupId, listener);
    }
}
