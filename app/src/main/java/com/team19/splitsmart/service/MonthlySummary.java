package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Category;

import java.util.EnumMap;
import java.util.Map;

/** Result of HistoryService.getMonthlySummary. month is "YYYY-MM". */
public class MonthlySummary {

    private final String month;
    private final int expenseCount;
    private final long totalCents;
    private final Map<Category, Long> categoryTotals;

    public MonthlySummary(String month, int expenseCount, long totalCents, Map<Category, Long> categoryTotals) {
        this.month = month;
        this.expenseCount = expenseCount;
        this.totalCents = totalCents;
        this.categoryTotals = categoryTotals == null ? new EnumMap<>(Category.class) : categoryTotals;
    }

    public String getMonth() {
        return month;
    }

    public int getExpenseCount() {
        return expenseCount;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public Map<Category, Long> getCategoryTotals() {
        return categoryTotals;
    }
}
