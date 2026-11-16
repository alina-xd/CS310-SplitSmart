package com.team19.splitsmart.testing;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.service.SettlementService;
import com.team19.splitsmart.util.Callback;

/** Debug-only process for exercising cross-process Firebase updates in instrumentation tests. */
public final class RemoteUserService extends Service {
    public static final int RECORD_SETTLEMENT = 1;
    public static final int RESULT = 2;
    public static final String EMAIL = "email";
    public static final String PASSWORD = "password";
    public static final String GROUP_ID = "groupId";
    public static final String RECIPIENT_ID = "recipientId";
    public static final String AMOUNT_CENTS = "amountCents";
    public static final String ERROR = "error";
    public static final String PROCESS_ID = "processId";

    private final Messenger messenger =
            new Messenger(new Handler(Looper.getMainLooper(), this::handleMessage));

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private boolean handleMessage(Message message) {
        if (message.what != RECORD_SETTLEMENT) return false;

        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            auth.useEmulator("10.0.2.2", 9099);
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            firestore.useEmulator("10.0.2.2", 8080);
            firestore.setFirestoreSettings(
                    new FirebaseFirestoreSettings.Builder(firestore.getFirestoreSettings())
                            .setPersistenceEnabled(false)
                            .build());

            Bundle data = message.getData();
            auth.signInWithEmailAndPassword(
                            data.getString(EMAIL), data.getString(PASSWORD))
                    .addOnSuccessListener(
                            result -> {
                                Settlement settlement = new Settlement();
                                settlement.setGroupId(data.getString(GROUP_ID));
                                settlement.setCreatorId(result.getUser().getUid());
                                settlement.setPayerId(result.getUser().getUid());
                                settlement.setRecipientId(data.getString(RECIPIENT_ID));
                                settlement.setAmountCents(data.getLong(AMOUNT_CENTS));
                                new SettlementService()
                                        .createSettlement(
                                                settlement,
                                                new Callback<Void>() {
                                                    @Override
                                                    public void onSuccess(Void unused) {
                                                        sendResult(message.replyTo, null);
                                                    }

                                                    @Override
                                                    public void onFailure(Exception error) {
                                                        sendResult(message.replyTo, error);
                                                    }
                                                });
                            })
                    .addOnFailureListener(error -> sendResult(message.replyTo, error));
        } catch (RuntimeException error) {
            sendResult(message.replyTo, error);
        }
        return true;
    }

    private void sendResult(Messenger replyTo, Exception error) {
        if (replyTo == null) return;
        Message response = Message.obtain(null, RESULT);
        Bundle data = new Bundle();
        data.putInt(PROCESS_ID, Process.myPid());
        if (error != null) data.putString(ERROR, error.getMessage());
        response.setData(data);
        try {
            replyTo.send(response);
        } catch (android.os.RemoteException e) {
            throw new IllegalStateException("Could not return remote test result", e);
        }
    }
}
