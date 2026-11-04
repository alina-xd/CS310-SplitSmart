package com.team19.splitsmart.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.data.repository.UserRepository;
import com.team19.splitsmart.dev.DevSession;
import com.team19.splitsmart.util.Callback;

/** C5 Account Service. Owner: Part A. */
public class AuthService {

    private final UserRepository userRepo;
    private final FirebaseAuth auth;

    public AuthService() {
        this(new UserRepository(), FirebaseAuth.getInstance());
    }

    public AuthService(UserRepository userRepo, FirebaseAuth auth) {
        this.userRepo = userRepo;
        this.auth = auth;
    }

    /**
     * The signed-in user's uid, or null if nobody is signed in. Firebase remembers the session.
     * Falls back to the user picked in Developer tools, so every part can test before login works.
     */
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? DevSession.getUserId() : user.getUid();
    }

    public void register(String name, String email, String uscId, String password, Callback<User> callback) {
        // TODO (Part A): validate fields, check duplicate email and USC ID, create the
        // Firebase account, then save the User with the email in lower case.
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part A)"));
    }

    public void login(String email, String password, Callback<User> callback) {
        // TODO (Part A)
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part A)"));
    }

    public void logout() {
        // TODO (Part A)
        throw new UnsupportedOperationException("Not implemented yet (Part A)");
    }

    public void getCurrentUser(Callback<User> callback) {
        // TODO (Part A)
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part A)"));
    }

    public void updateName(String newName, Callback<Void> callback) {
        // TODO (Part A)
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part A)"));
    }

    public boolean isValidUscEmail(String email) {
        // TODO (Part A)
        throw new UnsupportedOperationException("Not implemented yet (Part A)");
    }

    public boolean isValidUscId(String uscId) {
        // TODO (Part A)
        throw new UnsupportedOperationException("Not implemented yet (Part A)");
    }
}
