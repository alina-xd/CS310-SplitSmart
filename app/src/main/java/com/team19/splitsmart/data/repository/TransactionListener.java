package com.team19.splitsmart.data.repository;

import com.team19.splitsmart.data.model.Transaction;

import java.util.List;

/** Told whenever a group's transactions change, so a screen can redraw without a refresh (NFR-2). */
public interface TransactionListener {

    /** All of the group's transactions, sorted by sequenceNo (oldest first). */
    void onTransactionsChanged(List<Transaction> transactions);
}
