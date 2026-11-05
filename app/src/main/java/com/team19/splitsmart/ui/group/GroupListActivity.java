package com.team19.splitsmart.ui.group;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.service.GroupService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.ui.account.ProfileActivity;
import com.team19.splitsmart.util.Callback;

import java.util.ArrayList;
import java.util.List;

/** C2 Group Screens. Owner: Part A. The groups the user belongs to. */
public class GroupListActivity extends AppCompatActivity {

    private GroupService groupService;
    private AuthService authService;
    private ArrayAdapter<String> adapter;
    private TextView textEmpty;
    private final List<Group> groups = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_group_list);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        groupService = new GroupService();
        authService = new AuthService();
        textEmpty = findViewById(R.id.textEmpty);
        ListView list = findViewById(R.id.listGroups);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> onGroupSelected(groups.get(position).getGroupId()));
        findViewById(R.id.buttonCreateGroup).setOnClickListener(v -> startActivity(new Intent(this, GroupFormActivity.class)));
        findViewById(R.id.buttonProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGroups();
    }

    private void loadGroups() {
        String uid = authService.getCurrentUserId();
        groupService.getGroupsFor(uid, new Callback<List<Group>>() {
            @Override
            public void onSuccess(List<Group> result) {
                groups.clear();
                groups.addAll(result);
                adapter.clear();
                for (Group g : result) {
                    adapter.add(g.isOwner(uid) ? g.getName() + "  (owner)" : g.getName());
                }
                textEmpty.setText(result.isEmpty() ? "No groups yet. Create one, or ask an owner to add you." : "");
            }

            @Override
            public void onFailure(Exception e) {
                textEmpty.setText("Couldn't load groups: " + e.getMessage());
            }
        });
    }

    private void onGroupSelected(String groupId) {
        Intent intent = new Intent(this, GroupActivity.class);
        intent.putExtra(Extras.GROUP_ID, groupId);
        startActivity(intent);
    }
}
