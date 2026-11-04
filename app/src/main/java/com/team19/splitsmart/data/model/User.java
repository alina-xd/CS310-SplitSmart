package com.team19.splitsmart.data.model;

import com.google.firebase.firestore.IgnoreExtraProperties;

/**
 * A registered user, stored at users/{uid}. The uid is the Firebase Authentication user id.
 * uscEmail is always stored in lower case so duplicate checks ignore letter case (FR-1.2).
 * The setters exist for Firestore; app code must not change uscEmail or uscId after registration.
 */
@IgnoreExtraProperties
public class User {

    private String uid;
    private String name;
    private String uscEmail;
    private String uscId;

    /** Required by Firestore. */
    public User() {
    }

    public User(String uid, String name, String uscEmail, String uscId) {
        this.uid = uid;
        this.name = name;
        this.uscEmail = uscEmail;
        this.uscId = uscId;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUscEmail() {
        return uscEmail;
    }

    public void setUscEmail(String uscEmail) {
        this.uscEmail = uscEmail;
    }

    public String getUscId() {
        return uscId;
    }

    public void setUscId(String uscId) {
        this.uscId = uscId;
    }
}
