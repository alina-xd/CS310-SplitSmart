package com.team19.splitsmart.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.service.ValidationException;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.util.Callback;

/** C1 Account Screens. Owner: Part A. View and edit name, log out. Email and USC ID are read-only. */
public class ProfileActivity extends AppCompatActivity {

    private AuthService authService;
    private EditText editName;
    private TextView textEmail;
    private TextView textUscId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        authService = new AuthService();
        editName = findViewById(R.id.editName);
        textEmail = findViewById(R.id.textEmail);
        textUscId = findViewById(R.id.textUscId);
        findViewById(R.id.buttonSaveName).setOnClickListener(v -> onSaveNameClicked());
        findViewById(R.id.buttonLogOut).setOnClickListener(v -> onLogOutClicked());
        loadProfile();
    }

    private void loadProfile() {
        authService.getCurrentUser(new Callback<User>() {
            @Override
            public void onSuccess(User user) {
                if (user == null) {
                    return;
                }
                editName.setText(user.getName());
                textEmail.setText("USC email: " + user.getUscEmail());
                textUscId.setText("USC ID: " + user.getUscId());
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(ProfileActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onSaveNameClicked() {
        authService.updateName(editName.getText().toString(), new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(ProfileActivity.this, "Name saved", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Exception e) {
                if (e instanceof ValidationException) {
                    editName.setError(e.getMessage());
                } else {
                    Toast.makeText(ProfileActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void onLogOutClicked() {
        authService.logout();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
