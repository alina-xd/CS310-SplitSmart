package com.team19.splitsmart;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.view.View;
import android.widget.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.team19.splitsmart.data.model.*;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.service.*;
import com.team19.splitsmart.testing.RemoteUserService;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.ui.account.LoginActivity;
import com.team19.splitsmart.ui.account.ProfileActivity;
import com.team19.splitsmart.ui.account.RegisterActivity;
import com.team19.splitsmart.ui.group.GroupActivity;
import com.team19.splitsmart.ui.group.GroupFormActivity;
import com.team19.splitsmart.ui.group.GroupListActivity;
import com.team19.splitsmart.ui.summary.*;
import com.team19.splitsmart.ui.transaction.*;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.DateUtil;

import org.junit.*;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Runs real Android activities and Firebase SDKs against the local Auth/Firestore emulators. */
@RunWith(AndroidJUnit4.class)
public class FullAppIntegrationTest {
    private static final String PASSWORD = "test-password-310";
    private static final List<User> users = new ArrayList<>();
    private static final AuthService auth = new AuthService();
    private final GroupService groups = new GroupService();
    private final ExpenseService expenses = new ExpenseService();
    private final SettlementService settlements = new SettlementService();
    private final BalanceService balances = new BalanceService();
    private final HistoryService history = new HistoryService();
    private final TransactionRepository transactions = new TransactionRepository();
    private Group group;
    private String a, b, c, d;
    private String navigationGroupId;

    @BeforeClass
    public static void registerUsers() throws Exception {
        assertEquals(
                "10.0.2.2:8080", FirebaseFirestore.getInstance().getFirestoreSettings().getHost());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long idBase = 1000000000L + Math.floorMod(System.currentTimeMillis(), 8000000000L);
        for (int i = 0; i < 5; i++) {
            final int index = i;
            User user =
                    await(
                            cb ->
                                    auth.register(
                                            new String[] {
                                                "Alice", "Bob", "Carol", "David", "Eve"
                                            }[index],
                                            "test-" + suffix + "-" + index + "@usc.edu",
                                            String.valueOf(idBase + index),
                                            PASSWORD,
                                            cb));
            users.add(user);
        }
    }

    @Before
    public void createGroup() throws Exception {
        a = users.get(0).getUid();
        b = users.get(1).getUid();
        c = users.get(2).getUid();
        d = users.get(3).getUid();
        FullAppIntegrationTest.<User>await(
                cb -> auth.login(users.get(0).getUscEmail(), PASSWORD, cb));
        group =
                await(
                        cb ->
                                groups.createGroup(
                                        "Integration Trip", "Test data in local emulators", a, cb));
        for (int i = 1; i < 4; i++) {
            final int index = i;
            FullAppIntegrationTest.<Void>await(
                    cb ->
                            groups.addMember(
                                    group.getGroupId(), users.get(index).getUscEmail(), a, cb));
        }
    }

    @After
    public void removeGroup() throws Exception {
        if (group != null)
            FullAppIntegrationTest.<Void>await(cb -> groups.deleteGroup(group.getGroupId(), a, cb));
        if (navigationGroupId != null)
            FullAppIntegrationTest.<Void>await(
                    cb -> groups.deleteGroup(navigationGroupId, a, cb));
    }

    @Test
    public void accountsAndGroupPermissions() throws Exception {
        assertTrue(auth.isValidUscEmail("Student@USC.EDU"));
        assertFalse(auth.isValidUscEmail("student@gmail.com"));
        for (String id : new String[] {"123456789", "12345678901", "123456789x"})
            assertFalse(auth.isValidUscId(id));
        FullAppIntegrationTest.<User>assertError(
                "email",
                cb -> auth.register("Test", "bad@example.com", "1234567890", PASSWORD, cb));
        FullAppIntegrationTest.<User>assertError(
                "email",
                cb ->
                        auth.register(
                                "Test",
                                users.get(0).getUscEmail().toUpperCase(Locale.US),
                                "9999999999",
                                PASSWORD,
                                cb));
        FullAppIntegrationTest.<User>assertError(
                "uscId",
                cb ->
                        auth.register(
                                "Test",
                                "unique-" + UUID.randomUUID() + "@usc.edu",
                                users.get(0).getUscId(),
                                PASSWORD,
                                cb));
        FullAppIntegrationTest.<Void>assertError("name", cb -> auth.updateName(" ", cb));
        FullAppIntegrationTest.<Void>await(cb -> auth.updateName("Alice Updated", cb));
        User updated = await(auth::getCurrentUser);
        assertEquals("Alice Updated", updated.getName());
        FullAppIntegrationTest.<Void>await(cb -> auth.updateName("Alice", cb));
        auth.logout();
        assertFalse(auth.isSignedIn());
        FullAppIntegrationTest.<User>assertError(
                "login", cb -> auth.login(users.get(0).getUscEmail(), "wrong-password", cb));
        User logged =
                await(
                        cb ->
                                auth.login(
                                        users.get(0).getUscEmail().toUpperCase(Locale.US),
                                        PASSWORD,
                                        cb));
        assertEquals(a, logged.getUid());
        FullAppIntegrationTest.<Void>assertError(
                "permission", cb -> groups.addMember(group.getGroupId(), "none@usc.edu", b, cb));
        FullAppIntegrationTest.<Void>assertError(
                "email",
                cb -> groups.addMember(group.getGroupId(), users.get(1).getUscEmail(), a, cb));
        FullAppIntegrationTest.<Void>assertError(
                "email", cb -> groups.addMember(group.getGroupId(), "nonexistent@usc.edu", a, cb));
        group.setName("Renamed Trip");
        FullAppIntegrationTest.<Void>await(cb -> groups.updateGroup(group, a, cb));
        Group found = await(cb -> groups.getGroup(group.getGroupId(), cb));
        assertEquals("Renamed Trip", found.getName());
        assertEquals(4, found.getMemberIds().size());
        FullAppIntegrationTest.<Void>assertError(
                "permission", cb -> groups.deleteGroup(group.getGroupId(), b, cb));
        List<Group> bGroups = await(cb -> groups.getGroupsFor(b, cb));
        assertTrue(bGroups.stream().anyMatch(g -> g.getGroupId().equals(group.getGroupId())));
    }

    @Test
    public void expenseSplitsValidationEditingAndCascadeDeletion() throws Exception {
        Expense equal = expense("Dinner", a, 3000, SplitMethod.EQUAL, a, 0.0, b, 0.0, c, 0.0);
        equal.setCreatorId(d); // A member need not participate to record it.
        FullAppIntegrationTest.<Void>await(cb -> expenses.createExpense(equal, cb));
        assertEquals(1000, equal.getShare(b));
        assertEquals(DateUtil.today(), equal.getCreatedDate());
        assertEquals(Category.OTHER, equal.getCategory());
        FullAppIntegrationTest.<Void>assertError(
                "permission", cb -> expenses.deleteExpense(equal, a, cb));
        Expense bad = expense("bad", a, 100, SplitMethod.EQUAL, a, 0.0, b, 0.0);
        bad.getShares().put(a, -1L);
        bad.getShares().put(b, 101L);
        FullAppIntegrationTest.<Void>assertError("split", cb -> expenses.createExpense(bad, cb));
        bad.setAmountCents(0);
        FullAppIntegrationTest.<Void>assertError("amount", cb -> expenses.createExpense(bad, cb));
        Expense exact =
                expense("Taxi", b, 3000, SplitMethod.EXACT_AMOUNT, a, 5.0, b, 10.0, c, 15.0);
        FullAppIntegrationTest.<Void>await(cb -> expenses.createExpense(exact, cb));
        assertEquals(1500, exact.getShare(c));
        Expense percent =
                expense("Tickets", c, 3000, SplitMethod.PERCENTAGE, a, 20.0, b, 30.0, c, 50.0);
        FullAppIntegrationTest.<Void>await(cb -> expenses.createExpense(percent, cb));
        assertEquals(600, percent.getShare(a));
        String date = equal.getCreatedDate();
        long seq = equal.getSequenceNo();
        equal.setTitle("Updated dinner");
        equal.setCreatedDate("2099-01-01");
        equal.setSequenceNo(999);
        FullAppIntegrationTest.<Void>await(cb -> expenses.updateExpense(equal, d, cb));
        assertEquals(date, equal.getCreatedDate());
        assertEquals(seq, equal.getSequenceNo());
        FullAppIntegrationTest.<Void>await(cb -> expenses.deleteExpense(equal, d, cb));
        assertEquals(2, all().size());
        String id = group.getGroupId();
        FullAppIntegrationTest.<Void>await(cb -> groups.deleteGroup(id, a, cb));
        List<Transaction> remaining = await(cb -> transactions.findByGroup(id, cb));
        assertTrue(remaining.isEmpty());
        group = null;
    }

    @Test
    public void settlementLifecycleAndLocksAcrossServices() throws Exception {
        Expense expense = expense("Shared lunch", b, 2200, SplitMethod.EQUAL, a, 0.0, b, 0.0);
        FullAppIntegrationTest.<Void>await(cb -> expenses.createExpense(expense, cb));
        assertEquals(-1100, pair(a, b));
        Settlement s = payment(a, b, b, 500);
        FullAppIntegrationTest.<Void>await(cb -> settlements.createSettlement(s, cb));
        assertEquals(-600, pair(a, b));
        assertTrue(FullAppIntegrationTest.<Boolean>await(cb -> settlements.isLocked(expense, cb)));
        FullAppIntegrationTest.<Void>assertError(
                "permission", cb -> expenses.updateExpense(expense, b, cb));
        FullAppIntegrationTest.<Void>assertError(
                "permission", cb -> expenses.deleteExpense(expense, b, cb));
        FullAppIntegrationTest.<Void>assertError(
                "permission", cb -> settlements.deleteSettlement(s, a, cb));
        Settlement tooMuch = payment(a, b, a, 601);
        FullAppIntegrationTest.<Void>assertError(
                "amount", cb -> settlements.createSettlement(tooMuch, cb));
        s.setAmountCents(1000);
        FullAppIntegrationTest.<Void>await(cb -> settlements.updateSettlement(s, b, cb));
        assertEquals(-100, pair(a, b));
        FullAppIntegrationTest.<Void>await(cb -> settlements.deleteSettlement(s, b, cb));
        assertEquals(-1100, pair(a, b));
        assertFalse(FullAppIntegrationTest.<Boolean>await(cb -> settlements.isLocked(expense, cb)));
        FullAppIntegrationTest.<Void>await(cb -> expenses.deleteExpense(expense, b, cb));
        assertEquals(0, pair(a, b));
    }

    @Test
    public void onlySignedInGroupMembersCanCreateExpensesOrSettlements() throws Exception {
        String outsider = users.get(4).getUid();
        try {
            FullAppIntegrationTest.<User>await(
                    cb -> auth.login(users.get(4).getUscEmail(), PASSWORD, cb));

            Expense outsiderExpense =
                    expense("Outsider expense", a, 1000, SplitMethod.EQUAL, a, 0.0, b, 0.0);
            outsiderExpense.setCreatorId(outsider);
            FullAppIntegrationTest.<Void>assertError(
                    "permission", cb -> expenses.createExpense(outsiderExpense, cb));

            Settlement outsiderSettlement = payment(outsider, a, outsider, 100);
            FullAppIntegrationTest.<Void>assertError(
                    "member", cb -> settlements.createSettlement(outsiderSettlement, cb));

            auth.logout();
            assertFalse(auth.isSignedIn());
            Expense unsignedExpense =
                    expense("Unsigned expense", a, 1000, SplitMethod.EQUAL, a, 0.0, b, 0.0);
            FullAppIntegrationTest.<Void>assertError(
                    "permission", cb -> expenses.createExpense(unsignedExpense, cb));

            Settlement unsignedSettlement = payment(a, b, a, 100);
            FullAppIntegrationTest.<Void>assertError(
                    "permission", cb -> settlements.createSettlement(unsignedSettlement, cb));
        } finally {
            auth.logout();
            FullAppIntegrationTest.<User>await(
                    cb -> auth.login(users.get(0).getUscEmail(), PASSWORD, cb));
        }
    }

    @Test
    public void multipleUsersCollaborateOnSharedGroup() throws Exception {
        Expense shared = expense("Shared dinner", a, 2000, SplitMethod.EQUAL, a, 0.0, b, 0.0);
        FullAppIntegrationTest.<Void>await(cb -> expenses.createExpense(shared, cb));
        assertEquals(1000, pair(a, b));

        try (ActivityScenario<BalanceActivity> alice = launch(BalanceActivity.class, null)) {
            AtomicReference<Integer> appProcessId = new AtomicReference<>();
            alice.onActivity(activity -> appProcessId.set(android.os.Process.myPid()));
            waitFor(() -> text(alice, R.id.textToReceive).equals("To receive: $10.00"));

            int bobProcessId =
                    recordSettlementInRemoteProcess(
                            users.get(1).getUscEmail(), group.getGroupId(), a, 500);
            assertTrue(
                    "Bob's actions must run in a separate Android process",
                    bobProcessId != appProcessId.get());

            waitFor(() -> text(alice, R.id.textToReceive).equals("To receive: $5.00"));
            assertEquals(500, pair(a, b));
            Settlement recorded =
                    all().stream()
                            .filter(t -> t instanceof Settlement)
                            .map(t -> (Settlement) t)
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new AssertionError(
                                                    "Bob's settlement was not saved"));
            assertEquals(b, recorded.getCreatorId());
        }
    }

    @Test
    public void historyFiltersMonthlyTotalsAndServerPersistence() throws Exception {
        Expense oct = expense("Dinner", a, 3000, SplitMethod.EQUAL, a, 0.0, b, 0.0, c, 0.0);
        oct.setCategory(Category.FOOD);
        oct.setNote("Thai place");
        FullAppIntegrationTest.<Void>await(cb -> expenses.createExpense(oct, cb));
        oct.setCreatedDate("2026-10-12");
        FullAppIntegrationTest.<Void>await(cb -> transactions.update(oct, cb));
        Expense nov = expense("Movie", c, 1000, SplitMethod.EQUAL, a, 0.0, b, 0.0, c, 0.0);
        nov.setCategory(Category.ENTERTAINMENT);
        nov.setNote("IMAX");
        FullAppIntegrationTest.<Void>await(cb -> expenses.createExpense(nov, cb));
        nov.setCreatedDate("2026-11-02");
        FullAppIntegrationTest.<Void>await(cb -> transactions.update(nov, cb));
        Settlement s = payment(b, a, b, 300);
        s.setNote("Venmo");
        FullAppIntegrationTest.<Void>await(cb -> settlements.createSettlement(s, cb));
        HistoryFilter filter = new HistoryFilter();
        filter.setKeyword("IMAX");
        assertEquals(1, filtered(filter).size());
        filter.setKeyword("imax");
        assertEquals(0, filtered(filter).size());
        filter.setKeyword("Venmo");
        assertEquals(1, filtered(filter).size());
        filter.setKeyword(null);
        filter.setCategory(Category.FOOD);
        assertEquals(1, filtered(filter).size());
        filter.setCategory(null);
        filter.setType(Transaction.TYPE_SETTLEMENT);
        assertEquals(1, filtered(filter).size());
        filter.setType(null);
        filter.setMemberId(d);
        assertTrue(filtered(filter).isEmpty());
        filter.setMemberId(null);
        filter.setNewestFirst(false);
        assertEquals(oct.getId(), filtered(filter).get(0).getId());
        MonthlySummary summary =
                await(cb -> history.getMonthlySummary(group.getGroupId(), "2026-10", cb));
        assertEquals(3000, summary.getTotalCents());
        assertEquals(1, summary.getExpenseCount());
        assertTrue(
                Tasks.await(
                                FirebaseFirestore.getInstance()
                                        .collection("groups")
                                        .document(group.getGroupId())
                                        .collection("transactions")
                                        .document(nov.getId())
                                        .get(Source.SERVER),
                                20,
                                TimeUnit.SECONDS)
                        .exists());
        try (ActivityScenario<MonthlySummaryActivity> screen =
                launch(MonthlySummaryActivity.class, null)) {
            waitFor(() -> {
                AtomicReference<Boolean> found = new AtomicReference<>(false);
                screen.onActivity(activity -> {
                    Spinner months = activity.findViewById(R.id.spinnerMonth);
                    for (int i = 0; i < months.getCount(); i++) {
                        if ("November 2026".equals(months.getItemAtPosition(i).toString())) {
                            months.setSelection(i);
                            found.set(true);
                            break;
                        }
                    }
                });
                return found.get();
            });
            waitFor(() -> text(screen, R.id.textTotalSpending).contains("$10.00"));
            screenshot("monthly-summary");
        }
    }

    @Test
    public void balanceScreenUpdatesFromListenerAndSurvivesReopen() throws Exception {
        Expense e = expense("Dinner", a, 2000, SplitMethod.EQUAL, a, 0.0, b, 0.0);
        FullAppIntegrationTest.<Void>await(cb -> expenses.createExpense(e, cb));
        try (ActivityScenario<BalanceActivity> screen = launch(BalanceActivity.class, null)) {
            waitFor(() -> text(screen, R.id.textToReceive).equals("To receive: $10.00"));
            onView(withText("Bob owes you $10.00")).check(matches(isDisplayed()));
            onView(withText("Carol: settled · $0.00")).check(matches(isDisplayed()));
            screenshot("balances-before");
            Settlement s = payment(b, a, a, 500);
            FullAppIntegrationTest.<Void>await(cb -> settlements.createSettlement(s, cb));
            waitFor(() -> text(screen, R.id.textToReceive).equals("To receive: $5.00"));
            screenshot("balances-after-settlement");
            FullAppIntegrationTest.<Void>await(cb -> settlements.deleteSettlement(s, a, cb));
            waitFor(() -> text(screen, R.id.textToReceive).equals("To receive: $10.00"));
            screen.recreate();
            waitFor(() -> text(screen, R.id.textToReceive).equals("To receive: $10.00"));
        }
    }

    @Test
    public void settlementFormValidationEditAndConfirmedDeletion() throws Exception {
        Expense e = expense("Dinner", a, 2000, SplitMethod.EQUAL, a, 0.0, b, 0.0);
        FullAppIntegrationTest.<Void>await(cb -> expenses.createExpense(e, cb));
        try (ActivityScenario<SettlementFormActivity> screen =
                launch(SettlementFormActivity.class, null)) {
            waitFor(() -> enabled(screen, R.id.radioPaidMe));
            onView(withId(R.id.radioPaidMe)).perform(click());
            waitFor(() -> enabled(screen, R.id.buttonSave));
            assertEquals("Maximum: $10.00", text(screen, R.id.textSettlementMaximum));
            for (String invalid : new String[] {"0", "-1", "1.001", "11"}) {
                onView(withId(R.id.inputSettlementAmount))
                        .perform(scrollTo(), replaceText(invalid), closeSoftKeyboard());
                onView(withId(R.id.buttonSave)).perform(scrollTo(), click());
                waitFor(() -> hasError(screen, R.id.inputSettlementAmount));
            }
            onView(withId(R.id.inputSettlementAmount))
                    .perform(scrollTo(), replaceText("5.00"), closeSoftKeyboard());
            onView(withId(R.id.inputSettlementNote))
                    .perform(scrollTo(), replaceText("Paid in cash"), closeSoftKeyboard());
            screenshot("settlement-form");
            onView(withId(R.id.buttonSave)).perform(scrollTo(), click());
            waitFor(() -> screen.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED);
        }
        Settlement saved =
                (Settlement) all().stream().filter(t -> t instanceof Settlement).findFirst().get();
        assertEquals(500, saved.getAmountCents());
        assertEquals(a, saved.getCreatorId());
        assertEquals(500, pair(a, b));
        try (ActivityScenario<SettlementFormActivity> screen =
                launch(SettlementFormActivity.class, saved.getId())) {
            waitFor(() -> enabled(screen, R.id.buttonSave));
            assertEquals("Maximum: $10.00", text(screen, R.id.textSettlementMaximum));
            assertFalse(enabled(screen, R.id.radioPaidMe));
            onView(withId(R.id.inputSettlementAmount))
                    .perform(scrollTo(), replaceText("6.00"), closeSoftKeyboard());
            onView(withId(R.id.buttonSave)).perform(scrollTo(), click());
            waitFor(() -> screen.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED);
        }
        assertEquals(400, pair(a, b));
        Transaction edited =
                await(cb -> history.getTransaction(group.getGroupId(), saved.getId(), cb));
        assertEquals(saved.getCreatedDate(), edited.getCreatedDate());
        assertEquals(saved.getSequenceNo(), edited.getSequenceNo());
        try (ActivityScenario<TransactionDetailActivity> screen =
                launch(TransactionDetailActivity.class, e.getId())) {
            waitFor(() -> text(screen, R.id.textTransactionStatus).contains("Locked"));
            assertFalse(visible(screen, R.id.buttonEdit));
            screenshot("locked-expense");
        }
        try (ActivityScenario<TransactionDetailActivity> screen =
                launch(TransactionDetailActivity.class, saved.getId())) {
            waitFor(() -> visible(screen, R.id.buttonDelete));
            onView(withId(R.id.buttonDelete)).perform(scrollTo(), click());
            onView(withText("Cancel")).perform(click());
            assertEquals(2, all().size());
            onView(withId(R.id.buttonDelete)).perform(scrollTo(), click());
            onView(withId(android.R.id.button1)).perform(click());
            waitFor(() -> screen.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED);
        }
        assertEquals(1000, pair(a, b));
        assertFalse(FullAppIntegrationTest.<Boolean>await(cb -> settlements.isLocked(e, cb)));
    }

    @Test
    public void expenseFormAllSplitModesAndGroupHistoryNavigation() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> screen =
                launch(ExpenseFormActivity.class, null)) {
            waitFor(() -> enabled(screen, R.id.buttonSave));
            onView(withId(R.id.editExpenseTitle))
                    .perform(scrollTo(), replaceText("UI dinner"), closeSoftKeyboard());
            onView(withId(R.id.editExpenseAmount))
                    .perform(scrollTo(), replaceText("30.00"), closeSoftKeyboard());
            screen.onActivity(
                    activity ->
                            ((CheckBox)
                                            ((LinearLayout)
                                                            activity.findViewById(
                                                                    R.id.layoutParticipants))
                                                    .getChildAt(3))
                                    .setChecked(false));
            onView(withId(R.id.radioSplitExact)).perform(scrollTo(), click());
            fillSplit(screen, new String[] {"5", "10", "15"});
            waitFor(() -> text(screen, R.id.textSplitStatus).equals("Shares add up to $30.00"));
            screenshot("exact-split");
            onView(withId(R.id.radioSplitPercent)).perform(scrollTo(), click());
            fillSplit(screen, new String[] {"20", "30", "50"});
            waitFor(() -> text(screen, R.id.textSplitStatus).equals("Shares add up to $30.00"));
            screenshot("percentage-split");
            onView(withId(R.id.radioSplitEqual)).perform(scrollTo(), click());
            onView(withId(R.id.main)).perform(swipeUp());
            onView(withId(R.id.main)).perform(swipeUp());
            screenshot("expense-before-save");
            onView(withId(R.id.buttonSave)).perform(click());
            waitFor(() -> screen.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED);
        }
        Expense saved = (Expense) all().get(0);
        assertEquals(1000, saved.getShare(b));
        try (ActivityScenario<GroupActivity> screen = launch(GroupActivity.class, null)) {
            waitFor(() -> text(screen, R.id.textHistoryStatus).contains("1 transaction"));
            screen.onActivity(
                    activity ->
                            ((ListView) activity.findViewById(R.id.listHistory)).setSelection(2));
            onView(withText(containsString("UI dinner"))).check(matches(isDisplayed()));
            screenshot("group-history");
        }
    }
    /*
     * Checks that each primary group action allows the user to return to the group screen.
     */

    @Test
    public void primaryActionsAlwaysAllowReturningToGroup() throws Exception {
        try (ActivityScenario<GroupActivity> screen = launch(GroupActivity.class, null)) {
            onView(withId(R.id.buttonAddExpense)).perform(click());
            waitFor(() -> isEnabledOnResumedActivity(R.id.buttonSave));
            returnToGroup(screen);

            onView(withId(R.id.buttonRecordSettlement)).perform(click());
            waitFor(() -> isVisibleOnResumedActivity(R.id.inputSettlementAmount));
            returnToGroup(screen);

            onView(withId(R.id.buttonBalances)).perform(click());
            onView(withId(R.id.textToReceive)).check(matches(isDisplayed()));
            returnToGroup(screen);

            onView(withId(R.id.buttonMonthlySummary)).perform(click());
            onView(withId(R.id.spinnerMonth)).check(matches(isDisplayed()));
            returnToGroup(screen);
        }
    }

    @Test
    public void requiredFieldsRejectBlankInputAcrossForms() throws Exception {
        Expense debt =
                expense("Required-field setup", a, 2000, SplitMethod.EQUAL, a, 0.0, b, 0.0);
        FullAppIntegrationTest.<Void>await(cb -> expenses.createExpense(debt, cb));

        try (ActivityScenario<ExpenseFormActivity> screen =
                launch(ExpenseFormActivity.class, null)) {
            waitFor(() -> enabled(screen, R.id.buttonSave));
            onView(withId(R.id.editExpenseAmount)).perform(replaceText(""), closeSoftKeyboard());
            onView(withId(R.id.buttonSave)).perform(scrollTo(), click());
            waitFor(() -> hasError(screen, R.id.editExpenseAmount));

            onView(withId(R.id.editExpenseAmount))
                    .perform(replaceText("10.00"), closeSoftKeyboard());
            onView(withId(R.id.buttonSave)).perform(scrollTo(), click());
            waitFor(() -> hasError(screen, R.id.editExpenseTitle));

            onView(withId(R.id.editExpenseTitle))
                    .perform(replaceText("Split validation"), closeSoftKeyboard());
            onView(withId(R.id.radioSplitExact)).perform(scrollTo(), click());
            waitFor(
                    () ->
                            text(screen, R.id.textSplitStatus)
                                    .contains("Enter an amount for every participant"));
            onView(withId(R.id.buttonSave)).perform(scrollTo(), click());
            waitFor(() -> visible(screen, R.id.textSplitError));

            onView(withId(R.id.radioSplitPercent)).perform(scrollTo(), click());
            waitFor(
                    () ->
                            text(screen, R.id.textSplitStatus)
                                    .contains("Enter a percentage for every participant"));
            onView(withId(R.id.buttonSave)).perform(scrollTo(), click());
            waitFor(() -> visible(screen, R.id.textSplitError));
        }

        try (ActivityScenario<SettlementFormActivity> screen =
                launch(SettlementFormActivity.class, null)) {
            waitFor(() -> enabled(screen, R.id.radioPaidMe));
            onView(withId(R.id.radioPaidMe)).perform(click());
            waitFor(() -> enabled(screen, R.id.buttonSave));
            onView(withId(R.id.inputSettlementAmount))
                    .perform(replaceText(""), closeSoftKeyboard());
            onView(withId(R.id.buttonSave)).perform(scrollTo(), click());
            waitFor(() -> hasError(screen, R.id.inputSettlementAmount));
        }

        try (ActivityScenario<GroupFormActivity> screen =
                ActivityScenario.launch(GroupFormActivity.class)) {
            onView(withId(R.id.buttonSave)).perform(click());
            waitFor(() -> hasError(screen, R.id.editGroupName));
        }

        try (ActivityScenario<GroupActivity> screen = launch(GroupActivity.class, null)) {
            waitFor(() -> visible(screen, R.id.buttonAddMember));
            onView(withId(R.id.buttonAddMember)).perform(click());
            waitFor(() -> hasError(screen, R.id.editMemberEmail));
        }

        try (ActivityScenario<ProfileActivity> screen =
                ActivityScenario.launch(ProfileActivity.class)) {
            waitFor(() -> !text(screen, R.id.editName).isEmpty());
            onView(withId(R.id.editName)).perform(replaceText(""), closeSoftKeyboard());
            onView(withId(R.id.buttonSaveName)).perform(click());
            waitFor(() -> hasError(screen, R.id.editName));
        }

        try (ActivityScenario<RegisterActivity> screen =
                ActivityScenario.launch(RegisterActivity.class)) {
            onView(withId(R.id.buttonCreateAccount)).perform(click());
            waitFor(
                    () ->
                            hasError(screen, R.id.editName)
                                    && hasError(screen, R.id.editEmail)
                                    && hasError(screen, R.id.editUscId)
                                    && hasError(screen, R.id.editPassword));
        }

        auth.logout();
        try {
            try (ActivityScenario<LoginActivity> screen =
                    ActivityScenario.launch(LoginActivity.class)) {
                onView(withId(R.id.buttonLogin)).perform(click());
                waitFor(() -> visible(screen, R.id.textLoginError));
                assertFalse(text(screen, R.id.textLoginError).trim().isEmpty());
            }
        } finally {
            FullAppIntegrationTest.<User>await(
                    cb -> auth.login(users.get(0).getUscEmail(), PASSWORD, cb));
        }
    }

    @Test
    public void canReturnFromGroupToProfileAndEditName() throws Exception {
        String originalName = users.get(0).getName();
        try (ActivityScenario<GroupListActivity> screen =
                ActivityScenario.launch(GroupListActivity.class)) {
            waitFor(() -> hasListItemOnResumedActivity(
                    R.id.listGroups, "Integration Trip  (owner)"));
            onData(is("Integration Trip  (owner)"))
                    .inAdapterView(withId(R.id.listGroups))
                    .perform(click());
            onView(withId(R.id.buttonAddExpense)).check(matches(isDisplayed()));

            onView(isRoot()).perform(pressBack());
            onView(withId(R.id.buttonProfile)).check(matches(isDisplayed()));
            onView(withId(R.id.buttonProfile)).perform(click());
            onView(withId(R.id.editName)).check(matches(isDisplayed()));
            waitFor(() -> originalName.equals(textOnResumedActivity(R.id.editName)));
            onView(withId(R.id.textEmail)).check(matches(withText(containsString("@usc.edu"))));
            onView(withId(R.id.textUscId)).check(matches(withText(containsString("USC ID:"))));

            onView(isRoot()).perform(pressBack());
            waitFor(
                    () ->
                            screen.getState() == androidx.lifecycle.Lifecycle.State.RESUMED);
            onView(withId(R.id.buttonProfile)).check(matches(isDisplayed()));
            onView(withId(R.id.buttonProfile)).perform(click());
            waitFor(() -> originalName.equals(textOnResumedActivity(R.id.editName)));

            onView(withId(R.id.editName)).perform(replaceText("   "), closeSoftKeyboard());
            onView(withId(R.id.buttonSaveName)).perform(click());
            waitFor(() -> hasErrorOnResumedActivity(R.id.editName));

            onView(withId(R.id.editName))
                    .perform(replaceText("Alice Usability"), closeSoftKeyboard());
            onView(withId(R.id.buttonSaveName)).perform(click());
            waitFor(() -> currentUserHasName("Alice Usability"));
            assertEquals("Alice Usability", textOnResumedActivity(R.id.editName));

            onView(withId(R.id.editName)).perform(replaceText(originalName), closeSoftKeyboard());
            onView(withId(R.id.buttonSaveName)).perform(click());
            waitFor(() -> currentUserHasName(originalName));

            onView(isRoot()).perform(pressBack());
            onView(withId(R.id.buttonProfile)).check(matches(isDisplayed()));

            String newGroupName = "Usability " + UUID.randomUUID().toString().substring(0, 8);
            onView(withId(R.id.buttonCreateGroup)).perform(click());
            onView(withId(R.id.editGroupName)).check(matches(isDisplayed()));
            onView(isRoot()).perform(pressBack());
            waitFor(
                    () ->
                            screen.getState() == androidx.lifecycle.Lifecycle.State.RESUMED);
            onView(withId(R.id.buttonCreateGroup)).check(matches(isDisplayed()));

            onView(withId(R.id.buttonCreateGroup)).perform(click());
            onView(withId(R.id.editGroupName)).check(matches(isDisplayed()));
            onView(withId(R.id.editGroupName))
                    .perform(replaceText(newGroupName), closeSoftKeyboard());
            onView(withId(R.id.editGroupDescription))
                    .perform(replaceText("Navigation test"), closeSoftKeyboard());
            onView(withId(R.id.buttonSave)).perform(click());
            onView(withId(R.id.buttonAddExpense)).check(matches(isDisplayed()));

            Group created =
                    FullAppIntegrationTest.<List<Group>>await(
                                    cb -> groups.getGroupsFor(a, cb))
                            .stream()
                            .filter(candidate -> newGroupName.equals(candidate.getName()))
                            .findFirst()
                            .orElseThrow(
                                    () -> new AssertionError("Created group not found"));
            navigationGroupId = created.getGroupId();

            onView(isRoot()).perform(pressBack());
            waitFor(
                    () ->
                            screen.getState() == androidx.lifecycle.Lifecycle.State.RESUMED
                                    && hasListItemOnResumedActivity(
                                            R.id.listGroups, newGroupName + "  (owner)"));
        }
    }

    private void returnToGroup(ActivityScenario<GroupActivity> screen) {
        onView(isRoot()).perform(pressBack());
        waitFor(() -> screen.getState() == androidx.lifecycle.Lifecycle.State.RESUMED);
        onView(withId(R.id.buttonAddExpense)).check(matches(isDisplayed()));
    }

    private void fillSplit(ActivityScenario<ExpenseFormActivity> screen, String[] values) {
        screen.onActivity(
                activity -> {
                    LinearLayout rows = activity.findViewById(R.id.splitRows);
                    for (int i = 0; i < values.length; i++)
                        ((EditText) ((LinearLayout) rows.getChildAt(i)).getChildAt(1))
                                .setText(values[i]);
                });
    }

    private Expense expense(
            String title, String payer, long cents, SplitMethod method, Object... inputs) {
        Expense e = new Expense();
        e.setGroupId(group.getGroupId());
        e.setCreatorId(payer);
        e.setPayerId(payer);
        e.setTitle(title);
        e.setAmountCents(cents);
        e.setSplitMethod(method);
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < inputs.length; i += 2)
            map.put((String) inputs[i], (Double) inputs[i + 1]);
        e.setShares(expenses.previewShares(cents, method, map));
        e.setSplitInputs(method == SplitMethod.EQUAL ? new LinkedHashMap<>() : map);
        return e;
    }

    private int recordSettlementInRemoteProcess(
            String email, String groupId, String recipientId, long amountCents) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Messenger> remote = new AtomicReference<>();
        AtomicReference<Bundle> response = new AtomicReference<>();
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        remote.set(new Messenger(binder));
                        connected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        remote.set(null);
                    }
                };
        boolean bound =
                context.bindService(
                        new Intent(context, RemoteUserService.class),
                        connection,
                        Context.BIND_AUTO_CREATE);
        assertTrue("Could not bind to Bob's remote process", bound);
        try {
            assertTrue(
                    "Bob's remote process did not connect",
                    connected.await(15, TimeUnit.SECONDS));
            Messenger replyTo =
                    new Messenger(
                            new Handler(
                                    Looper.getMainLooper(),
                                    message -> {
                                        if (message.what == RemoteUserService.RESULT) {
                                            response.set(message.getData());
                                            completed.countDown();
                                            return true;
                                        }
                                        return false;
                                    }));
            Message request = Message.obtain(null, RemoteUserService.RECORD_SETTLEMENT);
            Bundle data = new Bundle();
            data.putString(RemoteUserService.EMAIL, email);
            data.putString(RemoteUserService.PASSWORD, PASSWORD);
            data.putString(RemoteUserService.GROUP_ID, groupId);
            data.putString(RemoteUserService.RECIPIENT_ID, recipientId);
            data.putLong(RemoteUserService.AMOUNT_CENTS, amountCents);
            request.setData(data);
            request.replyTo = replyTo;
            remote.get().send(request);

            assertTrue(
                    "Bob's remote process did not finish its settlement",
                    completed.await(30, TimeUnit.SECONDS));
            Bundle result = response.get();
            String error = result.getString(RemoteUserService.ERROR);
            assertNull("Bob's remote process failed: " + error, error);
            return result.getInt(RemoteUserService.PROCESS_ID);
        } finally {
            context.unbindService(connection);
        }
    }

    private Settlement payment(String payer, String recipient, String creator, long cents) {
        Settlement s = new Settlement();
        s.setGroupId(group.getGroupId());
        s.setPayerId(payer);
        s.setRecipientId(recipient);
        s.setCreatorId(creator);
        s.setAmountCents(cents);
        return s;
    }

    private List<Transaction> all() throws Exception {
        return await(cb -> transactions.findByGroup(group.getGroupId(), cb));
    }

    private List<Transaction> filtered(HistoryFilter f) throws Exception {
        return await(cb -> history.getHistory(group.getGroupId(), f, cb));
    }

    private long pair(String user, String other) throws Exception {
        return FullAppIntegrationTest.<Long>await(
                cb -> balances.getPairBalance(group.getGroupId(), user, other, cb));
    }

    private <A extends android.app.Activity> ActivityScenario<A> launch(Class<A> type, String id) {
        Intent intent =
                new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), type)
                        .putExtra(Extras.GROUP_ID, group.getGroupId());
        if (id != null) intent.putExtra(Extras.TRANSACTION_ID, id);
        return ActivityScenario.launch(intent);
    }

    private static <A extends android.app.Activity> String text(
            ActivityScenario<A> scenario, int id) {
        AtomicReference<String> result = new AtomicReference<>("");
        scenario.onActivity(a -> result.set(((TextView) a.findViewById(id)).getText().toString()));
        return result.get();
    }

    private static <A extends android.app.Activity> boolean enabled(
            ActivityScenario<A> scenario, int id) {
        AtomicReference<Boolean> r = new AtomicReference<>(false);
        scenario.onActivity(a -> r.set(a.findViewById(id).isEnabled()));
        return r.get();
    }

    private static <A extends android.app.Activity> boolean visible(
            ActivityScenario<A> scenario, int id) {
        AtomicReference<Boolean> r = new AtomicReference<>(false);
        scenario.onActivity(a -> r.set(a.findViewById(id).getVisibility() == View.VISIBLE));
        return r.get();
    }

    /* Helpers for checking views on the currently resumed activity. */

    private static boolean isVisibleOnResumedActivity(int id) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            for (android.app.Activity activity :
                                    ActivityLifecycleMonitorRegistry.getInstance()
                                            .getActivitiesInStage(Stage.RESUMED)) {
                                View view = activity.findViewById(id);
                                if (view != null) {
                                    result.set(view.getVisibility() == View.VISIBLE);
                                    return;
                                }
                            }
                        });
        return result.get();
    }

    private static String textOnResumedActivity(int id) {
        AtomicReference<String> result = new AtomicReference<>("");
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            for (android.app.Activity activity :
                                    ActivityLifecycleMonitorRegistry.getInstance()
                                            .getActivitiesInStage(Stage.RESUMED)) {
                                View view = activity.findViewById(id);
                                if (view instanceof TextView) {
                                    result.set(((TextView) view).getText().toString());
                                    return;
                                }
                            }
                        });
        return result.get();
    }

    private boolean currentUserHasName(String expected) {
        try {
            return expected.equals(await(auth::getCurrentUser).getName());
        } catch (Exception e) {
            throw new AssertionError("Could not verify the saved profile name", e);
        }
    }

    private static boolean hasListItemOnResumedActivity(int id, String expected) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            for (android.app.Activity activity :
                                    ActivityLifecycleMonitorRegistry.getInstance()
                                            .getActivitiesInStage(Stage.RESUMED)) {
                                View view = activity.findViewById(id);
                                if (view instanceof ListView) {
                                    ListView list = (ListView) view;
                                    for (int i = 0; i < list.getCount(); i++) {
                                        if (expected.equals(list.getItemAtPosition(i).toString())) {
                                            result.set(true);
                                            return;
                                        }
                                    }
                                    return;
                                }
                            }
                        });
        return result.get();
    }

    private static boolean isEnabledOnResumedActivity(int id) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            for (android.app.Activity activity :
                                    ActivityLifecycleMonitorRegistry.getInstance()
                                            .getActivitiesInStage(Stage.RESUMED)) {
                                View view = activity.findViewById(id);
                                if (view != null) {
                                    result.set(view.isEnabled());
                                    return;
                                }
                            }
                        });
        return result.get();
    }

    private static boolean hasErrorOnResumedActivity(int id) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            for (android.app.Activity activity :
                                    ActivityLifecycleMonitorRegistry.getInstance()
                                            .getActivitiesInStage(Stage.RESUMED)) {
                                View view = activity.findViewById(id);
                                if (view instanceof EditText) {
                                    result.set(((EditText) view).getError() != null);
                                    return;
                                }
                            }
                        });
        return result.get();
    }

    private static <A extends android.app.Activity> boolean hasError(
            ActivityScenario<A> scenario, int id) {
        AtomicReference<Boolean> r = new AtomicReference<>(false);
        scenario.onActivity(a -> r.set(((EditText) a.findViewById(id)).getError() != null));
        return r.get();
    }

    private static void waitFor(BooleanSupplier condition) {
        long end = SystemClock.uptimeMillis() + 20000;
        while (SystemClock.uptimeMillis() < end) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(100);
        }
        assertTrue("Timed out waiting for Android UI", condition.getAsBoolean());
    }

    private static void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(200);
        Bitmap bitmap =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File dir =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getExternalFilesDir("test-evidence");
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }

    private static <T> T await(Consumer<Callback<T>> action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        action.accept(
                new Callback<T>() {
                    public void onSuccess(T v) {
                        value.set(v);
                        done.countDown();
                    }

                    public void onFailure(Exception e) {
                        error.set(e);
                        done.countDown();
                    }
                });
        assertTrue("Firebase callback timed out", done.await(25, TimeUnit.SECONDS));
        if (error.get() != null) throw error.get();
        return value.get();
    }

    private static <T> void assertError(String field, Consumer<Callback<T>> action)
            throws Exception {
        try {
            await(action);
            fail("Expected validation error: " + field);
        } catch (ValidationException e) {
            assertEquals(field, e.getField());
        }
    }
}
