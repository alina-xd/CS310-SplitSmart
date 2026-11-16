package com.team19.splitsmart;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
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
import com.team19.splitsmart.ui.summary.MonthlySummaryActivity;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Part C black-box tests (author: Dawi Yoon), written with Espresso from the requirements
 * (FR-2.4 split methods, FR-2.5 rounding, FR-5.2 monthly summary) without relying on how the
 * code works inside. Each test drives the real screens, the way a user would.
 *
 * Runs against the local Firebase emulators (see tests/README.md), never the shared database:
 *   ./gradlew connectedDebugAndroidTest --no-watch-fs \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.team19.splitsmart.PartCBlackBoxTest
 */
@RunWith(AndroidJUnit4.class)
public class PartCBlackBoxTest {
    private static final String PASSWORD = "test-password-310";
    private static final String[] NAMES = {"Alice", "Bob", "Carol", "David"};
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
        long idBase = 1000000000L + Math.floorMod(System.currentTimeMillis() + 4242, 8000000000L);
        for (int i = 0; i < NAMES.length; i++) {
            final int index = i;
            users.add(await(cb -> auth.register(NAMES[index], "partc-" + suffix + "-" + index + "@usc.edu",
                    String.valueOf(idBase + index), PASSWORD, cb)));
        }
    }

    /** Alice (logged in) owns a fresh group with Bob, Carol and David for every test. */
    @Before
    public void createGroup() throws Exception {
        PartCBlackBoxTest.<User>await(cb -> auth.login(users.get(0).getUscEmail(), PASSWORD, cb));
        group = await(cb -> groups.createGroup("Part C test", "Black-box tests", uid(0), cb));
        for (int i = 1; i < NAMES.length; i++) {
            final int index = i;
            PartCBlackBoxTest.<Void>await(
                    cb -> groups.addMember(group.getGroupId(), users.get(index).getUscEmail(), uid(0), cb));
        }
    }

    @After
    public void deleteGroup() throws Exception {
        if (group != null) {
            PartCBlackBoxTest.<Void>await(cb -> groups.deleteGroup(group.getGroupId(), uid(0), cb));
        }
    }

    /**
     * BB-C1 Equal split that doesn't divide evenly (FR-2.4.1, FR-2.5). $10.00 among Alice, Bob and
     * Carol (David unchecked). Boundary case from the customer's own example: the shares must be
     * $3.34, $3.33, $3.33 before saving, and the saved shares must add up to exactly $10.00.
     */
    @Test
    public void bbC1_equalSplitOfTenDollarsThreeWays_showsExtraCentAndSaves() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> screen = openExpenseForm()) {
            typeTitleAndAmount("Pizza", "10.00");
            uncheckParticipant("David");
            waitFor(() -> rowCount(screen) == 3);

            assertEquals("Equal is the default split method", "Shares add up to $10.00", status(screen));
            assertTrue(shareText(screen, 0).startsWith("$3.34"));
            assertTrue(shareText(screen, 1).startsWith("$3.33"));
            assertTrue(shareText(screen, 2).startsWith("$3.33"));
            assertTrue("Non-payers show who they owe", shareText(screen, 1).contains("owes Alice"));
            screenshot("bb-c1-equal-split");

            save(screen);
            waitFor(() -> screen.getState() == Lifecycle.State.DESTROYED);
        }
        Expense saved = onlyExpense();
        assertEquals(SplitMethod.EQUAL, saved.getSplitMethod());
        assertEquals(1000, sumOf(saved.getShares()));
        assertEquals(3, saved.getShares().size());
    }

    /**
     * BB-C2 Exact amounts that don't add up are rejected (FR-2.4.2). $30.00 with 5 / 10 / 10
     * (invalid class: sum below the total) must show the sum, the remaining amount, and must not
     * save. Correcting to 5 / 10 / 15 (valid class) must save with those exact shares.
     */
    @Test
    public void bbC2_exactAmountsNotAddingUp_blockSave_thenCorrectedAmountsSave() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> screen = openExpenseForm()) {
            typeTitleAndAmount("Groceries", "30.00");
            uncheckParticipant("David");
            onView(withId(R.id.radioSplitExact)).perform(scrollTo(), click());
            waitFor(() -> rowCount(screen) == 3);

            fillSplit(screen, "5", "10", "10");
            waitFor(() -> status(screen).contains("$25.00 but the expense is $30.00"));
            assertTrue(status(screen).contains("$5.00 left to assign"));
            save(screen);
            waitFor(() -> visible(screen, R.id.textSplitError));
            screenshot("bb-c2-exact-mismatch");
            assertEquals("Nothing is saved while the amounts are wrong", 0, all().size());

            fillSplit(screen, "5", "10", "15");
            waitFor(() -> status(screen).equals("Shares add up to $30.00"));
            save(screen);
            waitFor(() -> screen.getState() == Lifecycle.State.DESTROYED);
        }
        Expense saved = onlyExpense();
        assertEquals(SplitMethod.EXACT_AMOUNT, saved.getSplitMethod());
        assertEquals(500L, saved.getShare(uid(0)));
        assertEquals(1000L, saved.getShare(uid(1)));
        assertEquals(1500L, saved.getShare(uid(2)));
    }

    /**
     * BB-C3 Percentages must add up to exactly 100 (FR-2.4.3). 20 / 30 / 40 % (invalid: 90%)
     * is rejected with the total shown; 0 / 50 / 50 % (boundary: a 0% participant is allowed)
     * saves as $0.00 / $15.00 / $15.00.
     */
    @Test
    public void bbC3_percentagesMustTotal100_andZeroPercentIsAllowed() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> screen = openExpenseForm()) {
            typeTitleAndAmount("Gas", "30.00");
            uncheckParticipant("David");
            onView(withId(R.id.radioSplitPercent)).perform(scrollTo(), click());
            waitFor(() -> rowCount(screen) == 3);

            fillSplit(screen, "20", "30", "40");
            waitFor(() -> status(screen).contains("Percentages add up to 90%"));
            assertTrue(status(screen).contains("10% left to assign"));
            screenshot("bb-c3-percent-not-100");
            save(screen);
            waitFor(() -> visible(screen, R.id.textSplitError));
            assertEquals(0, all().size());

            fillSplit(screen, "0", "50", "50");
            waitFor(() -> status(screen).equals("Shares add up to $30.00"));
            assertTrue(shareText(screen, 0).startsWith("$0.00"));
            assertTrue(shareText(screen, 1).startsWith("$15.00"));
            save(screen);
            waitFor(() -> screen.getState() == Lifecycle.State.DESTROYED);
        }
        Expense saved = onlyExpense();
        assertEquals(SplitMethod.PERCENTAGE, saved.getSplitMethod());
        assertEquals(0L, saved.getShare(uid(0)));
        assertEquals(1500L, saved.getShare(uid(1)));
        assertEquals(1500L, saved.getShare(uid(2)));
    }

    /**
     * BB-C4 A blank split box is a user error, not a crash (FR-2.4.2: every participant must be
     * assigned an amount). Leaving Carol's exact amount empty must show "Enter an amount for every
     * participant" and keep the form open. Regression test: this input used to crash the app with
     * a NullPointerException in ExpenseSplitSection (fixed in commit e0da7d2).
     */
    @Test
    public void bbC4_blankExactAmount_showsMessageInsteadOfCrashing() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> screen = openExpenseForm()) {
            typeTitleAndAmount("Snacks", "12.00");
            uncheckParticipant("David");
            onView(withId(R.id.radioSplitExact)).perform(scrollTo(), click());
            waitFor(() -> rowCount(screen) == 3);

            fillSplit(screen, "6", "6", "");
            waitFor(() -> status(screen).contains("Enter an amount for every participant"));
            save(screen);
            waitFor(() -> visible(screen, R.id.textSplitError));
            screenshot("bb-c4-blank-amount");
            assertEquals("The form is still open", Lifecycle.State.RESUMED, screen.getState());
            assertEquals(0, all().size());
        }
    }

    /**
     * BB-C5 Monthly summary (FR-5.2). This month: Food $30.00 + Transportation $20.00 and a $5.00
     * settlement; last month: Housing $100.00. The screen opens on the current month and must show
     * 2 expenses and $50.00 (settlement excluded, other month excluded). Adding an expense while
     * the screen is open must update it by itself, and choosing last month shows only that month.
     */
    @Test
    public void bbC5_monthlySummary_countsOnlyThisMonthsExpenses_andUpdatesLive() throws Exception {
        createExpense("Dinner", 3000, Category.FOOD, null);
        createExpense("Uber", 2000, Category.TRANSPORTATION, null);
        createExpense("Rent", 10000, Category.HOUSING, lastMonth() + "-15");
        Settlement paid = new Settlement();
        paid.setGroupId(group.getGroupId());
        paid.setCreatorId(uid(1));
        paid.setPayerId(uid(1));
        paid.setRecipientId(uid(0));
        paid.setAmountCents(500);
        PartCBlackBoxTest.<Void>await(cb -> settlements.createSettlement(paid, cb));

        try (ActivityScenario<MonthlySummaryActivity> screen = ActivityScenario.launch(groupIntent(MonthlySummaryActivity.class))) {
            waitFor(() -> text(screen, R.id.textTotalSpending).equals("Total spending: $50.00"));
            assertEquals("Opens on the current month", monthLabel(DateUtil.monthOf(DateUtil.today())),
                    selectedMonth(screen));
            assertEquals("2 expenses", text(screen, R.id.textExpenseCount));
            onView(allOf(withText("Food"), isDescendantOfA(withId(R.id.categoryRows)))).check(matches(isDisplayed()));
            onView(allOf(withText("$30.00"), isDescendantOfA(withId(R.id.categoryRows)))).check(matches(isDisplayed()));
            onView(allOf(withText("$20.00"), isDescendantOfA(withId(R.id.categoryRows)))).check(matches(isDisplayed()));
            assertEquals("All 5 categories are listed", 5, childCount(screen, R.id.categoryRows));
            screenshot("bb-c5-monthly-current");

            createExpense("Movie", 1250, Category.ENTERTAINMENT, null);
            waitFor(() -> text(screen, R.id.textTotalSpending).equals("Total spending: $62.50"));
            assertEquals("3 expenses", text(screen, R.id.textExpenseCount));

            selectMonth(screen, monthLabel(lastMonth()));
            waitFor(() -> text(screen, R.id.textTotalSpending).equals("Total spending: $100.00"));
            assertEquals("1 expense", text(screen, R.id.textExpenseCount));
            screenshot("bb-c5-monthly-last-month");
        }
    }

    // ---- Steps a user takes ----

    private ActivityScenario<ExpenseFormActivity> openExpenseForm() {
        ActivityScenario<ExpenseFormActivity> screen = ActivityScenario.launch(groupIntent(ExpenseFormActivity.class));
        waitFor(() -> childCount(screen, R.id.layoutParticipants) == NAMES.length);
        return screen;
    }

    private void typeTitleAndAmount(String title, String amount) {
        onView(withId(R.id.editExpenseTitle)).perform(scrollTo(), replaceText(title), closeSoftKeyboard());
        onView(withId(R.id.editExpenseAmount)).perform(scrollTo(), replaceText(amount), closeSoftKeyboard());
    }

    private void uncheckParticipant(String name) {
        onView(allOf(withText(name), isDescendantOfA(withId(R.id.layoutParticipants))))
                .perform(scrollTo(), click());
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

    private void selectMonth(ActivityScenario<MonthlySummaryActivity> screen, String label) {
        screen.onActivity(activity -> {
            Spinner months = activity.findViewById(R.id.spinnerMonth);
            for (int i = 0; i < months.getCount(); i++) {
                if (label.equals(months.getItemAtPosition(i).toString())) {
                    months.setSelection(i);
                    return;
                }
            }
        });
    }

    // ---- Reading the screen ----

    private static String status(ActivityScenario<ExpenseFormActivity> screen) {
        return text(screen, R.id.textSplitStatus);
    }

    private static int rowCount(ActivityScenario<ExpenseFormActivity> screen) {
        return childCount(screen, R.id.splitRows);
    }

    /** The share shown at the end of split row i, for example "$3.33\nowes Alice". */
    private static String shareText(ActivityScenario<ExpenseFormActivity> screen, int i) {
        AtomicReference<String> result = new AtomicReference<>("");
        screen.onActivity(activity -> {
            LinearLayout row = (LinearLayout) ((LinearLayout) activity.findViewById(R.id.splitRows)).getChildAt(i);
            result.set(((TextView) row.getChildAt(row.getChildCount() - 1)).getText().toString());
        });
        return result.get();
    }

    private static String selectedMonth(ActivityScenario<MonthlySummaryActivity> screen) {
        AtomicReference<String> result = new AtomicReference<>("");
        screen.onActivity(a -> result.set(String.valueOf(((Spinner) a.findViewById(R.id.spinnerMonth)).getSelectedItem())));
        return result.get();
    }

    private static <A extends android.app.Activity> String text(ActivityScenario<A> screen, int id) {
        AtomicReference<String> result = new AtomicReference<>("");
        screen.onActivity(a -> result.set(((TextView) a.findViewById(id)).getText().toString()));
        return result.get();
    }

    private static <A extends android.app.Activity> int childCount(ActivityScenario<A> screen, int id) {
        AtomicReference<Integer> result = new AtomicReference<>(0);
        screen.onActivity(a -> result.set(((LinearLayout) a.findViewById(id)).getChildCount()));
        return result.get();
    }

    private static <A extends android.app.Activity> boolean visible(ActivityScenario<A> screen, int id) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        screen.onActivity(a -> result.set(a.findViewById(id).getVisibility() == View.VISIBLE));
        return result.get();
    }

    // ---- Test data ----

    private String uid(int i) {
        return users.get(i).getUid();
    }

    private Intent groupIntent(Class<?> screen) {
        return new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), screen)
                .putExtra(Extras.GROUP_ID, group.getGroupId());
    }

    /** Alice pays, split equally with Bob. A non-null date moves the expense to that day. */
    private void createExpense(String title, long cents, Category category, String date) throws Exception {
        Expense e = new Expense();
        e.setGroupId(group.getGroupId());
        e.setCreatorId(uid(0));
        e.setPayerId(uid(0));
        e.setTitle(title);
        e.setAmountCents(cents);
        e.setCategory(category);
        e.setSplitMethod(SplitMethod.EQUAL);
        Map<String, Double> people = new LinkedHashMap<>();
        people.put(uid(0), 0.0);
        people.put(uid(1), 0.0);
        e.setShares(expenses.previewShares(cents, SplitMethod.EQUAL, people));
        PartCBlackBoxTest.<Void>await(cb -> expenses.createExpense(e, cb));
        if (date != null) {
            e.setCreatedDate(date);
            PartCBlackBoxTest.<Void>await(cb -> transactions.update(e, cb));
        }
    }

    private List<Transaction> all() throws Exception {
        return await(cb -> transactions.findByGroup(group.getGroupId(), cb));
    }

    private Expense onlyExpense() throws Exception {
        List<Transaction> list = all();
        assertEquals(1, list.size());
        return (Expense) list.get(0);
    }

    private static long sumOf(Map<String, Long> shares) {
        long total = 0;
        for (long v : shares.values()) total += v;
        return total;
    }

    private static String lastMonth() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, -1);
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(c.getTime());
    }

    private static String monthLabel(String yyyyMm) throws Exception {
        return new SimpleDateFormat("MMMM yyyy", Locale.US)
                .format(new SimpleDateFormat("yyyy-MM", Locale.US).parse(yyyyMm));
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
