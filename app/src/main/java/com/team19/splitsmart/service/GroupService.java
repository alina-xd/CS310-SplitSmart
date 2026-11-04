package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.data.repository.GroupRepository;
import com.team19.splitsmart.data.repository.UserRepository;
import com.team19.splitsmart.util.Callback;

import java.util.List;
import java.util.Map;

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
        // TODO (Part A)
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part A)"));
    }

    public void updateGroup(Group group, String requesterId, Callback<Void> callback) {
        // TODO (Part A): owner only.
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part A)"));
    }

    public void deleteGroup(String groupId, String requesterId, Callback<Void> callback) {
        // TODO (Part A): owner only. GroupRepository.delete already removes the transactions too.
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part A)"));
    }

    public void addMember(String groupId, String email, String requesterId, Callback<Void> callback) {
        // TODO (Part A): owner only; specific messages for unknown email and existing member.
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part A)"));
    }

    public void getGroupsFor(String userId, Callback<List<Group>> callback) {
        // TODO (Part A)
        callback.onFailure(new UnsupportedOperationException("Not implemented yet (Part A)"));
    }

    /** Shared read used by every part (already implemented). Returns null if not found. */
    public void getGroup(String groupId, Callback<Group> callback) {
        groupRepo.findById(groupId, callback);
    }

    /** The group's members keyed by uid, for showing names. Shared read (already implemented). */
    public void getMembers(Group group, Callback<Map<String, User>> callback) {
        userRepo.findByIds(group.getMemberIds(), callback);
    }
}
