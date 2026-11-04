package com.team19.splitsmart.data.model;

import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * A group, stored at groups/{groupId}. memberIds always includes the owner.
 * The group document also holds a "seqCounter" field used by TransactionRepository;
 * it isn't part of this class, which is why extra properties are ignored.
 */
@IgnoreExtraProperties
public class Group {

    private String groupId;
    private String name;
    private String description;
    private String ownerId;
    private List<String> memberIds = new ArrayList<>();

    /** Required by Firestore. */
    public Group() {
    }

    /** A new group whose creator is its owner and first member. */
    public Group(String name, String description, String ownerId) {
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.memberIds.add(ownerId);
    }

    public boolean isOwner(String userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    public boolean hasMember(String userId) {
        return memberIds != null && memberIds.contains(userId);
    }

    public void addMember(String userId) {
        if (memberIds == null) {
            memberIds = new ArrayList<>();
        }
        if (!memberIds.contains(userId)) {
            memberIds.add(userId);
        }
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
    }
}
