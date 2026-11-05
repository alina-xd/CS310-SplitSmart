package com.team19.splitsmart;

import androidx.test.runner.AndroidJUnitRunner;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

/** Redirects the app's default Firebase clients before any tested activity or service runs. */
public class EmulatorTestRunner extends AndroidJUnitRunner {
    @Override
    public void onStart() {
        FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.useEmulator("10.0.2.2", 8080);
        db.setFirestoreSettings(
                new FirebaseFirestoreSettings.Builder(db.getFirestoreSettings())
                        .setPersistenceEnabled(false)
                        .build());
        super.onStart();
    }
}
