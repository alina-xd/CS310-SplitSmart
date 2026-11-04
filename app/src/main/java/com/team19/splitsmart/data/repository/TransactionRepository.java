package com.team19.splitsmart.data.repository;

import android.util.Log;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.util.Callback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads and writes groups/{groupId}/transactions/{id}. Only the service layer should use this class.
 * Lists are always returned sorted by sequenceNo, oldest first.
 */
public class TransactionRepository {

    private static final String TAG = "TransactionRepository";

    private final FirebaseFirestore db;

    public TransactionRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public TransactionRepository(FirebaseFirestore db) {
        this.db = db;
    }

    private CollectionReference transactions(String groupId) {
        return db.collection(FirestorePaths.GROUPS).document(groupId)
                .collection(FirestorePaths.TRANSACTIONS);
    }

    /** Saves a new transaction and fills in its id. Set sequenceNo first (nextSequenceNo). */
    public void save(Transaction t, Callback<Transaction> callback) {
        DocumentReference ref = t.getId() == null
                ? transactions(t.getGroupId()).document()
                : transactions(t.getGroupId()).document(t.getId());
        t.setId(ref.getId());
        ref.set(t)
                .addOnSuccessListener(unused -> callback.onSuccess(t))
                .addOnFailureListener(callback::onFailure);
    }

    /** Overwrites an existing transaction. */
    public void update(Transaction t, Callback<Void> callback) {
        transactions(t.getGroupId()).document(t.getId()).set(t)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void delete(Transaction t, Callback<Void> callback) {
        transactions(t.getGroupId()).document(t.getId()).delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    /** Returns null through the callback if no such transaction exists. */
    public void findById(String groupId, String transactionId, Callback<Transaction> callback) {
        transactions(groupId).document(transactionId).get()
                .addOnSuccessListener(doc -> callback.onSuccess(doc.exists() ? fromSnapshot(doc) : null))
                .addOnFailureListener(callback::onFailure);
    }

    public void findByGroup(String groupId, Callback<List<Transaction>> callback) {
        transactions(groupId).get()
                .addOnSuccessListener(snap -> callback.onSuccess(toSortedList(snap)))
                .addOnFailureListener(callback::onFailure);
    }

    /** Atomically increments the group's seqCounter and returns the new value (FR-4.3). */
    public void nextSequenceNo(String groupId, Callback<Long> callback) {
        DocumentReference groupRef = db.collection(FirestorePaths.GROUPS).document(groupId);
        db.<Long>runTransaction(tx -> {
                    DocumentSnapshot group = tx.get(groupRef);
                    Long current = group.getLong(FirestorePaths.GROUP_SEQ_COUNTER);
                    long next = (current == null ? 0 : current) + 1;
                    tx.update(groupRef, FirestorePaths.GROUP_SEQ_COUNTER, next);
                    return next;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onFailure);
    }

    /**
     * Calls the listener now and after every change to the group's transactions, including the
     * acting user's own writes. Call remove() on the result when the screen stops (onStop).
     */
    public ListenerRegistration addListener(String groupId, TransactionListener listener) {
        return transactions(groupId).addSnapshotListener((snap, error) -> {
            if (error != null) {
                Log.w(TAG, "Transaction listener failed", error);
                return;
            }
            if (snap != null) {
                listener.onTransactionsChanged(toSortedList(snap));
            }
        });
    }

    private static List<Transaction> toSortedList(QuerySnapshot snap) {
        List<Transaction> result = new ArrayList<>();
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Transaction t = fromSnapshot(doc);
            if (t != null) {
                result.add(t);
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(a.getSequenceNo(), b.getSequenceNo()));
        return result;
    }

    private static Transaction fromSnapshot(DocumentSnapshot doc) {
        String type = doc.getString(FirestorePaths.TYPE_FIELD);
        if (Transaction.TYPE_EXPENSE.equals(type)) {
            return doc.toObject(Expense.class);
        }
        if (Transaction.TYPE_SETTLEMENT.equals(type)) {
            return doc.toObject(Settlement.class);
        }
        Log.w(TAG, "Unknown transaction type in " + doc.getId());
        return null;
    }
}
