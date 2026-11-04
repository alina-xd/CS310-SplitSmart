package com.team19.splitsmart.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.data.repository.UserRepository;
import com.team19.splitsmart.util.Callback;

/** C5 Account Service. Owner: Feature 1. */
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

    /** The signed-in user's uid, or null if nobody is signed in. Firebase remembers the session. */
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    public void register(String name, String email, String uscId, String password, Callback<User> callback) {
        // TODO (Feature 1): validate fields, check duplicate email and USC ID, create the
        // Firebase account, then save the User with the email in lower case.
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }

    public void login(String email, String password, Callback<User> callback) {
        // TODO (Feature 1)
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }

    public void logout() {
        // TODO (Feature 1)
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }

    public void getCurrentUser(Callback<User> callback) {
        // TODO (Feature 1)
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }

    public void updateName(String newName, Callback<Void> callback) {
        // TODO (Feature 1)
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }

    public boolean isValidUscEmail(String email) {
        // TODO (Feature 1)
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }

    public boolean isValidUscId(String uscId) {
        // TODO (Feature 1)
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }
}
