package com.team19.splitsmart.ui.group;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.service.GroupService;
import com.team19.splitsmart.service.ValidationException;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.util.Callback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Members part of the group screen (section_group_members.xml). Owner: Part A.
 * Group name, member list, and for the owner only: add member, edit and delete group.
 */
public class GroupMembersSection {

    private final AppCompatActivity activity;
    private final String groupId;
    private final GroupService groupService = new GroupService();
    private final AuthService authService = new AuthService();
    private final TextView textName;
    private final TextView textDescription;
    private final TextView textMembers;
    private final View layoutOwnerActions;
    private final EditText editMemberEmail;

    public GroupMembersSection(AppCompatActivity activity, String groupId) {
        this.activity = activity;
        this.groupId = groupId;
        textName = activity.findViewById(R.id.textGroupName);
        textDescription = activity.findViewById(R.id.textGroupDescription);
        textMembers = activity.findViewById(R.id.textGroupMembers);
        layoutOwnerActions = activity.findViewById(R.id.layoutOwnerActions);
        editMemberEmail = activity.findViewById(R.id.editMemberEmail);
        activity.findViewById(R.id.buttonEditGroup).setOnClickListener(v -> onEditGroupClicked());
        activity.findViewById(R.id.buttonAddMember).setOnClickListener(v -> onAddMemberClicked());
        activity.findViewById(R.id.buttonDeleteGroup).setOnClickListener(v -> onDeleteGroupClicked());
    }

    /** Called from GroupActivity.onResume, so the group reloads after editing. */
    public void refresh() {
        groupService.getGroup(groupId, new Callback<Group>() {
            @Override
            public void onSuccess(Group group) {
                if (group == null) {
                    Toast.makeText(activity, "This group no longer exists", Toast.LENGTH_LONG).show();
                    activity.finish();
                    return;
                }
                show(group);
            }

            @Override
            public void onFailure(Exception e) {
                toast(e);
            }
        });
    }

    private void show(Group group) {
        String uid = authService.getCurrentUserId();
        textName.setText(group.getName());
        String description = group.getDescription();
        textDescription.setText(description);
        textDescription.setVisibility(description == null || description.isEmpty() ? View.GONE : View.VISIBLE);
        layoutOwnerActions.setVisibility(group.isOwner(uid) ? View.VISIBLE : View.GONE);
        groupService.getMembers(group, new Callback<Map<String, User>>() {
            @Override
            public void onSuccess(Map<String, User> members) {
                List<String> names = new ArrayList<>();
                for (String memberId : group.getMemberIds()) {
                    User user = members.get(memberId);
                    String name = user == null ? "Unknown user" : user.getName();
                    if (group.isOwner(memberId)) {
                        name += " (owner)";
                    }
                    if (memberId.equals(uid)) {
                        name += " (you)";
                    }
                    names.add(name);
                }
                textMembers.setText("Members: " + TextUtils.join(", ", names));
            }

            @Override
            public void onFailure(Exception e) {
                toast(e);
            }
        });
    }

    private void onEditGroupClicked() {
        Intent intent = new Intent(activity, GroupFormActivity.class);
        intent.putExtra(Extras.GROUP_ID, groupId);
        activity.startActivity(intent);
    }

    private void onAddMemberClicked() {
        String email = editMemberEmail.getText().toString();
        View addButton = activity.findViewById(R.id.buttonAddMember);
        addButton.setEnabled(false); // a second tap would report "already in the group"
        groupService.addMember(groupId, email, authService.getCurrentUserId(), new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                addButton.setEnabled(true);
                editMemberEmail.setText("");
                Toast.makeText(activity, "Member added", Toast.LENGTH_SHORT).show();
                refresh();
            }

            @Override
            public void onFailure(Exception e) {
                addButton.setEnabled(true);
                if (e instanceof ValidationException && "email".equals(((ValidationException) e).getField())) {
                    editMemberEmail.setError(e.getMessage());
                } else {
                    toast(e);
                }
            }
        });
    }

    private void onDeleteGroupClicked() {
        new AlertDialog.Builder(activity)
                .setTitle("Delete group?")
                .setMessage("This also deletes all of the group's expenses and settlements.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteGroup())
                .show();
    }

    private void deleteGroup() {
        groupService.deleteGroup(groupId, authService.getCurrentUserId(), new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(activity, "Group deleted", Toast.LENGTH_SHORT).show();
                activity.finish();
            }

            @Override
            public void onFailure(Exception e) {
                toast(e);
            }
        });
    }

    private void toast(Exception e) {
        Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
    }
}
