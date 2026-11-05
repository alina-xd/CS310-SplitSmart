package com.team19.splitsmart.ui.group;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.service.GroupService;
import com.team19.splitsmart.service.ValidationException;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.util.Callback;

/** C2 Group Screens. Owner: Part A. Create a group, or edit one when GROUP_ID is passed. */
public class GroupFormActivity extends AppCompatActivity {

    private GroupService groupService;
    private AuthService authService;
    private EditText editName;
    private EditText editDescription;
    private View buttonSave;
    private String groupId; // null when creating a new group
    private Group group;    // the loaded group when editing

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_group_form);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        groupService = new GroupService();
        authService = new AuthService();
        editName = findViewById(R.id.editGroupName);
        editDescription = findViewById(R.id.editGroupDescription);
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        buttonSave = findViewById(R.id.buttonSave);
        buttonSave.setOnClickListener(v -> onSaveClicked());
        if (groupId != null) {
            ((TextView) findViewById(R.id.textFormTitle)).setText("Edit group");
            loadGroup();
        }
    }

    private void loadGroup() {
        groupService.getGroup(groupId, new Callback<Group>() {
            @Override
            public void onSuccess(Group result) {
                if (result == null) {
                    finish();
                    return;
                }
                group = result;
                editName.setText(group.getName());
                editDescription.setText(group.getDescription());
            }

            @Override
            public void onFailure(Exception e) {
                showError(e);
            }
        });
    }

    private void onSaveClicked() {
        String name = editName.getText().toString();
        String description = editDescription.getText().toString();
        String uid = authService.getCurrentUserId();
        buttonSave.setEnabled(false); // a second tap would save twice
        if (groupId == null) {
            groupService.createGroup(name, description, uid, new Callback<Group>() {
                @Override
                public void onSuccess(Group created) {
                    Intent intent = new Intent(GroupFormActivity.this, GroupActivity.class);
                    intent.putExtra(Extras.GROUP_ID, created.getGroupId());
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onFailure(Exception e) {
                    showError(e);
                }
            });
        } else if (group == null) {
            buttonSave.setEnabled(true); // still loading
        } else {
            group.setName(name);
            group.setDescription(description);
            groupService.updateGroup(group, uid, new Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    finish();
                }

                @Override
                public void onFailure(Exception e) {
                    showError(e);
                }
            });
        }
    }

    private void showError(Exception e) {
        buttonSave.setEnabled(true);
        if (e instanceof ValidationException && "name".equals(((ValidationException) e).getField())) {
            editName.setError(e.getMessage());
        } else {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
