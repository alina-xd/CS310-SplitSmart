package com.team19.splitsmart.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.group.GroupListActivity;
import com.team19.splitsmart.util.Callback;

/** C1 Account Screens. Owner: Part A. Login screen; the app starts here. */
public class LoginActivity extends AppCompatActivity {

    private AuthService authService;
    private EditText editEmail;
    private EditText editPassword;
    private TextView textError;
    private Button buttonLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authService = new AuthService();
        if (authService.isSignedIn()) {
            openGroupList();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        textError = findViewById(R.id.textLoginError);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonLogin.setOnClickListener(v -> onLoginClicked());
        findViewById(R.id.buttonSignUp).setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void onLoginClicked() {
        textError.setVisibility(View.GONE);
        buttonLogin.setEnabled(false);
        authService.login(editEmail.getText().toString(), editPassword.getText().toString(), new Callback<User>() {
            @Override
            public void onSuccess(User user) {
                openGroupList();
            }

            @Override
            public void onFailure(Exception e) {
                buttonLogin.setEnabled(true);
                textError.setText(e.getMessage());
                textError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void openGroupList() {
        startActivity(new Intent(this, GroupListActivity.class));
        finish();
    }
}
