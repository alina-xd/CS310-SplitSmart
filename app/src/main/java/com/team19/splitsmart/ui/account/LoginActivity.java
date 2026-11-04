package com.team19.splitsmart.ui.account;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.dev.DevActivity;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.group.GroupListActivity;

/** C1 Account Screens. Owner: Part A. Login screen; the app starts here. */
public class LoginActivity extends AppCompatActivity {

    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        authService = new AuthService();
        findViewById(R.id.buttonLogin).setOnClickListener(v -> onLoginClicked());
        findViewById(R.id.buttonSignUp).setOnClickListener(v -> onSignUpClicked());
        // Development only: remove this line, the button and the dev package before the demo.
        findViewById(R.id.buttonDevTools).setOnClickListener(v -> startActivity(new Intent(this, DevActivity.class)));
    }

    private void onLoginClicked() {
        // TODO (Part A): read email and password, call authService.login(...), and open the
        // group list only on success. Also skip this screen if authService.getCurrentUserId() != null.
        Intent intent = new Intent(this, GroupListActivity.class);
        startActivity(intent);
        finish();
    }

    private void onSignUpClicked() {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }
}
