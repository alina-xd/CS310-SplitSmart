package com.team19.splitsmart;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.firebase.firestore.FirebaseFirestore;
import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.model.SplitMethod;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.service.ExpenseService;
import com.team19.splitsmart.service.GroupService;
import com.team19.splitsmart.service.SettlementService;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.ui.account.RegisterActivity;
import com.team19.splitsmart.ui.group.GroupActivity;
import com.team19.splitsmart.ui.group.GroupListActivity;
import com.team19.splitsmart.util.Callback;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Part A black-box tests (author: Alina Du). Espresso instrumented tests that drive the account
 * and group screens the way a user would and check what the screens show.
 *
 * Like FullAppIntegrationTest, they run against the local Firebase emulators (EmulatorTestRunner),
 * never the shared cloud database. See tests/README.md for how to start the emulators.
 */
@RunWith(AndroidJUnit4.class)
public class PartAAccountsGroupsTest {

    private static final String PASSWORD = "part-a-password";
    private static final String ACCOUNT_EXISTS = "An account with this USC email or USC ID already exists.";
    private static final long TIMEOUT_MS = 20000;

    private static final AuthService auth = new AuthService();
    private static final GroupService groups = new GroupService();
    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static User owner;
    private static User member;
    /** 9xxxxxxxxx: outside the 1xxxxxxxxx-8xxxxxxxxx range FullAppIntegrationTest generates. */
    private static long nextId = 9000000000L + Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 990000000L);

    private final List<String> createdGroups = new ArrayList<>();

    @BeforeClass
    public static void registerOwnerAndMember() throws Exception {
        assertEquals("tests must use the local emulator",
                "10.0.2.2:8080", FirebaseFirestore.getInstance().getFirestoreSettings().getHost());
        member = PartAAccountsGroupsTest.<User>await(cb ->
                auth.register("Bob " + RUN, email("member"), freshUscId(), PASSWORD, cb));
        owner = PartAAccountsGroupsTest.<User>await(cb ->
                auth.register("Alice " + RUN, email("owner"), freshUscId(), PASSWORD, cb));
    }

    @After
    public void cleanUp() throws Exception {
        finishAllActivities();
        signIn(owner);
        for (String id : createdGroups) {
            try {
                PartAAccountsGroupsTest.<Void>await(cb -> groups.deleteGroup(id, owner.getUid(), cb));
            } catch (Exception alreadyDeleted) {
                // the test deleted it itself
            }
        }
    }

    /**
     * BB-A1: wrong formats are rejected on the Register screen, what was typed stays, and no
     * account is created. Inputs: one invalid email class (non-@usc.edu) and the three invalid
     * USC ID classes (too short, too long, 10 characters containing a letter).
     */
    @Test
    public void register_rejectsWrongEmailAndIdFormats_keepsInput_createsNoAccount() throws Exception {
        auth.logout();
        try (ActivityScenario<RegisterActivity> screen = ActivityScenario.launch(RegisterActivity.class)) {
            type(R.id.editName, "Format Tester");
            type(R.id.editEmail, "format.tester@gmail.com");
            type(R.id.editUscId, "123456789");
            type(R.id.editPassword, PASSWORD);
            onView(withId(R.id.buttonCreateAccount)).perform(click());
            waitForView(withId(R.id.editEmail), hasErrorText("Use your @usc.edu email"));
            waitForView(withId(R.id.editUscId), hasErrorText("USC ID must be exactly 10 digits"));
            onView(withId(R.id.editEmail)).check(matches(withText("format.tester@gmail.com")));
            onView(withId(R.id.editName)).check(matches(withText("Format Tester")));

            for (String badId : new String[] {"12345678901", "123456789a"}) {
                clearError(screen, R.id.editUscId);
                type(R.id.editUscId, badId);
                onView(withId(R.id.buttonCreateAccount)).perform(click());
                waitForView(withId(R.id.editUscId), hasErrorText("USC ID must be exactly 10 digits"));
            }
            onView(withId(R.id.buttonCreateAccount)).check(matches(isDisplayed()));
        }
        assertFalse("invalid input must not sign anyone in", auth.isSignedIn());
    }

    /**
     * BB-A2: a registered email (typed in capitals) with a new USC ID is rejected, then a
     * registered USC ID with a new email is rejected. Both stay on the Register screen.
     */
    @Test
    public void register_rejectsDuplicateEmailThenDuplicateUscId() throws Exception {
        auth.logout();
        try (ActivityScenario<RegisterActivity> screen = ActivityScenario.launch(RegisterActivity.class)) {
            type(R.id.editName, "Duplicate Tester");
            type(R.id.editEmail, owner.getUscEmail().toUpperCase(Locale.US));
            type(R.id.editUscId, freshUscId());
            type(R.id.editPassword, PASSWORD);
            onView(withId(R.id.buttonCreateAccount)).perform(click());
            waitForView(withId(R.id.editEmail), hasErrorText(ACCOUNT_EXISTS));

            clearError(screen, R.id.editEmail);
            type(R.id.editEmail, email("unused"));
            type(R.id.editUscId, owner.getUscId());
            onView(withId(R.id.buttonCreateAccount)).perform(click());
            waitForView(withId(R.id.editUscId), hasErrorText(ACCOUNT_EXISTS));
            onView(withId(R.id.buttonCreateAccount)).check(matches(isDisplayed()));
        }
        assertFalse("a rejected registration must not sign anyone in", auth.isSignedIn());
    }

    /**
     * BB-A3: the full account cycle through the screens: register, land on an empty group list,
     * see email and USC ID as read-only text on Profile, log out, fail with a wrong password,
     * then log back in with the email typed in capitals.
     */
    @Test
    public void register_viewProfile_logOut_andLogBackIn() throws Exception {
        auth.logout();
        String name = "Carol " + RUN;
        String newEmail = email("carol");
        String newId = freshUscId();
        try (ActivityScenario<RegisterActivity> ignored = ActivityScenario.launch(RegisterActivity.class)) {
            type(R.id.editName, name);
            type(R.id.editEmail, newEmail);
            type(R.id.editUscId, newId);
            type(R.id.editPassword, PASSWORD);
            onView(withId(R.id.buttonCreateAccount)).perform(click());

            waitForView(withId(R.id.textEmpty), withText(containsString("No groups yet")));
            assertTrue(auth.isSignedIn());
            String uid = auth.getCurrentUserId();

            onView(withId(R.id.buttonProfile)).perform(click());
            waitForView(withId(R.id.editName), withText(name));
            onView(withId(R.id.textEmail)).check(matches(allOf(
                    withText("USC email: " + newEmail), not(isAssignableFrom(EditText.class)))));
            onView(withId(R.id.textUscId)).check(matches(allOf(
                    withText("USC ID: " + newId), not(isAssignableFrom(EditText.class)))));

            onView(withId(R.id.buttonLogOut)).perform(click());
            waitForView(withId(R.id.buttonLogin), isDisplayed());
            assertFalse(auth.isSignedIn());

            type(R.id.editEmail, newEmail);
            type(R.id.editPassword, "wrong-password");
            onView(withId(R.id.buttonLogin)).perform(click());
            waitForView(withId(R.id.textLoginError), withText("Incorrect email or password"));

            type(R.id.editEmail, newEmail.toUpperCase(Locale.US));
            type(R.id.editPassword, PASSWORD);
            onView(withId(R.id.buttonLogin)).perform(click());
            waitForView(withId(R.id.buttonCreateGroup), isDisplayed());
            assertEquals(uid, auth.getCurrentUserId());
        }
    }

    /**
     * BB-A4: on the group screen the owner gets a message for an unregistered email, adds a real
     * member (email typed in capitals), and gets a message for adding them again. The member then
     * sees both of their groups and the member list, with no owner controls.
     */
    @Test
    public void owner_addsMembersWithMessages_memberSeesGroupsButNoOwnerControls() throws Exception {
        signIn(owner);
        String groupName = "PA Members " + RUN;
        String otherName = "PA Second " + RUN;
        String groupId = newGroup(groupName);
        String otherId = newGroup(otherName);
        PartAAccountsGroupsTest.<Void>await(cb ->
                groups.addMember(otherId, member.getUscEmail(), owner.getUid(), cb));

        try (ActivityScenario<GroupActivity> screen = launchGroup(groupId)) {
            waitForView(withId(R.id.textGroupName), withText(groupName));
            waitForView(withId(R.id.layoutOwnerActions), isDisplayed());

            type(R.id.editMemberEmail, "nobody-" + RUN + "@usc.edu");
            onView(withId(R.id.buttonAddMember)).perform(click());
            waitForView(withId(R.id.editMemberEmail), hasErrorText("No SplitSmart account uses this email"));

            clearError(screen, R.id.editMemberEmail);
            type(R.id.editMemberEmail, member.getUscEmail().toUpperCase(Locale.US));
            onView(withId(R.id.buttonAddMember)).perform(click());
            waitForView(withId(R.id.textGroupMembers), withText(containsString(member.getName())));

            type(R.id.editMemberEmail, member.getUscEmail());
            onView(withId(R.id.buttonAddMember)).perform(click());
            waitForView(withId(R.id.editMemberEmail), hasErrorText("This user is already in the group"));
        }
        finishAllActivities();

        signIn(member);
        try (ActivityScenario<GroupListActivity> ignored = ActivityScenario.launch(GroupListActivity.class)) {
            waitForListItem(R.id.listGroups, groupName);
            waitForListItem(R.id.listGroups, otherName);
        }
        try (ActivityScenario<GroupActivity> ignored = launchGroup(groupId)) {
            waitForView(withId(R.id.textGroupMembers), withText(allOf(
                    containsString(owner.getName() + " (owner)"),
                    containsString(member.getName() + " (you)"))));
            onView(withId(R.id.layoutOwnerActions))
                    .check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    /**
     * BB-A5: the owner renames a group from the group screen, cancels the delete dialog (nothing
     * is removed), then confirms it: the group, its expense and its settlement are all gone.
     */
    @Test
    public void owner_renamesGroup_cancelsThenConfirmsDelete_removingItsRecords() throws Exception {
        signIn(owner);
        String oldName = "PA Hot pot " + RUN;
        String newName = "PA Hot pot renamed " + RUN;
        String groupId = newGroup(oldName);
        PartAAccountsGroupsTest.<Void>await(cb ->
                groups.addMember(groupId, member.getUscEmail(), owner.getUid(), cb));
        addExpenseAndSettlement(groupId);
        TransactionRepository transactions = new TransactionRepository();
        assertEquals(2, PartAAccountsGroupsTest.<List<Transaction>>await(cb ->
                transactions.findByGroup(groupId, cb)).size());

        try (ActivityScenario<GroupActivity> ignored = launchGroup(groupId)) {
            waitForView(withId(R.id.textGroupName), withText(oldName));
            onView(withId(R.id.buttonEditGroup)).perform(click());
            waitForView(withId(R.id.editGroupName), withText(oldName));
            type(R.id.editGroupName, newName);
            onView(withId(R.id.buttonSave)).perform(click());
            waitForView(withId(R.id.textGroupName), withText(newName));

            onView(withId(R.id.buttonDeleteGroup)).perform(click());
            onView(withText("Cancel")).inRoot(isDialog()).perform(click());
            waitForView(withId(R.id.textGroupName), withText(newName));
            assertNotNull("cancel must keep the group", PartAAccountsGroupsTest.<Group>await(cb ->
                    groups.getGroup(groupId, cb)));

            onView(withId(R.id.buttonDeleteGroup)).perform(click());
            onView(withText("Delete")).inRoot(isDialog()).perform(click());
            waitFor(() -> {
                try {
                    return PartAAccountsGroupsTest.<Group>await(cb -> groups.getGroup(groupId, cb)) == null;
                } catch (Exception e) {
                    return false;
                }
            });
        }
        assertNull(PartAAccountsGroupsTest.<Group>await(cb -> groups.getGroup(groupId, cb)));
        assertTrue("the group's expense and settlement must be deleted too",
                PartAAccountsGroupsTest.<List<Transaction>>await(cb ->
                        transactions.findByGroup(groupId, cb)).isEmpty());
        List<Group> ownersGroups = await(cb -> groups.getGroupsFor(owner.getUid(), cb));
        for (Group g : ownersGroups) {
            assertFalse(g.getGroupId().equals(groupId));
        }
    }

    // ---------- helpers ----------

    private static String email(String who) {
        return "pa-" + who + "-" + RUN + "-" + UUID.randomUUID().toString().substring(0, 4) + "@usc.edu";
    }

    private static synchronized String freshUscId() {
        return String.valueOf(nextId++);
    }

    private static void signIn(User user) throws Exception {
        auth.logout();
        PartAAccountsGroupsTest.<User>await(cb -> auth.login(user.getUscEmail(), PASSWORD, cb));
    }

    private String newGroup(String name) throws Exception {
        Group g = await(cb -> groups.createGroup(name, "Part A black-box test", owner.getUid(), cb));
        createdGroups.add(g.getGroupId());
        return g.getGroupId();
    }

    /** Owner pays $20.00 split equally with the member; the member then pays back $5.00. */
    private void addExpenseAndSettlement(String groupId) throws Exception {
        Expense expense = new Expense();
        expense.setGroupId(groupId);
        expense.setTitle("Hot pot dinner");
        expense.setCategory(Category.FOOD);
        expense.setAmountCents(2000);
        expense.setPayerId(owner.getUid());
        expense.setCreatorId(owner.getUid());
        expense.setSplitMethod(SplitMethod.EQUAL);
        Map<String, Long> shares = new LinkedHashMap<>();
        shares.put(owner.getUid(), 1000L);
        shares.put(member.getUid(), 1000L);
        expense.setShares(shares);
        PartAAccountsGroupsTest.<Void>await(cb -> new ExpenseService().createExpense(expense, cb));

        Settlement settlement = new Settlement();
        settlement.setGroupId(groupId);
        settlement.setPayerId(member.getUid());
        settlement.setRecipientId(owner.getUid());
        settlement.setCreatorId(member.getUid());
        settlement.setAmountCents(500);
        settlement.setNote("Venmo");
        PartAAccountsGroupsTest.<Void>await(cb -> new SettlementService().createSettlement(settlement, cb));
    }

    private static ActivityScenario<GroupActivity> launchGroup(String groupId) {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                GroupActivity.class).putExtra(Extras.GROUP_ID, groupId);
        return ActivityScenario.launch(intent);
    }

    private static void type(int viewId, String text) {
        onView(withId(viewId)).perform(replaceText(text), closeSoftKeyboard());
    }

    /** Removes an old error so the next check proves the error was shown again. */
    private static <A extends Activity> void clearError(ActivityScenario<A> screen, int viewId) {
        screen.onActivity(a -> ((EditText) a.findViewById(viewId)).setError(null));
    }

    /** Firebase calls are asynchronous, so keep checking until the view matches or time runs out. */
    private static void waitForView(Matcher<View> view, Matcher<View> state) {
        long end = SystemClock.uptimeMillis() + TIMEOUT_MS;
        Throwable last = null;
        while (SystemClock.uptimeMillis() < end) {
            try {
                onView(view).check(matches(state));
                return;
            } catch (Throwable notYet) {
                last = notYet;
                SystemClock.sleep(200);
            }
        }
        throw new AssertionError("Timed out waiting for " + state, last);
    }

    private static void waitForListItem(int listId, String item) {
        long end = SystemClock.uptimeMillis() + TIMEOUT_MS;
        Throwable last = null;
        while (SystemClock.uptimeMillis() < end) {
            try {
                onData(is(item)).inAdapterView(withId(listId)).check(matches(isDisplayed()));
                return;
            } catch (Throwable notYet) {
                last = notYet;
                SystemClock.sleep(200);
            }
        }
        throw new AssertionError("Timed out waiting for list item " + item, last);
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) {
        long end = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            SystemClock.sleep(200);
        }
        assertTrue("Timed out waiting", condition.getAsBoolean());
    }

    /** Closes screens a test opened by navigating, so the next test starts clean. */
    private static void finishAllActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Stage stage : EnumSet.of(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.STARTED, Stage.CREATED)) {
                for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)) {
                    a.finish();
                }
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static <T> T await(Consumer<Callback<T>> action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        action.accept(new Callback<T>() {
            @Override
            public void onSuccess(T result) {
                value.set(result);
                done.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                done.countDown();
            }
        });
        assertTrue("Firebase callback timed out", done.await(25, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw error.get();
        }
        return value.get();
    }
}
