package com.team19.splitsmart.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.data.repository.UserRepository;
import com.team19.splitsmart.dev.DevSession;
import com.team19.splitsmart.util.Callback;

import java.util.Locale;
import java.util.function.Consumer;

/** C5 Account Service. Owner: Part A. */
public class AuthService {

    /** UC-1 4.2.2. */
    static final String ACCOUNT_EXISTS = "An account with this USC email or USC ID already exists.";

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

    /** True only for a real Firebase sign-in (not the Developer tools user). */
    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    public void register(String name, String email, String uscId, String password, Callback<User> callback) {
        String cleanName = trim(name);
        String cleanEmail = trim(email).toLowerCase(Locale.US);
        String cleanId = trim(uscId);
        if (cleanName.isEmpty()) {
            callback.onFailure(new ValidationException("name", "Enter your name"));
        } else if (!isValidUscEmail(cleanEmail)) {
            callback.onFailure(new ValidationException("email", "Use your @usc.edu email"));
        } else if (!isValidUscId(cleanId)) {
            callback.onFailure(new ValidationException("uscId", "USC ID must be exactly 10 digits"));
        } else if (password == null || password.isEmpty()) {
            callback.onFailure(new ValidationException("password", "Enter a password"));
        } else {
            userRepo.findByEmail(cleanEmail, then(callback, byEmail -> {
                if (byEmail != null) {
                    callback.onFailure(new ValidationException("email", ACCOUNT_EXISTS));
                    return;
                }
                userRepo.findByUscId(cleanId, then(callback, byId -> {
                    if (byId != null) {
                        callback.onFailure(new ValidationException("uscId", ACCOUNT_EXISTS));
                        return;
                    }
                    createAccount(cleanName, cleanEmail, cleanId, password, callback);
                }));
            }));
        }
    }

    private void createAccount(String name, String email, String uscId, String password, Callback<User> callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    User user = new User(result.getUser().getUid(), name, email, uscId);
                    userRepo.save(user, then(callback, unused -> callback.onSuccess(user)));
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthWeakPasswordException) {
                        callback.onFailure(new ValidationException("password", "Password must be at least 6 characters"));
                    } else if (e instanceof FirebaseAuthUserCollisionException) {
                        callback.onFailure(new ValidationException("email", ACCOUNT_EXISTS));
                    } else {
                        callback.onFailure(e);
                    }
                });
    }

    public void login(String email, String password, Callback<User> callback) {
        String cleanEmail = trim(email).toLowerCase(Locale.US);
        if (cleanEmail.isEmpty() || password == null || password.isEmpty()) {
            callback.onFailure(new ValidationException("login", "Enter your email and password"));
            return;
        }
        auth.signInWithEmailAndPassword(cleanEmail, password)
                .addOnSuccessListener(result -> userRepo.findById(result.getUser().getUid(), then(callback, user -> {
                    if (user == null) {
                        auth.signOut();
                        callback.onFailure(new ValidationException("login", "No profile found for this account"));
                    } else {
                        callback.onSuccess(user);
                    }
                })))
                .addOnFailureListener(e -> callback.onFailure(new ValidationException("login", "Incorrect email or password")));
    }

    public void logout() {
        auth.signOut();
    }

    public void getCurrentUser(Callback<User> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onFailure(new IllegalStateException("Not signed in"));
            return;
        }
        userRepo.findById(uid, callback);
    }

    public void updateName(String newName, Callback<Void> callback) {
        String cleanName = trim(newName);
        if (cleanName.isEmpty()) {
            callback.onFailure(new ValidationException("name", "Enter your name"));
            return;
        }
        getCurrentUser(then(callback, user -> {
            if (user == null) {
                callback.onFailure(new IllegalStateException("User not found"));
                return;
            }
            user.setName(cleanName);
            userRepo.save(user, callback);
        }));
    }

    /** Some text, then exactly "@usc.edu" (any letter case). */
    public boolean isValidUscEmail(String email) {
        return email != null && email.trim().toLowerCase(Locale.US).matches("[^@\\s]+@usc\\.edu");
    }

    /** Exactly 10 digits. */
    public boolean isValidUscId(String uscId) {
        return uscId != null && uscId.trim().matches("[0-9]{10}");
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** A Callback that sends failures to outer and handles success with onSuccess. */
    private static <T> Callback<T> then(Callback<?> outer, Consumer<T> onSuccess) {
        return new Callback<T>() {
            @Override
            public void onSuccess(T result) {
                onSuccess.accept(result);
            }

            @Override
            public void onFailure(Exception e) {
                outer.onFailure(e);
            }
        };
    }
}
