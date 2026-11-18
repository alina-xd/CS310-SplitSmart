package com.team19.splitsmart;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

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
import com.team19.splitsmart.ui.transaction.ExpenseFormActivity;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.DateUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Part B black-box tests (author: Chenyang Wang), written with Espresso from the requirements
 * (UC-2, FR-2.1 to FR-2.3 and the creator-only, not-settled edit rule of FR-4.2) without relying
 * on how the code works inside. Each test drives the real expense form, the way a user would.
 *
 * Runs against the local Firebase emulators (see tests/README.md), never the shared database:
 *   ./gradlew connectedDebugAndroidTest --no-watch-fs \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.team19.splitsmart.PartBBlackBoxTest
 */
@RunWith(AndroidJUnit4.class)
public class PartBBlackBoxTest {
    private static final String PASSWORD = "test-password-310";
    /** Alice, Bob, Carol and David are in the group; Eve has an account but is not a member. */
    private static final String[] NAMES = {"Alice", "Bob", "Carol", "David", "Eve"};
    private static final int ALICE = 0, BOB = 1, CAROL = 2, DAVID = 3;
    private static final List<User> users = new ArrayList<>();
    private static final AuthService auth = new AuthService();

    private final GroupService groups = new GroupService();
    private final ExpenseService expenses = new ExpenseService();
    private final SettlementService settlements = new SettlementService();
    private final TransactionRepository transactions = new TransactionRepository();
    private Group group;

    @BeforeClass
    public static void registerUsers() throws Exception {
        assertEquals("Tests must use the local emulator, not the shared database",
                "10.0.2.2:8080", FirebaseFirestore.getInstance().getFirestoreSettings().getHost());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long idBase = 1000000000L + Math.floorMod(System.currentTimeMillis() + 7373, 8000000000L);
        for (int i = 0; i < NAMES.length; i++) {
            final int index = i;
            users.add(await(cb -> auth.register(NAMES[index], "partb-" + suffix + "-" + index + "@usc.edu",
                    String.valueOf(idBase + index), PASSWORD, cb)));
        }
    }

    /** Alice (logged in) owns a fresh group with Bob, Carol and David for every test. */
    @Before
    public void createGroup() throws Exception {
        logIn(ALICE);
        group = await(cb -> groups.createGroup("Part B test", "Black-box tests", uid(ALICE), cb));
        for (int i = BOB; i <= DAVID; i++) {
            final int index = i;
            PartBBlackBoxTest.<Void>await(
                    cb -> groups.addMember(group.getGroupId(), users.get(index).getUscEmail(), uid(ALICE), cb));
        }
    }

    @After
    public void deleteGroup() throws Exception {
        if (group != null) {
            PartBBlackBoxTest.<Void>await(cb -> groups.deleteGroup(group.getGroupId(), uid(ALICE), cb));
        }
    }

    /**
     * BB-B1 A complete expense saves every field (UC-2 steps 2 to 8, FR-2.1, FR-2.2). Before
     * typing, the category list must hold exactly the 5 categories with Other selected, and the
     * payer must default to the logged-in user. "Dinner", $30.00, Food, note "Birthday", equal
     * split among all 4 members must save with the creator, $7.50 each and today's date as
     * YYYY-MM-DD.
     */
    @Test
    public void bbB1_completeExpense_savesEveryFieldWithTodaysDate() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> screen = openNewExpenseForm()) {
            assertEquals(Arrays.asList("Food", "Housing", "Transportation", "Entertainment", "Other"),
                    spinnerItems(screen, R.id.spinnerExpenseCategory));
            assertEquals("New expenses default to Other", "Other", selected(screen, R.id.spinnerExpenseCategory));
            assertEquals("The payer defaults to the logged-in user", "Alice", selected(screen, R.id.spinnerExpensePayer));

            typeTitleAndAmount(screen, "Dinner", "30.00");
            choose(screen, R.id.spinnerExpenseCategory, "Food");
            reveal(screen, R.id.editExpenseNote);
            onView(withId(R.id.editExpenseNote)).perform(replaceText("Birthday"), closeSoftKeyboard());
            screenshot("bb-b1-complete-expense");
            save(screen);
            waitFor(() -> screen.getState() == Lifecycle.State.DESTROYED);
        }
        Expense saved = onlyExpense();
        assertEquals("Dinner", saved.getTitle());
        assertEquals(3000, saved.getAmountCents());
        assertEquals(Category.FOOD, saved.getCategory());
        assertEquals(uid(ALICE), saved.getPayerId());
        assertEquals(uid(ALICE), saved.getCreatorId());
        assertEquals("Birthday", saved.getNote());
        assertEquals(SplitMethod.EQUAL, saved.getSplitMethod());
        assertEquals(4, saved.getShares().size());
        for (long share : saved.getShares().values()) assertEquals(750L, share);
        assertEquals(DateUtil.today(), saved.getCreatedDate());
        assertTrue(saved.getCreatedDate().matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    /**
     * BB-B2 Invalid inputs are refused next to the field, and the smallest valid amount saves
     * (UC-2 alternate path 5.2, FR-2.3). Equivalence classes for the title (blank) and the amount
     * (zero, negative, three decimals, empty), then the lower boundary $0.01, which must save with
     * the default category Other. Also an invalid split: its error must be shown once, not twice
     * (regression test for the duplicate message found by Part C's BB-C2).
     */
    @Test
    public void bbB2_invalidTitleAmountAndSplit_areRefused_andOneCentSaves() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> screen = openNewExpenseForm()) {
            typeTitleAndAmount(screen, "   ", "10.00");
            save(screen);
            waitFor(() -> "Enter a title".equals(errorOf(screen, R.id.editExpenseTitle)));

            String[][] badAmounts = {
                    {"0", "The amount must be more than $0.00"},
                    {"-5.00", "The amount must be more than $0.00"},
                    {"12.345", "Enter a valid amount with at most 2 decimals"},
                    {"", "Enter a valid amount with at most 2 decimals"},
            };
            for (String[] bad : badAmounts) {
                typeTitleAndAmount(screen, "Coffee", bad[0]);
                save(screen);
                waitFor(() -> bad[1].equals(errorOf(screen, R.id.editExpenseAmount)));
            }
            screenshot("bb-b2-invalid-amount");
            assertEquals("Nothing is saved while an input is invalid", 0, all().size());

            // Invalid exact split: the section's status line has the details, the form only points to it.
            typeTitleAndAmount(screen, "Coffee", "12.00");
            clickRadio(screen, R.id.radioSplitExact);
            waitFor(() -> childCount(screen, R.id.splitRows) == 4);
            fillSplit(screen, "3", "3", "3", "1");
            waitFor(() -> text(screen, R.id.textSplitStatus).contains("$10.00 but the expense is $12.00"));
            save(screen);
            waitFor(() -> visible(screen, R.id.textSplitError));
            screenshot("bb-b2-split-error-once");
            assertNotEquals("The split error is not repeated",
                    text(screen, R.id.textSplitStatus), text(screen, R.id.textSplitError));
            assertEquals(0, all().size());

            clickRadio(screen, R.id.radioSplitEqual);
            typeTitleAndAmount(screen, "Coffee", "0.01");
            save(screen);
            waitFor(() -> screen.getState() == Lifecycle.State.DESTROYED);
        }
        Expense saved = onlyExpense();
        assertEquals(1, saved.getAmountCents());
        assertEquals(Category.OTHER, saved.getCategory());
        assertEquals(1L, sumOf(saved.getShares()));
    }

    /**
     * BB-B3 Payer and participants come only from the group, and the payer can't be unchecked
     * (UC-2 step 4, FR-2.3). Eve has an account but isn't a member, so she must not be offered.
     * Choosing a new payer must lock that member's box and release the old payer's box. With only
     * the payer left, saving must be refused (fewer than 2 participants).
     */
    @Test
    public void bbB3_payerAndParticipants_comeFromGroup_andPayerCantBeUnchecked() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> screen = openNewExpenseForm()) {
            List<String> members = Arrays.asList("Alice", "Bob", "Carol", "David");
            assertEquals(members, spinnerItems(screen, R.id.spinnerExpensePayer));
            assertEquals(members, checkboxNames(screen));
            assertFalse("Eve isn't a member", checkboxNames(screen).contains("Eve"));
            participant("Alice").check(matches(allOf(isChecked(), not(isEnabled()))));
            participant("Bob").check(matches(allOf(isChecked(), isEnabled())));

            choose(screen, R.id.spinnerExpensePayer, "Bob");
            participant("Bob").check(matches(allOf(isChecked(), not(isEnabled()))));
            participant("Alice").check(matches(isEnabled()));
            choose(screen, R.id.spinnerExpensePayer, "Alice");

            typeTitleAndAmount(screen, "Solo lunch", "8.00");
            for (String name : new String[] {"Bob", "Carol", "David"}) {
                toggle(screen, name);
            }
            participant("Alice").check(matches(isChecked()));
            save(screen);
            waitFor(() -> visible(screen, R.id.textSplitError));
            assertEquals("Select at least one participant besides the payer", text(screen, R.id.textSplitStatus));
            screenshot("bb-b3-only-payer");
            assertEquals("The form is still open", Lifecycle.State.RESUMED, screen.getState());
            assertEquals(0, all().size());
        }
    }

    /**
     * BB-B4 A member can record an expense that someone else paid and that they aren't part of
     * (FR-2.3, README Part B checklist). Alice records a $20.00 taxi paid by Bob for Bob and Carol
     * only. Alice must be the creator but not a participant.
     */
    @Test
    public void bbB4_memberRecordsExpensePaidBySomeoneElse_withoutThemselves() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> screen = openNewExpenseForm()) {
            typeTitleAndAmount(screen, "Taxi", "20.00");
            choose(screen, R.id.spinnerExpensePayer, "Bob");
            toggle(screen, "Alice");
            toggle(screen, "David");
            participant("Alice").check(matches(isNotChecked()));
            waitFor(() -> childCount(screen, R.id.splitRows) == 2);
            screenshot("bb-b4-paid-by-bob");
            save(screen);
            waitFor(() -> screen.getState() == Lifecycle.State.DESTROYED);
        }
        Expense saved = onlyExpense();
        assertEquals(uid(ALICE), saved.getCreatorId());
        assertEquals(uid(BOB), saved.getPayerId());
        assertEquals(new HashSet<>(Arrays.asList(uid(BOB), uid(CAROL))), saved.getShares().keySet());
        assertEquals(1000L, saved.getShare(uid(BOB)));
        assertEquals(1000L, saved.getShare(uid(CAROL)));
    }

    /**
     * BB-B5 The creator edits an unlocked expense (UC-2 alternate path 8.1, FR-2.1). Dinner was
     * created on 2026-10-12 for $30.00 among Alice, Bob and Carol. The form must open with the
     * saved values; changing the amount to $40.00, the payer to Bob and Carol to David must save
     * in place, keeping the original date and order.
     */
    @Test
    public void bbB5_creatorEditsUnlockedExpense_dateAndOrderStayTheSame() throws Exception {
        Expense original = createDinnerAsAlice();
        try (ActivityScenario<ExpenseFormActivity> screen = openEditForm(original)) {
            assertEquals("Edit expense", text(screen, R.id.textExpenseHeading));
            assertEquals("30.00", text(screen, R.id.editExpenseAmount));
            assertEquals("Food", selected(screen, R.id.spinnerExpenseCategory));
            assertEquals("Alice", selected(screen, R.id.spinnerExpensePayer));
            participant("David").check(matches(isNotChecked()));

            typeTitleAndAmount(screen, "Dinner", "40.00");
            choose(screen, R.id.spinnerExpensePayer, "Bob");
            toggle(screen, "Carol");
            toggle(screen, "David");
            waitFor(() -> text(screen, R.id.textSplitStatus).equals("Shares add up to $40.00"));
            screenshot("bb-b5-edit-unlocked");
            save(screen);
            waitFor(() -> screen.getState() == Lifecycle.State.DESTROYED);
        }
        Expense saved = onlyExpense();
        assertEquals("Edited in place, not copied", original.getId(), saved.getId());
        assertEquals("2026-10-12", saved.getCreatedDate());
        assertEquals(original.getSequenceNo(), saved.getSequenceNo());
        assertEquals(uid(ALICE), saved.getCreatorId());
        assertEquals(4000, saved.getAmountCents());
        assertEquals(uid(BOB), saved.getPayerId());
        assertEquals(new HashSet<>(Arrays.asList(uid(ALICE), uid(BOB), uid(DAVID))), saved.getShares().keySet());
        assertEquals(4000L, sumOf(saved.getShares()));
    }

    /**
     * BB-B6 Only the creator can edit, and only while the expense isn't settled (UC-2 alternate
     * paths 8.1 and 8.3, FR-4.2). Bob opens Alice's Dinner and tries to rename it: refused. Then
     * Bob pays Alice back $3.00, which settles Dinner; Alice herself is now refused too. In both
     * cases the saved expense must not change. This is the server-side check that still holds if a
     * screen offered Edit before the settlement was recorded.
     */
    @Test
    public void bbB6_nonCreatorAndSettledExpense_cannotBeEdited() throws Exception {
        Expense original = createDinnerAsAlice();

        logIn(BOB);
        try (ActivityScenario<ExpenseFormActivity> screen = openEditForm(original)) {
            typeTitleAndAmount(screen, "Renamed by Bob", "30.00");
            save(screen);
            waitFor(() -> visible(screen, R.id.textFormError));
            assertEquals("Only the creator can edit this expense", text(screen, R.id.textFormError));
            screenshot("bb-b6-not-creator");
            assertEquals(Lifecycle.State.RESUMED, screen.getState());
        }
        assertEquals("Dinner", onlyExpense().getTitle());

        Settlement repaid = new Settlement();
        repaid.setGroupId(group.getGroupId());
        repaid.setCreatorId(uid(BOB));
        repaid.setPayerId(uid(BOB));
        repaid.setRecipientId(uid(ALICE));
        repaid.setAmountCents(300);
        repaid.setNote("Venmo");
        PartBBlackBoxTest.<Void>await(cb -> settlements.createSettlement(repaid, cb));

        logIn(ALICE);
        try (ActivityScenario<ExpenseFormActivity> screen = openEditForm(original)) {
            typeTitleAndAmount(screen, "Dinner", "36.00");
            save(screen);
            waitFor(() -> visible(screen, R.id.textFormError));
            assertEquals("This expense is locked by a later settlement", text(screen, R.id.textFormError));
            screenshot("bb-b6-locked");
        }
        Expense after = (Expense) findById(original.getId());
        assertEquals(3000, after.getAmountCents());
        assertEquals("Dinner", after.getTitle());
    }

    // ---- Steps a user takes ----

    private ActivityScenario<ExpenseFormActivity> openNewExpenseForm() {
        ActivityScenario<ExpenseFormActivity> screen = ActivityScenario.launch(groupIntent());
        waitFor(() -> childCount(screen, R.id.layoutParticipants) == 4 && saveEnabled(screen));
        return screen;
    }

    private ActivityScenario<ExpenseFormActivity> openEditForm(Expense expense) {
        ActivityScenario<ExpenseFormActivity> screen =
                ActivityScenario.launch(groupIntent().putExtra(Extras.TRANSACTION_ID, expense.getId()));
        waitFor(() -> expense.getTitle().equals(text(screen, R.id.editExpenseTitle)) && saveEnabled(screen));
        return screen;
    }

    private void typeTitleAndAmount(ActivityScenario<ExpenseFormActivity> screen, String title, String amount) {
        reveal(screen, R.id.editExpenseTitle);
        onView(withId(R.id.editExpenseTitle)).perform(replaceText(title), closeSoftKeyboard());
        onView(withId(R.id.editExpenseAmount)).perform(replaceText(amount), closeSoftKeyboard());
    }

    /** Opens a spinner and taps one of its items, like a user picking from the list. */
    private void choose(ActivityScenario<ExpenseFormActivity> screen, int spinnerId, String item) {
        reveal(screen, spinnerId);
        onView(withId(spinnerId)).check(matches(isDisplayed())).perform(click());
        onData(allOf(is(instanceOf(String.class)), is(item))).perform(click());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /** Taps a member's participant checkbox. */
    private void toggle(ActivityScenario<ExpenseFormActivity> screen, String name) {
        reveal(screen, R.id.layoutParticipants);
        participant(name).check(matches(isDisplayed())).perform(click());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static androidx.test.espresso.ViewInteraction participant(String name) {
        return onView(allOf(withText(name), isDescendantOfA(withId(R.id.layoutParticipants))));
    }

    private void clickRadio(ActivityScenario<ExpenseFormActivity> screen, int id) {
        reveal(screen, id);
        onView(withId(id)).check(matches(isDisplayed())).perform(click());
    }

    /**
     * Scrolls to the bottom and taps Save. The form is a NestedScrollView, which Espresso's
     * scrollTo() can't scroll, so the test scrolls it the way a user's swipe would.
     */
    private void save(ActivityScenario<ExpenseFormActivity> screen) {
        screen.onActivity(activity ->
                ((NestedScrollView) activity.findViewById(R.id.main)).fullScroll(View.FOCUS_DOWN));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        onView(withId(R.id.buttonSave)).check(matches(isDisplayed())).perform(click());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /** Scrolls the form so that view id is on screen, as a user's swipe would. */
    private void reveal(ActivityScenario<ExpenseFormActivity> screen, int id) {
        screen.onActivity(activity -> {
            NestedScrollView scroll = activity.findViewById(R.id.main);
            View view = activity.findViewById(id);
            int top = 0;
            for (View v = view; v != scroll; v = (View) v.getParent()) top += v.getTop();
            scroll.scrollTo(0, Math.max(0, top - 200));
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /** Types one value per split row (payer first), like a user filling each box. */
    private void fillSplit(ActivityScenario<ExpenseFormActivity> screen, String... values) {
        screen.onActivity(activity -> {
            LinearLayout rows = activity.findViewById(R.id.splitRows);
            for (int i = 0; i < values.length; i++) {
                ((EditText) ((LinearLayout) rows.getChildAt(i)).getChildAt(1)).setText(values[i]);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    // ---- Reading the screen ----

    private static List<String> spinnerItems(ActivityScenario<ExpenseFormActivity> screen, int id) {
        AtomicReference<List<String>> result = new AtomicReference<>();
        screen.onActivity(a -> {
            Spinner spinner = a.findViewById(id);
            List<String> items = new ArrayList<>();
            for (int i = 0; i < spinner.getCount(); i++) items.add(String.valueOf(spinner.getItemAtPosition(i)));
            result.set(items);
        });
        return result.get();
    }

    private static String selected(ActivityScenario<ExpenseFormActivity> screen, int id) {
        AtomicReference<String> result = new AtomicReference<>("");
        screen.onActivity(a -> result.set(String.valueOf(((Spinner) a.findViewById(id)).getSelectedItem())));
        return result.get();
    }

    private static List<String> checkboxNames(ActivityScenario<ExpenseFormActivity> screen) {
        AtomicReference<List<String>> result = new AtomicReference<>();
        screen.onActivity(a -> {
            LinearLayout boxes = a.findViewById(R.id.layoutParticipants);
            List<String> names = new ArrayList<>();
            for (int i = 0; i < boxes.getChildCount(); i++) {
                names.add(((TextView) boxes.getChildAt(i)).getText().toString());
            }
            result.set(names);
        });
        return result.get();
    }

    private static String errorOf(ActivityScenario<ExpenseFormActivity> screen, int id) {
        AtomicReference<String> result = new AtomicReference<>();
        screen.onActivity(a -> {
            CharSequence error = ((EditText) a.findViewById(id)).getError();
            result.set(error == null ? null : error.toString());
        });
        return result.get();
    }

    private static String text(ActivityScenario<ExpenseFormActivity> screen, int id) {
        AtomicReference<String> result = new AtomicReference<>("");
        screen.onActivity(a -> result.set(((TextView) a.findViewById(id)).getText().toString()));
        return result.get();
    }

    private static int childCount(ActivityScenario<ExpenseFormActivity> screen, int id) {
        AtomicReference<Integer> result = new AtomicReference<>(0);
        screen.onActivity(a -> result.set(((LinearLayout) a.findViewById(id)).getChildCount()));
        return result.get();
    }

    private static boolean visible(ActivityScenario<ExpenseFormActivity> screen, int id) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        screen.onActivity(a -> result.set(a.findViewById(id).getVisibility() == View.VISIBLE));
        return result.get();
    }

    private static boolean saveEnabled(ActivityScenario<ExpenseFormActivity> screen) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        screen.onActivity(a -> result.set(a.findViewById(R.id.buttonSave).isEnabled()));
        return result.get();
    }

    // ---- Test data ----

    private static String uid(int i) {
        return users.get(i).getUid();
    }

    private static void logIn(int i) throws Exception {
        PartBBlackBoxTest.<User>await(cb -> auth.login(users.get(i).getUscEmail(), PASSWORD, cb));
    }

    private Intent groupIntent() {
        return new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), ExpenseFormActivity.class)
                .putExtra(Extras.GROUP_ID, group.getGroupId());
    }

    /** Dinner: $30.00 Food, paid by Alice, equal among Alice, Bob and Carol, dated 2026-10-12. */
    private Expense createDinnerAsAlice() throws Exception {
        Expense e = new Expense();
        e.setGroupId(group.getGroupId());
        e.setCreatorId(uid(ALICE));
        e.setPayerId(uid(ALICE));
        e.setTitle("Dinner");
        e.setAmountCents(3000);
        e.setCategory(Category.FOOD);
        e.setSplitMethod(SplitMethod.EQUAL);
        Map<String, Double> people = new LinkedHashMap<>();
        people.put(uid(ALICE), 0.0);
        people.put(uid(BOB), 0.0);
        people.put(uid(CAROL), 0.0);
        e.setShares(expenses.previewShares(3000, SplitMethod.EQUAL, people));
        PartBBlackBoxTest.<Void>await(cb -> expenses.createExpense(e, cb));
        Expense saved = onlyExpense();
        saved.setCreatedDate("2026-10-12");
        PartBBlackBoxTest.<Void>await(cb -> transactions.update(saved, cb));
        return saved;
    }

    private List<Transaction> all() throws Exception {
        return await(cb -> transactions.findByGroup(group.getGroupId(), cb));
    }

    private Transaction findById(String id) throws Exception {
        return await(cb -> transactions.findById(group.getGroupId(), id, cb));
    }

    private Expense onlyExpense() throws Exception {
        List<Transaction> list = new ArrayList<>();
        for (Transaction t : all()) if (t instanceof Expense) list.add(t);
        assertEquals(1, list.size());
        return (Expense) list.get(0);
    }

    private static long sumOf(Map<String, Long> shares) {
        long total = 0;
        for (long v : shares.values()) total += v;
        return total;
    }

    // ---- Waiting and evidence ----

    private static void waitFor(BooleanSupplier condition) {
        long end = SystemClock.uptimeMillis() + 20000;
        while (SystemClock.uptimeMillis() < end) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(100);
        }
        assertTrue("Timed out waiting for the screen", condition.getAsBoolean());
    }

    /** Saves a screenshot to the emulator's test-evidence folder (see tests/README.md). */
    private static void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(200);
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("test-evidence");
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }

    private static <T> T await(Consumer<Callback<T>> action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        action.accept(new Callback<T>() {
            @Override
            public void onSuccess(T v) {
                value.set(v);
                done.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                done.countDown();
            }
        });
        assertTrue("Firebase callback timed out", done.await(25, TimeUnit.SECONDS));
        if (error.get() != null) throw error.get();
        return value.get();
    }
}
