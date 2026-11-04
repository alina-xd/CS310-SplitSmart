package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.util.Callback;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class HistoryServiceTest {
    private static class Repository extends TransactionRepository {
        String requestedGroup;
        Callback<List<Transaction>> pending;
        Repository() { super(null); } // No Firebase initialization/network in unit tests.
        @Override public void findByGroup(String group, Callback<List<Transaction>> callback) {
            requestedGroup = group;
            pending = callback;
        }
    }
    private static class Result implements Callback<List<Transaction>> {
        List<Transaction> value;
        Exception error;
        int calls;
        @Override public void onSuccess(List<Transaction> value) { this.value = value; calls++; }
        @Override public void onFailure(Exception error) { this.error = error; calls++; }
    }
    @Test public void delegatesGroupAndWaitsForAsyncResult() {
        Repository repository = new Repository();
        Result result = new Result();
        new HistoryService(repository).getHistory("trip", new HistoryFilter(), result);
        assertEquals("trip", repository.requestedGroup);
        assertEquals(0, result.calls);
        repository.pending.onSuccess(Collections.emptyList());
        assertEquals(1, result.calls); assertTrue(result.value.isEmpty());
    }
    @Test public void filtersAndSortsRepositoryResultsWithoutMutatingThem() {
        Expense dinner = new Expense(); dinner.setTitle("Dinner"); dinner.setSequenceNo(1);
        Expense movie = new Expense(); movie.setTitle("Movie"); movie.setSequenceNo(3);
        Settlement settlement = new Settlement(); settlement.setSequenceNo(4);
        List<Transaction> input = Collections.unmodifiableList(Arrays.asList(dinner, settlement, movie));
        Repository repository = new Repository(); Result result = new Result();
        HistoryFilter filter = new HistoryFilter(); filter.setType(Transaction.TYPE_EXPENSE);
        new HistoryService(repository).getHistory("trip", filter, result);
        repository.pending.onSuccess(input);
        assertEquals(Arrays.asList(movie, dinner), result.value);
        assertEquals(Arrays.asList(dinner, settlement, movie), input);
        assertNull(result.error); assertEquals(1, result.calls);
    }
    @Test public void nullFilterUsesNewestFirstAndAllTransactions() {
        Expense first = new Expense(); first.setSequenceNo(1);
        Settlement second = new Settlement(); second.setSequenceNo(2);
        Repository repository = new Repository(); Result result = new Result();
        new HistoryService(repository).getHistory("trip", null, result);
        repository.pending.onSuccess(Arrays.asList(first, second));
        assertEquals(Arrays.asList(second, first), result.value);
    }
    @Test public void supportsOldestFirst() {
        Expense first = new Expense(); first.setSequenceNo(1);
        Settlement second = new Settlement(); second.setSequenceNo(2);
        Repository repository = new Repository(); Result result = new Result();
        HistoryFilter filter = new HistoryFilter(); filter.setNewestFirst(false);
        new HistoryService(repository).getHistory("trip", filter, result);
        repository.pending.onSuccess(Arrays.asList(second, first));
        assertEquals(Arrays.asList(first, second), result.value);
    }
    @Test public void forwardsRepositoryErrorUnchangedExactlyOnce() {
        Repository repository = new Repository(); Result result = new Result();
        new HistoryService(repository).getHistory("trip", null, result);
        Exception error = new IllegalStateException("offline");
        repository.pending.onFailure(error);
        assertSame(error, result.error); assertNull(result.value); assertEquals(1, result.calls);
    }
}
