package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.util.Callback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-group pairwise balances. Positive means the other member owes the user. */
public class BalanceService {
    private final TransactionRepository txRepo;

    public BalanceService() {
        this(new TransactionRepository());
    }

    public BalanceService(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    public void getSummary(String groupId, String userId, Callback<BalanceSummary> callback) {
        txRepo.findByGroup(
                groupId,
                new Callback<List<Transaction>>() {
                    @Override
                    public void onSuccess(List<Transaction> transactions) {
                        BalanceSummary summary;
                        try {
                            summary = compute(groupId, userId, transactions);
                        } catch (ArithmeticException e) {
                            callback.onFailure(
                                    new ValidationException(
                                            "amount", "The combined balance is too large"));
                            return;
                        }
                        callback.onSuccess(summary);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        callback.onFailure(e);
                    }
                });
    }

    /** Positive if otherId owes userId, negative if userId owes otherId. */
    public void getPairBalance(
            String groupId, String userId, String otherId, Callback<Long> callback) {
        getSummary(
                groupId,
                userId,
                new Callback<BalanceSummary>() {
                    @Override
                    public void onSuccess(BalanceSummary summary) {
                        for (PairBalance pair : summary.getPairBalances()) {
                            if (pair.getOtherUserId().equals(otherId)) {
                                callback.onSuccess(pair.getAmountCents());
                                return;
                            }
                        }
                        callback.onSuccess(0L);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        callback.onFailure(e);
                    }
                });
    }

    /** Pure calculation shared by tests and callers that already hold a transaction snapshot. */
    public static BalanceSummary compute(
            String groupId, String userId, List<Transaction> transactions) {
        Map<String, Long> pairs = new LinkedHashMap<>();
        for (Transaction t : transactions) {
            if (!groupId.equals(t.getGroupId())) continue;
            if (t instanceof Expense) applyExpense((Expense) t, userId, pairs);
            else if (t instanceof Settlement) applySettlement((Settlement) t, userId, pairs);
        }
        List<PairBalance> result = new ArrayList<>();
        long pay = 0, receive = 0;
        for (Map.Entry<String, Long> entry : pairs.entrySet()) {
            long amount = entry.getValue();
            if (amount == 0) continue;
            result.add(new PairBalance(entry.getKey(), amount));
            if (amount > 0) receive = Math.addExact(receive, amount);
            else pay = Math.addExact(pay, Math.negateExact(amount));
        }
        return new BalanceSummary(pay, receive, result);
    }

    private static void applyExpense(Expense expense, String userId, Map<String, Long> pairs) {
        if (userId.equals(expense.getPayerId())) {
            for (Map.Entry<String, Long> share : expense.getShares().entrySet()) {
                if (!userId.equals(share.getKey())) add(pairs, share.getKey(), share.getValue());
            }
        } else if (expense.getShares().containsKey(userId)) {
            add(pairs, expense.getPayerId(), Math.negateExact(expense.getShare(userId)));
        }
    }

    private static void applySettlement(
            Settlement settlement, String userId, Map<String, Long> pairs) {
        if (userId.equals(settlement.getPayerId())) {
            add(pairs, settlement.getRecipientId(), settlement.getAmountCents());
        } else if (userId.equals(settlement.getRecipientId())) {
            add(pairs, settlement.getPayerId(), Math.negateExact(settlement.getAmountCents()));
        }
    }

    private static void add(Map<String, Long> pairs, String otherId, long amount) {
        pairs.put(otherId, Math.addExact(pairs.getOrDefault(otherId, 0L), amount));
    }
}
