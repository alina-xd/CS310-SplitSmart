package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.data.repository.GroupRepository;
import com.team19.splitsmart.data.repository.UserRepository;
import com.team19.splitsmart.util.Callback;

import java.util.List;
import java.util.Map;

/** C6 Group Service. Owner: Feature 1. */
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
        // TODO (Feature 1)
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }

    public void updateGroup(Group group, String requesterId, Callback<Void> callback) {
        // TODO (Feature 1): owner only.
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }

    public void deleteGroup(String groupId, String requesterId, Callback<Void> callback) {
        // TODO (Feature 1): owner only. GroupRepository.delete already removes the transactions too.
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }

    public void addMember(String groupId, String email, String requesterId, Callback<Void> callback) {
        // TODO (Feature 1): owner only; specific messages for unknown email and existing member.
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }

    public void getGroupsFor(String userId, Callback<List<Group>> callback) {
        // TODO (Feature 1)
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }

    public void getGroup(String groupId, Callback<Group> callback) {
        // TODO (Feature 1)
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }

    /** The group's members keyed by uid, for showing names. Added for implementation. */
    public void getMembers(Group group, Callback<Map<String, User>> callback) {
        // TODO (Feature 1): delegate to userRepo.findByIds(group.getMemberIds(), callback).
        throw new UnsupportedOperationException("Not implemented yet (Feature 1)");
    }
}
