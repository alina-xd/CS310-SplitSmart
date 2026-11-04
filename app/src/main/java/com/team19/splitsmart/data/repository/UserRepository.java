package com.team19.splitsmart.data.repository;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.util.Callback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads and writes users/{uid}. Only the service layer should use this class. */
public class UserRepository {

    private final FirebaseFirestore db;

    public UserRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public UserRepository(FirebaseFirestore db) {
        this.db = db;
    }

    private CollectionReference users() {
        return db.collection(FirestorePaths.USERS);
    }

    public void save(User user, Callback<Void> callback) {
        users().document(user.getUid()).set(user)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    /** Returns null through the callback if no such user exists. */
    public void findById(String uid, Callback<User> callback) {
        users().document(uid).get()
                .addOnSuccessListener(doc -> callback.onSuccess(doc.exists() ? doc.toObject(User.class) : null))
                .addOnFailureListener(callback::onFailure);
    }

    /** Looks up the lower-case email. Returns null if no account uses it. */
    public void findByEmail(String email, Callback<User> callback) {
        findOneWhere("uscEmail", email.trim().toLowerCase(Locale.US), callback);
    }

    /** Returns null if no account uses the USC ID. */
    public void findByUscId(String uscId, Callback<User> callback) {
        findOneWhere("uscId", uscId.trim(), callback);
    }

    /** Loads several users at once, for example a group's members, keyed by uid. */
    public void findByIds(List<String> uids, Callback<Map<String, User>> callback) {
        List<Task<DocumentSnapshot>> reads = new ArrayList<>();
        for (String uid : uids) {
            reads.add(users().document(uid).get());
        }
        Tasks.<DocumentSnapshot>whenAllSuccess(reads)
                .addOnSuccessListener(docs -> {
                    Map<String, User> byId = new LinkedHashMap<>();
                    for (DocumentSnapshot doc : docs) {
                        User user = doc.exists() ? doc.toObject(User.class) : null;
                        if (user != null) {
                            byId.put(doc.getId(), user);
                        }
                    }
                    callback.onSuccess(byId);
                })
                .addOnFailureListener(callback::onFailure);
    }

    private void findOneWhere(String field, String value, Callback<User> callback) {
        users().whereEqualTo(field, value).limit(1).get()
                .addOnSuccessListener(snap -> callback.onSuccess(
                        snap.isEmpty() ? null : snap.getDocuments().get(0).toObject(User.class)))
                .addOnFailureListener(callback::onFailure);
    }
}
