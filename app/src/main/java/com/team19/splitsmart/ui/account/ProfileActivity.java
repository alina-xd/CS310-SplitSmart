package com.team19.splitsmart.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;

/** C1 Account Screens. Owner: Feature 1. View and edit name, log out. */
public class ProfileActivity extends AppCompatActivity {

    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        authService = new AuthService();
        findViewById(R.id.buttonSaveName).setOnClickListener(v -> onSaveNameClicked());
        findViewById(R.id.buttonLogOut).setOnClickListener(v -> onLogOutClicked());
    }

    private void onSaveNameClicked() {
        // TODO (Feature 1): authService.updateName(...). Email and USC ID are read-only.
        Toast.makeText(this, "Not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private void onLogOutClicked() {
        // TODO (Feature 1): call authService.logout() first.
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
