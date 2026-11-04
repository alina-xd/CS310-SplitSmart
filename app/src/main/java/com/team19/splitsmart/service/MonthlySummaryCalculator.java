package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Transaction;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Monthly expense summary (FR-5.2). Pure logic with no Firebase, so it can be unit tested.
 * Only expenses count (settlements are excluded); an expense belongs to the month of its
 * createdDate. The category totals always add up to the total. Owner: Part C.
 */
public final class MonthlySummaryCalculator {

    private MonthlySummaryCalculator() {
    }

    /** month is "YYYY-MM". The result has an entry for all 5 categories (0 where there are none). */
    public static MonthlySummary compute(List<Transaction> transactions, String month) {
        Map<Category, Long> categoryTotals = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            categoryTotals.put(category, 0L);
        }
        int count = 0;
        long total = 0;
        if (transactions != null && month != null) {
            String prefix = month + "-";
            for (Transaction t : transactions) {
                if (!(t instanceof Expense) || t.getCreatedDate() == null
                        || !t.getCreatedDate().startsWith(prefix)) {
                    continue;
                }
                Expense expense = (Expense) t;
                Category category = expense.getCategory() == null ? Category.OTHER : expense.getCategory();
                categoryTotals.put(category, categoryTotals.get(category) + expense.getAmountCents());
                total += expense.getAmountCents();
                count++;
            }
        }
        return new MonthlySummary(month, count, total, categoryTotals);
    }
}
