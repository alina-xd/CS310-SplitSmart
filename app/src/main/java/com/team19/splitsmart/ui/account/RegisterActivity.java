package com.team19.splitsmart.ui.account;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.group.GroupListActivity;

/** C1 Account Screens. Owner: Feature 1. Registration form. */
public class RegisterActivity extends AppCompatActivity {

    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        authService = new AuthService();
        findViewById(R.id.buttonCreateAccount).setOnClickListener(v -> onCreateAccountClicked());
    }

    private void onCreateAccountClicked() {
        // TODO (Feature 1): call authService.register(...); on ValidationException call
        // showFieldError(e.getField(), e.getMessage()); open the group list only on success.
        Intent intent = new Intent(this, GroupListActivity.class);
        startActivity(intent);
        finish();
    }

    private void showFieldError(String field, String message) {
        // TODO (Feature 1): show the message next to the matching input.
    }
}
