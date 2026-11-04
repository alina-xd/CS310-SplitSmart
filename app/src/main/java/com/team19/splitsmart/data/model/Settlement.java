package com.team19.splitsmart.data.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.List;

/** A payment made outside the app from payerId to recipientId (UC-4). */
@IgnoreExtraProperties
public class Settlement extends Transaction {

    private String payerId;
    private String recipientId;

    /** Required by Firestore. */
    public Settlement() {
        super(TYPE_SETTLEMENT);
    }

    @Exclude
    @Override
    public List<String> getInvolvedUserIds() {
        List<String> ids = new ArrayList<>();
        ids.add(payerId);
        ids.add(recipientId);
        return ids;
    }

    public String getPayerId() {
        return payerId;
    }

    public void setPayerId(String payerId) {
        this.payerId = payerId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }
}
