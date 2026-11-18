package com.team19.splitsmart.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.repository.GroupRepository;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.DateUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Part B white-box tests (author: Chenyang Wang). Each test is written from the source code of
 * ExpenseService (createExpense, updateExpense, deleteExpense and their private helpers validate,
 * loadEditable and withGroup) and aims to execute every branch in it (branch coverage). The
 * comment above each test lists the branches it covers.
 *
 * Firebase is replaced by in-memory fakes, so no emulator or network is needed. Each fake can be
 * told to fail, which reaches the onFailure branches that a real database rarely takes.
 *
 * Run: ./gradlew testDebugUnitTest --tests "com.team19.splitsmart.service.PartBWhiteBoxTest"
 */
public class PartBWhiteBoxTest {

    /** Stored transactions by id. Each operation fails if its "fail" field is set. */
    private static class FakeTransactions extends TransactionRepository {
        final Map<String, Transaction> stored = new LinkedHashMap<>();
        Transaction saved;
        Transaction updated;
        Transaction deleted;
        long nextSeq = 7;
        Exception failNextSeq;
        Exception failSave;
        Exception failFind;

        FakeTransactions() {
            super(null); // no Firebase in local unit tests
        }

        @Override
        public void nextSequenceNo(String groupId, Callback<Long> callback) {
            if (failNextSeq != null) callback.onFailure(failNextSeq);
            else callback.onSuccess(nextSeq);
        }

        @Override
        public void save(Transaction t, Callback<Transaction> callback) {
            if (failSave != null) {
                callback.onFailure(failSave);
                return;
            }
            t.setId("new-id");
            saved = t;
            callback.onSuccess(t);
        }

        @Override
        public void update(Transaction t, Callback<Void> callback) {
            updated = t;
            callback.onSuccess(null);
        }

        @Override
        public void delete(Transaction t, Callback<Void> callback) {
            deleted = t;
            callback.onSuccess(null);
        }

        @Override
        public void findById(String groupId, String id, Callback<Transaction> callback) {
            if (failFind != null) callback.onFailure(failFind);
            else callback.onSuccess(stored.get(id));
        }
    }

    /** Returns one group, or null ("deleted"), or fails. */
    private static class FakeGroups extends GroupRepository {
        Group group;
        Exception fail;

        FakeGroups() {
            super(null);
        }

        @Override
        public void findById(String groupId, Callback<Group> callback) {
            if (fail != null) callback.onFailure(fail);
            else callback.onSuccess(group);
        }
    }

    /** Answers isLocked with a fixed value (which may be null), or fails. */
    private static class FakeLocks extends SettlementService {
        Boolean locked = false;
        Exception fail;

        FakeLocks() {
            super(null, null, null);
        }

        @Override
        public void isLocked(Expense expense, Callback<Boolean> callback) {
            if (fail != null) callback.onFailure(fail);
            else callback.onSuccess(locked);
        }
    }

    /** Records how a callback finished. */
    private static class Result implements Callback<Void> {
        int successes;
        Exception error;

        @Override
        public void onSuccess(Void unused) {
            successes++;
        }

        @Override
        public void onFailure(Exception e) {
            error = e;
        }

        String field() {
            return error instanceof ValidationException ? ((ValidationException) error).getField() : null;
        }

        String message() {
            return error == null ? null : error.getMessage();
        }
    }

    private FakeTransactions txRepo;
    private FakeGroups groupRepo;
    private FakeLocks locks;
    private ExpenseService service;

    /** Group "trip": Alice (owner), Bob, Carol, David. */
    @Before
    public void setUp() {
        txRepo = new FakeTransactions();
        groupRepo = new FakeGroups();
        groupRepo.group = new Group("Trip", "", "alice");
        groupRepo.group.setGroupId("trip");
        groupRepo.group.addMember("bob");
        groupRepo.group.addMember("carol");
        groupRepo.group.addMember("david");
        locks = new FakeLocks();
        service = new ExpenseService(txRepo, groupRepo, locks);
    }

    /** $30.00 paid by Alice, split 10 / 10 / 10 between Alice, Bob and Carol. */
    private static Expense dinner(String creatorId) {
        Expense e = new Expense();
        e.setGroupId("trip");
        e.setCreatorId(creatorId);
        e.setTitle("Dinner");
        e.setAmountCents(3000);
        e.setPayerId("alice");
        e.setShares(shares("alice", 1000L, "bob", 1000L, "carol", 1000L));
        return e;
    }

    /** Builds an ordered uid -> cents map from pairs: "a", 1L, "b", 2L, ... */
    private static Map<String, Long> shares(Object... pairs) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Long) pairs[i + 1]);
        }
        return map;
    }

    private Result create(Expense e) {
        Result r = new Result();
        service.createExpense(e, r);
        return r;
    }

    /** Puts a saved copy of Dinner (id "dinner", sequenceNo 3, dated 2026-10-12) in the fake store. */
    private Expense storeDinner(String creatorId) {
        Expense saved = dinner(creatorId);
        saved.setId("dinner");
        saved.setSequenceNo(3);
        saved.setCreatedDate("2026-10-12");
        saved.setCategory(Category.FOOD);
        txRepo.stored.put("dinner", saved);
        return saved;
    }

    /** What the edit form sends: a new object with the id, the new values and the editor as creator. */
    private static Expense editedDinner(String editorId) {
        Expense e = new Expense();
        e.setId("dinner");
        e.setGroupId("trip");
        e.setCreatorId(editorId);
        e.setTitle("Dinner and drinks");
        e.setAmountCents(4000);
        e.setCategory(Category.ENTERTAINMENT);
        e.setPayerId("bob");
        e.setShares(shares("bob", 2000L, "david", 2000L));
        return e;
    }

    /**
     * WB-B1 createExpense control flow. Branches: id != null (reject, true) and id == null (false);
     * in withGroup, the group lookup failing, the group being null, and the group found; in
     * createExpense, validate throwing (covered in WB-B2 and WB-B3) and passing; nextSequenceNo
     * failing and succeeding; save failing and succeeding. Also the last branch of validate:
     * category == null (set to OTHER) and category != null (kept).
     */
    @Test
    public void wbB1_createExpense_coversEveryCallbackPathAndCategoryDefault() {
        // id != null: an existing expense can't be created again.
        Expense existing = dinner("alice");
        existing.setId("already-saved");
        assertEquals("permission", create(existing).field());

        // withGroup: lookup fails, then the group has been deleted.
        groupRepo.fail = new IllegalStateException("offline");
        assertEquals("offline", create(dinner("alice")).message());
        groupRepo.fail = null;
        Group trip = groupRepo.group;
        groupRepo.group = null;
        assertEquals("This group no longer exists", create(dinner("alice")).message());
        groupRepo.group = trip;

        // nextSequenceNo fails, then save fails: the error is passed on, onSuccess is never called.
        txRepo.failNextSeq = new IllegalStateException("counter failed");
        Result counterFailed = create(dinner("alice"));
        assertEquals("counter failed", counterFailed.message());
        assertEquals(0, counterFailed.successes);
        txRepo.failNextSeq = null;
        txRepo.failSave = new IllegalStateException("write failed");
        assertEquals("write failed", create(dinner("alice")).message());
        txRepo.failSave = null;
        assertNull("Nothing was saved on any failure path", txRepo.saved);

        // Success with a null category: sequenceNo and today's date are set, category becomes OTHER.
        Expense noCategory = dinner("alice");
        noCategory.setCategory(null);
        Result ok = create(noCategory);
        assertNull(ok.error);
        assertEquals(1, ok.successes);
        Expense saved = (Expense) txRepo.saved;
        assertEquals(7, saved.getSequenceNo());
        assertEquals(DateUtil.today(), saved.getCreatedDate());
        assertTrue(saved.getCreatedDate().matches("\\d{4}-\\d{2}-\\d{2}"));
        assertEquals(Category.OTHER, saved.getCategory());

        // Success with a category: it is kept.
        Expense food = dinner("alice");
        food.setCategory(Category.FOOD);
        assertNull(create(food).error);
        assertEquals(Category.FOOD, ((Expense) txRepo.saved).getCategory());
    }

    /**
     * WB-B2 validate, first half (the basic fields). Each condition is made true once while all
     * earlier ones are false: title null, title blank, amount 0, amount negative, creator not a
     * member, payer null, payer empty, payer not a member. Then all are false for a creator who
     * isn't a participant (David records an expense he isn't part of).
     */
    @Test
    public void wbB2_validate_coversTitleAmountCreatorAndPayerBranches() {
        Expense noTitle = dinner("alice");
        noTitle.setTitle(null);
        assertEquals("title", create(noTitle).field());
        Expense blankTitle = dinner("alice");
        blankTitle.setTitle("   ");
        assertEquals("title", create(blankTitle).field());

        Expense zero = dinner("alice");
        zero.setAmountCents(0);
        assertEquals("amount", create(zero).field());
        Expense negative = dinner("alice");
        negative.setAmountCents(-1);
        assertEquals("amount", create(negative).field());

        Result outsider = create(dinner("eve"));
        assertEquals("permission", outsider.field());
        assertEquals("The creator must be a member of this group", outsider.message());

        Expense noPayer = dinner("alice");
        noPayer.setPayerId(null);
        assertEquals("payer", create(noPayer).field());
        Expense emptyPayer = dinner("alice");
        emptyPayer.setPayerId("");
        assertEquals("payer", create(emptyPayer).field());
        Expense outsidePayer = dinner("alice");
        outsidePayer.setPayerId("eve");
        assertEquals("The payer must be a member of this group", create(outsidePayer).message());
        assertNull(txRepo.saved);

        // All false: David is a member, not the payer and not a participant. That is allowed.
        assertNull(create(dinner("david")).error);
        assertEquals("david", txRepo.saved.getCreatorId());
    }

    /**
     * WB-B3 validate, second half (participants and shares). Branches: shares null, fewer than 2
     * shares, a participant who isn't a member, the payer not among the participants; in the
     * share loop, a null share, a negative share and a share larger than what is left
     * (share > amount - total); after the loop, total below the amount. Valid boundaries: a $0.00
     * share is allowed, and the smallest amount, 1 cent, splits as 1 / 0.
     */
    @Test
    public void wbB3_validate_coversParticipantAndShareBranches() {
        Expense noShares = dinner("alice");
        noShares.setShares(null);
        assertEquals("participants", create(noShares).field());
        Expense onlyPayer = dinner("alice");
        onlyPayer.setShares(shares("alice", 3000L));
        assertEquals("Choose at least 2 participants", create(onlyPayer).message());
        Expense outsider = dinner("alice");
        outsider.setShares(shares("alice", 1500L, "eve", 1500L));
        assertEquals("Every participant must be a member of this group", create(outsider).message());
        Expense payerLeftOut = dinner("alice");
        payerLeftOut.setShares(shares("bob", 1500L, "carol", 1500L));
        assertEquals("The payer must be one of the participants", create(payerLeftOut).message());

        Expense nullShare = dinner("alice");
        nullShare.setShares(shares("alice", 3000L, "bob", null));
        assertEquals("split", create(nullShare).field());
        Expense negativeShare = dinner("alice");
        negativeShare.setShares(shares("bob", -100L, "alice", 3100L)); // negative first, so "share < 0" decides
        assertEquals("split", create(negativeShare).field());
        Expense tooMuch = dinner("alice"); // 2000 + 2000 > 3000: the second share is over what is left
        tooMuch.setShares(shares("alice", 2000L, "bob", 2000L));
        assertEquals("Shares must be nonnegative and add up to the expense", create(tooMuch).message());
        Expense tooLittle = dinner("alice");
        tooLittle.setShares(shares("alice", 1000L, "bob", 1000L, "carol", 999L));
        assertEquals("The shares must add up to the full amount", create(tooLittle).message());
        assertNull(txRepo.saved);

        // Boundaries that must pass: a $0.00 share, and a 1-cent expense.
        Expense zeroShare = dinner("alice");
        zeroShare.setShares(shares("alice", 0L, "bob", 3000L));
        assertNull(create(zeroShare).error);
        Expense oneCent = dinner("alice");
        oneCent.setAmountCents(1);
        oneCent.setShares(shares("alice", 1L, "bob", 0L));
        assertNull(create(oneCent).error);
        assertEquals(1, txRepo.saved.getAmountCents());
    }

    /**
     * WB-B4 loadEditable, through both updateExpense and deleteExpense. Branches: findById
     * failing; the result not being an Expense (null, and a Settlement); editorId null; editorId
     * not the creator; isLocked failing; locked true; locked null (anything but FALSE counts as
     * locked); locked false (continues).
     */
    @Test
    public void wbB4_editAndDelete_coverEveryPermissionAndLockBranch() {
        Expense saved = storeDinner("alice");

        txRepo.failFind = new IllegalStateException("read failed");
        Result readFailed = new Result();
        service.deleteExpense(saved, "alice", readFailed);
        assertEquals("read failed", readFailed.message());
        txRepo.failFind = null;

        Result missing = new Result();
        Expense gone = editedDinner("alice");
        gone.setId("no-such-id");
        service.updateExpense(gone, "alice", missing);
        assertEquals("This expense no longer exists", missing.message());

        Settlement settlement = new Settlement();
        settlement.setId("s1");
        settlement.setCreatorId("alice");
        txRepo.stored.put("s1", settlement);
        Expense wrongType = editedDinner("alice");
        wrongType.setId("s1");
        Result notAnExpense = new Result();
        service.deleteExpense(wrongType, "alice", notAnExpense);
        assertEquals("This expense no longer exists", notAnExpense.message());

        Result noEditor = new Result();
        service.updateExpense(editedDinner(null), null, noEditor);
        assertEquals("permission", noEditor.field());
        Result notCreator = new Result();
        service.deleteExpense(saved, "bob", notCreator);
        assertEquals("Only the creator can delete this expense", notCreator.message());

        locks.fail = new IllegalStateException("lock check failed");
        Result lockFailed = new Result();
        service.updateExpense(editedDinner("alice"), "alice", lockFailed);
        assertEquals("lock check failed", lockFailed.message());
        locks.fail = null;
        locks.locked = true;
        Result lockedEdit = new Result();
        service.updateExpense(editedDinner("alice"), "alice", lockedEdit);
        assertEquals("This expense is locked by a later settlement", lockedEdit.message());
        locks.locked = null;
        Result unknownLock = new Result();
        service.deleteExpense(saved, "alice", unknownLock);
        assertEquals("permission", unknownLock.field());
        assertNull("Nothing was changed on any failure path", txRepo.updated);
        assertNull(txRepo.deleted);

        // locked == false: the creator can delete, and the saved version is what gets deleted.
        locks.locked = false;
        Result deleted = new Result();
        Expense staleCopy = editedDinner("alice");
        service.deleteExpense(staleCopy, "alice", deleted);
        assertEquals(1, deleted.successes);
        assertSame(saved, txRepo.deleted);
    }

    /**
     * WB-B5 updateExpense after loadEditable passes. Branches: withGroup finding no group;
     * validate throwing on the new values; validate passing, where createdDate, sequenceNo and
     * creatorId must come from the saved version (FR-2.1) while payer, participants, amount,
     * title and category come from the form.
     */
    @Test
    public void wbB5_updateExpense_revalidatesAndKeepsOriginalDateOrderAndCreator() {
        storeDinner("alice");

        Group trip = groupRepo.group;
        groupRepo.group = null;
        Result noGroup = new Result();
        service.updateExpense(editedDinner("alice"), "alice", noGroup);
        assertEquals("This group no longer exists", noGroup.message());
        groupRepo.group = trip;

        Expense invalid = editedDinner("alice");
        invalid.setAmountCents(0);
        Result rejected = new Result();
        service.updateExpense(invalid, "alice", rejected);
        assertEquals("amount", rejected.field());
        assertNull(txRepo.updated);

        // The form sends a different date, order and creator; the saved ones must win.
        Expense edit = editedDinner("alice");
        edit.setCreatedDate("2030-01-01");
        edit.setSequenceNo(99);
        Result ok = new Result();
        service.updateExpense(edit, "alice", ok);
        assertNull(ok.error);
        assertEquals(1, ok.successes);
        Expense updated = (Expense) txRepo.updated;
        assertEquals("2026-10-12", updated.getCreatedDate());
        assertEquals(3, updated.getSequenceNo());
        assertEquals("alice", updated.getCreatorId());
        assertEquals("Dinner and drinks", updated.getTitle());
        assertEquals(4000, updated.getAmountCents());
        assertEquals(Category.ENTERTAINMENT, updated.getCategory());
        assertEquals("bob", updated.getPayerId());
        assertEquals(Arrays.asList("bob", "david"), updated.getInvolvedUserIds());
    }
}
