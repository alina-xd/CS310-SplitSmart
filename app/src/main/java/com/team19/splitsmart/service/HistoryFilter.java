package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Transaction;

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
        // TODO (Part E): keyword (case-sensitive), type, category (expenses only), member.
        throw new UnsupportedOperationException("Not implemented yet (Part E)");
    }

    /** Case-sensitive search of expense titles, expense notes and settlement notes (FR-5.1). */
    private boolean matchesKeyword(Transaction t) {
        // TODO (Part E)
        throw new UnsupportedOperationException("Not implemented yet (Part E)");
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
