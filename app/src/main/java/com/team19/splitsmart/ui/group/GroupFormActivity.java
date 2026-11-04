package com.team19.splitsmart.ui.group;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.service.GroupService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;

/** C2 Group Screens. Owner: Part A. Create a group, or edit one when GROUP_ID is passed. */
public class GroupFormActivity extends AppCompatActivity {

    private GroupService groupService;
    private String groupId; // null when creating a new group

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_group_form);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        groupService = new GroupService();
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        findViewById(R.id.buttonSave).setOnClickListener(v -> onSaveClicked());
    }

    private void onSaveClicked() {
        // TODO (Part A): createGroup(...) or updateGroup(...), then finish() on success.
        finish();
    }
}
