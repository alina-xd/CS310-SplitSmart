package com.team19.splitsmart.dev;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.ui.group.GroupActivity;
import com.team19.splitsmart.ui.group.GroupListActivity;
import com.team19.splitsmart.util.Callback;

/** Developer tools: seed test data, act as a test user, jump to a test group. Development only. */
public class DevActivity extends AppCompatActivity {

    private TextView currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dev);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        currentUser = findViewById(R.id.textDevCurrentUser);
        showCurrentUser();

        findViewById(R.id.buttonDevSeed).setOnClickListener(v -> seed());
        findViewById(R.id.buttonDevAlice).setOnClickListener(v -> actAs(DevData.ALICE));
        findViewById(R.id.buttonDevBob).setOnClickListener(v -> actAs(DevData.BOB));
        findViewById(R.id.buttonDevCarol).setOnClickListener(v -> actAs(DevData.CAROL));
        findViewById(R.id.buttonDevDavid).setOnClickListener(v -> actAs(DevData.DAVID));
        findViewById(R.id.buttonDevTrip).setOnClickListener(v -> openGroup(DevData.GROUP_TRIP));
        findViewById(R.id.buttonDevHome).setOnClickListener(v -> openGroup(DevData.GROUP_HOME));
        findViewById(R.id.buttonDevDisposable).setOnClickListener(v -> openGroup(DevData.GROUP_DISPOSABLE));
        findViewById(R.id.buttonDevGroupList).setOnClickListener(v -> startActivity(new Intent(this, GroupListActivity.class)));
    }

    private void seed() {
        DevData.seed(new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(DevActivity.this, "Test data written", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(DevActivity.this, "Seeding failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void actAs(String uid) {
        DevSession.setUserId(uid);
        showCurrentUser();
    }

    private void showCurrentUser() {
        String uid = DevSession.getUserId();
        currentUser.setText("Acting as: " + (uid == null ? "nobody" : uid));
    }

    private void openGroup(String groupId) {
        Intent intent = new Intent(this, GroupActivity.class);
        intent.putExtra(Extras.GROUP_ID, groupId);
        startActivity(intent);
    }
}
