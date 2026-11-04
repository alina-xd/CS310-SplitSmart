package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Search and filter settings for the group history. A null field means "don't filter on this".
 * type is null, Transaction.TYPE_EXPENSE or Transaction.TYPE_SETTLEMENT.
 */
public class HistoryFilter {

    private String keyword;
    private String type;
    private Category category;
    private String memberId;
    private boolean newestFirst = true;

    public boolean matches(Transaction t) {
        if (t == null) return false;
        if (type != null && !type.isEmpty() && !type.equals(t.getType())) return false;
        if (category != null && (!(t instanceof Expense)
                || ((Expense) t).getCategory() != category)) return false;
        if (memberId != null && !memberId.isEmpty()
                && !t.getInvolvedUserIds().contains(memberId)) return false;
        return matchesKeyword(t);
    }

    /** Case-sensitive search of expense titles, expense notes and settlement notes (FR-5.1). */
    private boolean matchesKeyword(Transaction t) {
        if (keyword == null || keyword.isEmpty()) return true;
        if (t.getNote() != null && t.getNote().contains(keyword)) return true;
        return t instanceof Expense && ((Expense) t).getTitle() != null
                && ((Expense) t).getTitle().contains(keyword);
    }

    /** Shared by repository reads and live UI snapshots; never changes the input list. */
    public List<Transaction> apply(List<Transaction> transactions) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (matches(transaction)) result.add(transaction);
        }
        Comparator<Transaction> order = Comparator.comparingLong(Transaction::getSequenceNo);
        result.sort(newestFirst ? order.reversed() : order);
        return result;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public boolean isNewestFirst() {
        return newestFirst;
    }

    public void setNewestFirst(boolean newestFirst) {
        this.newestFirst = newestFirst;
    }
}
