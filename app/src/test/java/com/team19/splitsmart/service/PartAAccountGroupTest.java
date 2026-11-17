package com.team19.splitsmart.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.data.repository.GroupRepository;
import com.team19.splitsmart.data.repository.UserRepository;
import com.team19.splitsmart.util.Callback;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Part A white-box tests (author: Alina Du). JUnit local unit tests for AuthService.register's
 * duplicate checks and every branch of GroupService.
 *
 * The repositories are replaced by in-memory fakes that record each lookup, so a test can check
 * which branch ran and that the code stopped early when it should. AuthService gets a null
 * FirebaseAuth: if register ever went past its checks it would crash, so these tests also prove
 * that a rejected registration never tries to create an account.
 */
public class PartAAccountGroupTest {

    private static final String ACCOUNT_EXISTS = "An account with this USC email or USC ID already exists.";

    private FakeUsers users;
    private FakeGroups groups;
    private AuthService auth;
    private GroupService groupService;

    @Before
    public void setUp() {
        users = new FakeUsers();
        groups = new FakeGroups();
        users.add(new User("alice", "Alice", "alice@usc.edu", "1000000001"));
        users.add(new User("bob", "Bob", "bob@usc.edu", "1000000002"));
        users.add(new User("carol", "Carol", "carol@usc.edu", "1000000003"));
        auth = new AuthService(users, null);
        groupService = new GroupService(groups, users);
    }

    /** WB-A1: the email check runs first, ignores case and spaces, and stops before the ID check. */
    @Test
    public void register_duplicateEmail_isCheckedFirstIgnoringCaseAndSpaces() {
        Result<User> result = new Result<>();
        auth.register("New Person", "  ALICE@USC.edu ", "1000000009", "secret1", result);

        result.assertInvalid("email", ACCOUNT_EXISTS);
        assertEquals(Collections.singletonList("email:alice@usc.edu"), users.lookups);
    }

    /** WB-A2: a free email moves on to the ID check; a lookup failure is passed on unchanged. */
    @Test
    public void register_duplicateUscIdOrLookupFailure_stopsBeforeCreatingAccount() {
        Result<User> duplicateId = new Result<>();
        auth.register("New Person", "new@usc.edu", " 1000000001 ", "secret1", duplicateId);
        duplicateId.assertInvalid("uscId", ACCOUNT_EXISTS);
        assertEquals(Arrays.asList("email:new@usc.edu", "uscId:1000000001"), users.lookups);

        users.lookups.clear();
        IllegalStateException offline = new IllegalStateException("offline");
        users.failNextLookup = offline;
        Result<User> failed = new Result<>();
        auth.register("New Person", "new@usc.edu", "1000000009", "secret1", failed);
        assertSame(offline, failed.assertFailed());
        assertEquals(Collections.singletonList("email:new@usc.edu"), users.lookups);
    }

    /** WB-A3: createGroup's blank-name and signed-out branches, the success branch, and reading it back. */
    @Test
    public void createGroup_rejectsBlankNameOrMissingOwner_andMakesOwnerTheOnlyMember() {
        Result<Group> blank = new Result<>();
        groupService.createGroup("   ", "", "alice", blank);
        blank.assertInvalid("name", "Enter a group name");

        Result<Group> signedOut = new Result<>();
        groupService.createGroup("Roommates", "", null, signedOut);
        assertEquals("Not signed in", signedOut.assertFailed().getMessage());
        assertEquals(0, groups.saves);

        Result<Group> created = new Result<>();
        groupService.createGroup("  Roommates  ", null, "alice", created);
        Group saved = groups.stored(created.assertOk().getGroupId());
        assertEquals("Roommates", saved.getName());
        assertEquals("", saved.getDescription());
        assertEquals("alice", saved.getOwnerId());
        assertEquals(Collections.singletonList("alice"), saved.getMemberIds());
        assertEquals(1, groups.saves);

        Result<Group> read = new Result<>();
        groupService.getGroup(saved.getGroupId(), read);
        assertEquals("Roommates", read.assertOk().getName());
        Result<Map<String, User>> members = new Result<>();
        groupService.getMembers(saved, members);
        assertEquals(Collections.singletonList("Alice"), Arrays.asList(members.assertOk().get("alice").getName()));
        assertEquals(1, members.assertOk().size());
    }

    /** WB-A4: every branch of addMember, in the order the method checks them, plus a database failure. */
    @Test
    public void addMember_coversEveryBranchInOrder() {
        String id = newGroup("Trip", "alice");
        int savesAfterCreate = groups.saves;

        Result<Void> blank = new Result<>();
        groupService.addMember(id, "  ", "alice", blank);
        blank.assertInvalid("email", "Enter a USC email");

        Result<Void> missingGroup = new Result<>();
        groupService.addMember("no-such-group", "bob@usc.edu", "alice", missingGroup);
        assertEquals("This group no longer exists", missingGroup.assertFailed().getMessage());

        IllegalStateException offline = new IllegalStateException("offline");
        groups.failNextRead = offline;
        Result<Void> readFailed = new Result<>();
        groupService.addMember(id, "bob@usc.edu", "alice", readFailed);
        assertSame("a database failure is passed on unchanged", offline, readFailed.assertFailed());

        Result<Void> notOwner = new Result<>();
        groupService.addMember(id, "carol@usc.edu", "bob", notOwner);
        notOwner.assertInvalid("permission", "Only the owner can add members");
        assertTrue("a non-owner must be stopped before any email lookup", users.lookups.isEmpty());

        Result<Void> unknown = new Result<>();
        groupService.addMember(id, "nobody@usc.edu", "alice", unknown);
        unknown.assertInvalid("email", "No SplitSmart account uses this email");

        Result<Void> added = new Result<>();
        groupService.addMember(id, "  BOB@usc.edu ", "alice", added);
        added.assertOk();
        assertEquals("email:bob@usc.edu", users.lookups.get(users.lookups.size() - 1));
        assertEquals(Arrays.asList("alice", "bob"), groups.stored(id).getMemberIds());

        Result<Void> again = new Result<>();
        groupService.addMember(id, "bob@usc.edu", "alice", again);
        again.assertInvalid("email", "This user is already in the group");
        assertEquals("only the successful add saves", savesAfterCreate + 1, groups.saves);
    }

    /** WB-A5: owner-only edit and delete, edits from an old copy, and the sorted group list. */
    @Test
    public void updateAndDeleteGroup_areOwnerOnly_andStaleEditKeepsNewerMembers() {
        String trip = newGroup("Trip", "alice");
        Group staleCopy = groups.stored(trip);           // taken before Carol joins
        new Result<Void>().run(cb -> groupService.addMember(trip, "carol@usc.edu", "alice", cb)).assertOk();

        staleCopy.setName("  Trip 2026 ");
        staleCopy.setDescription(null);
        Result<Void> notOwner = new Result<>();
        groupService.updateGroup(staleCopy, "bob", notOwner);
        notOwner.assertInvalid("permission", "Only the owner can edit this group");
        assertEquals("Trip", groups.stored(trip).getName());

        Result<Void> renamed = new Result<>();
        groupService.updateGroup(staleCopy, "alice", renamed);
        renamed.assertOk();
        Group saved = groups.stored(trip);
        assertEquals("Trip 2026", saved.getName());
        assertEquals("", saved.getDescription());
        assertEquals("the old copy must not drop Carol", Arrays.asList("alice", "carol"), saved.getMemberIds());

        staleCopy.setName(" ");
        Result<Void> blank = new Result<>();
        groupService.updateGroup(staleCopy, "alice", blank);
        blank.assertInvalid("name", "Enter a group name");

        newGroup("apartment", "alice");
        newGroup("Book club", "alice");
        Result<List<Group>> mine = new Result<>();
        groupService.getGroupsFor("alice", mine);
        assertEquals(Arrays.asList("apartment", "Book club", "Trip 2026"), names(mine.assertOk()));
        Result<List<Group>> signedOut = new Result<>();
        groupService.getGroupsFor(null, signedOut);
        assertEquals("Not signed in", signedOut.assertFailed().getMessage());

        Result<Void> deleteByMember = new Result<>();
        groupService.deleteGroup(trip, "carol", deleteByMember);
        deleteByMember.assertInvalid("permission", "Only the owner can delete this group");
        assertEquals(0, groups.deletes);

        Result<Void> deleted = new Result<>();
        groupService.deleteGroup(trip, "alice", deleted);
        deleted.assertOk();
        assertNull(groups.stored(trip));
        Result<List<Group>> after = new Result<>();
        groupService.getGroupsFor("carol", after);
        assertTrue(after.assertOk().isEmpty());
    }

    // ---------- helpers ----------

    private String newGroup(String name, String owner) {
        Result<Group> created = new Result<>();
        groupService.createGroup(name, "", owner, created);
        return created.assertOk().getGroupId();
    }

    private static List<String> names(List<Group> list) {
        List<String> out = new ArrayList<>();
        for (Group g : list) {
            out.add(g.getName());
        }
        return out;
    }

    /** Captures one callback and checks it was called exactly once. */
    private static final class Result<T> implements Callback<T> {
        private T value;
        private Exception error;
        private int calls;

        Result<T> run(java.util.function.Consumer<Callback<T>> action) {
            action.accept(this);
            return this;
        }

        @Override
        public void onSuccess(T result) {
            value = result;
            calls++;
        }

        @Override
        public void onFailure(Exception e) {
            error = e;
            calls++;
        }

        T assertOk() {
            assertEquals("callback calls", 1, calls);
            assertNull("unexpected failure: " + error, error);
            return value;
        }

        Exception assertFailed() {
            assertEquals("callback calls", 1, calls);
            assertNotNull("expected a failure", error);
            return error;
        }

        void assertInvalid(String field, String message) {
            Exception e = assertFailed();
            assertTrue("expected ValidationException but got " + e, e instanceof ValidationException);
            assertEquals(field, ((ValidationException) e).getField());
            assertEquals(message, e.getMessage());
        }
    }

    /** In-memory users that record each lookup ("email:..." or "uscId:...") exactly as received. */
    private static final class FakeUsers extends UserRepository {
        final Map<String, User> byUid = new LinkedHashMap<>();
        final List<String> lookups = new ArrayList<>();
        Exception failNextLookup;

        FakeUsers() {
            super(null);
        }

        void add(User user) {
            byUid.put(user.getUid(), user);
        }

        @Override
        public void findByEmail(String email, Callback<User> callback) {
            lookups.add("email:" + email);
            if (failNextLookup != null) {
                Exception e = failNextLookup;
                failNextLookup = null;
                callback.onFailure(e);
                return;
            }
            for (User u : byUid.values()) {
                if (u.getUscEmail().equals(email)) {
                    callback.onSuccess(u);
                    return;
                }
            }
            callback.onSuccess(null);
        }

        @Override
        public void findByUscId(String uscId, Callback<User> callback) {
            lookups.add("uscId:" + uscId);
            for (User u : byUid.values()) {
                if (u.getUscId().equals(uscId)) {
                    callback.onSuccess(u);
                    return;
                }
            }
            callback.onSuccess(null);
        }

        @Override
        public void findById(String uid, Callback<User> callback) {
            callback.onSuccess(byUid.get(uid));
        }

        @Override
        public void findByIds(List<String> uids, Callback<Map<String, User>> callback) {
            Map<String, User> found = new LinkedHashMap<>();
            for (String uid : uids) {
                if (byUid.containsKey(uid)) {
                    found.put(uid, byUid.get(uid));
                }
            }
            callback.onSuccess(found);
        }

        @Override
        public void save(User user, Callback<Void> callback) {
            byUid.put(user.getUid(), user);
            callback.onSuccess(null);
        }
    }

    /** In-memory groups that store copies (like a database) and count writes. */
    private static final class FakeGroups extends GroupRepository {
        private final Map<String, Group> byId = new LinkedHashMap<>();
        private int nextId = 1;
        int saves;
        int deletes;
        Exception failNextRead;

        FakeGroups() {
            super(null);
        }

        Group stored(String id) {
            Group g = byId.get(id);
            return g == null ? null : copy(g);
        }

        private static Group copy(Group g) {
            Group c = new Group();
            c.setGroupId(g.getGroupId());
            c.setName(g.getName());
            c.setDescription(g.getDescription());
            c.setOwnerId(g.getOwnerId());
            c.setMemberIds(new ArrayList<>(g.getMemberIds()));
            return c;
        }

        @Override
        public void save(Group group, Callback<Group> callback) {
            if (group.getGroupId() == null) {
                group.setGroupId("g" + nextId++);
            }
            byId.put(group.getGroupId(), copy(group));
            saves++;
            callback.onSuccess(group);
        }

        @Override
        public void findById(String groupId, Callback<Group> callback) {
            if (failNextRead != null) {
                Exception e = failNextRead;
                failNextRead = null;
                callback.onFailure(e);
                return;
            }
            callback.onSuccess(stored(groupId));
        }

        @Override
        public void findByMember(String userId, Callback<List<Group>> callback) {
            List<Group> found = new ArrayList<>();
            for (Group g : byId.values()) {
                if (g.hasMember(userId)) {
                    found.add(copy(g));
                }
            }
            callback.onSuccess(found);
        }

        @Override
        public void delete(String groupId, Callback<Void> callback) {
            byId.remove(groupId);
            deletes++;
            callback.onSuccess(null);
        }
    }
}
