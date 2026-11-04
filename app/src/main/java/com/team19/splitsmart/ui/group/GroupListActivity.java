package com.team19.splitsmart.ui.group;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.service.GroupService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.ui.account.ProfileActivity;

/** C2 Group Screens. Owner: Part A. The user's groups. */
public class GroupListActivity extends AppCompatActivity {

    private GroupService groupService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_group_list);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        groupService = new GroupService();
        findViewById(R.id.buttonCreateGroup).setOnClickListener(v -> startActivity(new Intent(this, GroupFormActivity.class)));
        findViewById(R.id.buttonProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.buttonOpenGroupPlaceholder).setOnClickListener(v -> onGroupSelected("dev-group-trip"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGroups();
    }

    private void loadGroups() {
        // TODO (Part A): groupService.getGroupsFor(currentUserId, ...) and show them in a list
        // whose rows call onGroupSelected(group.getGroupId()). Then remove the placeholder button.
    }

    private void onGroupSelected(String groupId) {
        Intent intent = new Intent(this, GroupActivity.class);
        intent.putExtra(Extras.GROUP_ID, groupId);
        startActivity(intent);
    }
}
