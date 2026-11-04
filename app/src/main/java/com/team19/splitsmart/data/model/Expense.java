package com.team19.splitsmart.data.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An expense. shares maps every participant's uid to their share in cents; the payer is always
 * one of the participants (FR-2.3), and the shares must add up to amountCents (FR-2.5).
 */
@IgnoreExtraProperties
public class Expense extends Transaction {

    private String title;
    private Category category = Category.OTHER;
    private String payerId;
    private SplitMethod splitMethod = SplitMethod.EQUAL;
    private Map<String, Long> shares = new LinkedHashMap<>();

    /** Required by Firestore. */
    public Expense() {
        super(TYPE_EXPENSE);
    }

    @Exclude
    @Override
    public List<String> getInvolvedUserIds() {
        List<String> ids = new ArrayList<>();
        if (payerId != null) {
            ids.add(payerId);
        }
        if (shares != null) {
            for (String uid : shares.keySet()) {
                if (!ids.contains(uid)) {
                    ids.add(uid);
                }
            }
        }
        return ids;
    }

    /** The user's share in cents, or 0 if they aren't a participant. */
    public long getShare(String userId) {
        if (shares == null) {
            return 0;
        }
        Long share = shares.get(userId);
        return share == null ? 0 : share;
    }

    public boolean sharesAddUp() {
        long sum = 0;
        if (shares != null) {
            for (Long share : shares.values()) {
                sum += share == null ? 0 : share;
            }
        }
        return sum == getAmountCents();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getPayerId() {
        return payerId;
    }

    public void setPayerId(String payerId) {
        this.payerId = payerId;
    }

    public SplitMethod getSplitMethod() {
        return splitMethod;
    }

    public void setSplitMethod(SplitMethod splitMethod) {
        this.splitMethod = splitMethod;
    }

    public Map<String, Long> getShares() {
        return shares;
    }

    public void setShares(Map<String, Long> shares) {
        this.shares = shares;
    }
}
