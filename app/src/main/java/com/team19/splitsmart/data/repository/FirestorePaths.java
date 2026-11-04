package com.team19.splitsmart.data.repository;

/**
 * Firestore layout:
 *   users/{uid}                              User
 *   groups/{groupId}                         Group (+ seqCounter field)
 *   groups/{groupId}/transactions/{id}       Expense or Settlement, told apart by "type"
 */
public final class FirestorePaths {

    public static final String USERS = "users";
    public static final String GROUPS = "groups";
    public static final String TRANSACTIONS = "transactions";
    public static final String GROUP_SEQ_COUNTER = "seqCounter";
    public static final String TYPE_FIELD = "type";

    private FirestorePaths() {
    }
}
