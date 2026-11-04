package com.team19.splitsmart.data.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.List;

/**
 * An expense or a settlement, stored at groups/{groupId}/transactions/{id}.
 * The "type" field tells TransactionRepository which subclass to read the document back into.
 * sequenceNo is a per-group counter that orders transactions saved on the same day (FR-4.3).
 */
@IgnoreExtraProperties
public abstract class Transaction {

    public static final String TYPE_EXPENSE = "EXPENSE";
    public static final String TYPE_SETTLEMENT = "SETTLEMENT";

    private String id;
    private String type;
    private String groupId;
    private String creatorId;
    private long amountCents;
    private String createdDate;
    private long sequenceNo;
    private String note;

    protected Transaction(String type) {
        this.type = type;
    }

    /** The users this transaction involves: payer and participants, or payer and recipient. */
    @Exclude
    public abstract List<String> getInvolvedUserIds();

    @Exclude
    public boolean isExpense() {
        return TYPE_EXPENSE.equals(type);
    }

    /** Case-sensitive keyword search (FR-5.1). */
    public boolean matchesKeyword(String keyword) {
        // TODO (Feature 5): match against the note here; Expense should also match its title.
        throw new UnsupportedOperationException("Not implemented yet (Feature 5)");
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(long amountCents) {
        this.amountCents = amountCents;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public long getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(long sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
