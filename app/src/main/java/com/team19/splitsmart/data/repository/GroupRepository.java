package com.team19.splitsmart.data.repository;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.util.Callback;

import java.util.ArrayList;
import java.util.List;

/** Reads and writes groups/{groupId}. Only the service layer should use this class. */
public class GroupRepository {

    private final FirebaseFirestore db;

    public GroupRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public GroupRepository(FirebaseFirestore db) {
        this.db = db;
    }

    private CollectionReference groups() {
        return db.collection(FirestorePaths.GROUPS);
    }

    /**
     * Creates the group if it has no groupId yet (and fills the id in), otherwise updates it.
     * Uses merge so the seqCounter field on the document is kept.
     */
    public void save(Group group, Callback<Group> callback) {
        DocumentReference ref = group.getGroupId() == null
                ? groups().document()
                : groups().document(group.getGroupId());
        group.setGroupId(ref.getId());
        ref.set(group, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess(group))
                .addOnFailureListener(callback::onFailure);
    }

    /** Returns null through the callback if no such group exists. */
    public void findById(String groupId, Callback<Group> callback) {
        groups().document(groupId).get()
                .addOnSuccessListener(doc -> callback.onSuccess(doc.exists() ? doc.toObject(Group.class) : null))
                .addOnFailureListener(callback::onFailure);
    }

    /** All groups the user belongs to. */
    public void findByMember(String userId, Callback<List<Group>> callback) {
        groups().whereArrayContains("memberIds", userId).get()
                .addOnSuccessListener(snap -> {
                    List<Group> result = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Group group = doc.toObject(Group.class);
                        if (group != null) {
                            result.add(group);
                        }
                    }
                    callback.onSuccess(result);
                })
                .addOnFailureListener(callback::onFailure);
    }

    /** Deletes the group together with all of its expenses and settlements, in one batch. */
    public void delete(String groupId, Callback<Void> callback) {
        DocumentReference groupRef = groups().document(groupId);
        groupRef.collection(FirestorePaths.TRANSACTIONS).get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        batch.delete(doc.getReference());
                    }
                    batch.delete(groupRef);
                    return batch.commit();
                })
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
}
