package com.team19.splitsmart;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.team19.splitsmart.data.model.*;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.service.*;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.ui.group.GroupActivity;
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

    @BeforeClass
    public static void registerUsers() throws Exception {
        assertEquals(
                "10.0.2.2:8080", FirebaseFirestore.getInstance().getFirestoreSettings().getHost());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long idBase = 1000000000L + Math.floorMod(System.currentTimeMillis(), 8000000000L);
        for (int i = 0; i < 4; i++) {
            final int index = i;
            User user =
                    await(
                            cb ->
                                    auth.register(
                                            new String[] {"Alice", "Bob", "Carol", "David"}[index],
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
