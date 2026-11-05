package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.data.repository.GroupRepository;
import com.team19.splitsmart.data.repository.UserRepository;
import com.team19.splitsmart.util.Callback;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** C6 Group Service. Owner: Part A. */
public class GroupService {

    private final GroupRepository groupRepo;
    private final UserRepository userRepo;

    public GroupService() {
        this(new GroupRepository(), new UserRepository());
    }

    public GroupService(GroupRepository groupRepo, UserRepository userRepo) {
        this.groupRepo = groupRepo;
        this.userRepo = userRepo;
    }

    public void createGroup(String name, String description, String ownerId, Callback<Group> callback) {
        String cleanName = trim(name);
        if (cleanName.isEmpty()) {
            callback.onFailure(new ValidationException("name", "Enter a group name"));
            return;
        }
        if (ownerId == null) {
            callback.onFailure(new IllegalStateException("Not signed in"));
            return;
        }
        groupRepo.save(new Group(cleanName, trim(description), ownerId), callback);
    }

    /** Saves the new name and description. Owner only. */
    public void updateGroup(Group group, String requesterId, Callback<Void> callback) {
        String cleanName = trim(group.getName());
        if (cleanName.isEmpty()) {
            callback.onFailure(new ValidationException("name", "Enter a group name"));
            return;
        }
        loadOwnedGroup(group.getGroupId(), requesterId, "edit this group", callback, saved -> {
            // Only copy the editable fields, so a stale member list can't overwrite newer members.
            saved.setName(cleanName);
            saved.setDescription(trim(group.getDescription()));
            groupRepo.save(saved, then(callback, unused -> callback.onSuccess(null)));
        });
    }

    /** Owner only. GroupRepository.delete also removes the group's expenses and settlements. */
    public void deleteGroup(String groupId, String requesterId, Callback<Void> callback) {
        loadOwnedGroup(groupId, requesterId, "delete this group", callback,
                saved -> groupRepo.delete(groupId, callback));
    }

    /** Owner only. Adds a registered user by USC email. */
    public void addMember(String groupId, String email, String requesterId, Callback<Void> callback) {
        String cleanEmail = trim(email).toLowerCase(Locale.US);
        if (cleanEmail.isEmpty()) {
            callback.onFailure(new ValidationException("email", "Enter a USC email"));
            return;
        }
        loadOwnedGroup(groupId, requesterId, "add members", callback, saved ->
                userRepo.findByEmail(cleanEmail, then(callback, user -> {
                    if (user == null) {
                        callback.onFailure(new ValidationException("email", "No registered user with this email"));
                    } else if (saved.hasMember(user.getUid())) {
                        callback.onFailure(new ValidationException("email", user.getName() + " is already a member"));
                    } else {
                        saved.addMember(user.getUid());
                        groupRepo.save(saved, then(callback, unused -> callback.onSuccess(null)));
                    }
                })));
    }

    public void getGroupsFor(String userId, Callback<List<Group>> callback) {
        if (userId == null) {
            callback.onFailure(new IllegalStateException("Not signed in"));
            return;
        }
        groupRepo.findByMember(userId, then(callback, groups -> {
            groups.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(trim(a.getName()), trim(b.getName())));
            callback.onSuccess(groups);
        }));
    }

    /** Shared read used by every part (already implemented). Returns null if not found. */
    public void getGroup(String groupId, Callback<Group> callback) {
        groupRepo.findById(groupId, callback);
    }

    /** The group's members keyed by uid, for showing names. Shared read (already implemented). */
    public void getMembers(Group group, Callback<Map<String, User>> callback) {
        userRepo.findByIds(group.getMemberIds(), callback);
    }

    /** Loads the group and continues only if requesterId is its owner. */
    private void loadOwnedGroup(String groupId, String requesterId, String action, Callback<?> callback,
                                Consumer<Group> onOwner) {
        groupRepo.findById(groupId, then(callback, saved -> {
            if (saved == null) {
                callback.onFailure(new IllegalStateException("This group no longer exists"));
            } else if (!saved.isOwner(requesterId)) {
                callback.onFailure(new ValidationException("permission", "Only the owner can " + action));
            } else {
                onOwner.accept(saved);
            }
        }));
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** A Callback that sends failures to outer and handles success with onSuccess. */
    private static <T> Callback<T> then(Callback<?> outer, Consumer<T> onSuccess) {
        return new Callback<T>() {
            @Override
            public void onSuccess(T result) {
                onSuccess.accept(result);
            }

            @Override
            public void onFailure(Exception e) {
                outer.onFailure(e);
            }
        };
    }
}
