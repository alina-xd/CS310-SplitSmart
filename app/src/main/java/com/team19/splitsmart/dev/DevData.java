package com.team19.splitsmart.dev;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.model.SplitMethod;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.data.repository.FirestorePaths;
import com.team19.splitsmart.util.Callback;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes fixed test data to Firestore so each part can work without waiting for the others.
 * Running it again resets these documents (fixed ids). The users have no Firebase login;
 * pick one in Developer tools instead. Development only.
 *
 * Expected numbers in the Trip group (see README, "Test data"):
 *   1 Dinner $30.00, Alice paid, equal A/B/C        -> B owes A $10.00, C owes A $10.00
 *   2 Taxi   $20.00, Bob paid, exact A $5.00/B $15.00 -> A owes B $5.00
 *   3 Movie  $10.00, Carol paid, equal A/B/C          -> A $3.34, B $3.33, C $3.33 (C paid)
 *   4 Settlement Bob -> Alice $3.00
 *   Alice: Bob owes her $2.00, Carol owes her $6.66 (receive $8.66, pay $0.00).
 *   Bob: owes Alice $2.00, owes Carol $3.33. Carol: owes Alice $6.66, Bob owes her $3.33.
 *   Locked by settlement 4 (Bob-Alice): Dinner and Taxi. Movie is not locked.
 *   October: 2 expenses, $50.00 (Food $30.00, Transportation $20.00). November: 1 expense, $10.00.
 */
public final class DevData {

    public static final String ALICE = "dev-alice";
    public static final String BOB = "dev-bob";
    public static final String CAROL = "dev-carol";
    public static final String DAVID = "dev-david";

    public static final String GROUP_TRIP = "dev-group-trip";
    public static final String GROUP_HOME = "dev-group-home";
    public static final String GROUP_DISPOSABLE = "dev-group-disposable";

    private DevData() {
    }

    public static void seed(Callback<Void> callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();

        user(db, batch, ALICE, "Alice", "alice.dev@usc.edu", "9000000001");
        user(db, batch, BOB, "Bob", "bob.dev@usc.edu", "9000000002");
        user(db, batch, CAROL, "Carol", "carol.dev@usc.edu", "9000000003");
        user(db, batch, DAVID, "David", "david.dev@usc.edu", "9000000004");

        group(db, batch, GROUP_TRIP, "Trip (dev)", "Alice owns; all four are members", ALICE, 4, BOB, CAROL, DAVID);
        group(db, batch, GROUP_HOME, "Apartment (dev)", "Bob owns", BOB, 1, ALICE, CAROL);
        group(db, batch, GROUP_DISPOSABLE, "Disposable (dev)", "For the delete-group demo", ALICE, 1, BOB);

        expense(db, batch, GROUP_TRIP, "dev-trip-1", 1, "2026-10-12", "Dinner", Category.FOOD, ALICE,
                SplitMethod.EQUAL, 3000, "Thai place", ALICE, 1000L, BOB, 1000L, CAROL, 1000L);
        expense(db, batch, GROUP_TRIP, "dev-trip-2", 2, "2026-10-20", "Taxi", Category.TRANSPORTATION, BOB,
                SplitMethod.EXACT_AMOUNT, 2000, null, ALICE, 500L, BOB, 1500L);
        expense(db, batch, GROUP_TRIP, "dev-trip-3", 3, "2026-11-02", "Movie", Category.ENTERTAINMENT, CAROL,
                SplitMethod.EQUAL, 1000, "IMAX", ALICE, 334L, BOB, 333L, CAROL, 333L);
        settlement(db, batch, GROUP_TRIP, "dev-trip-4", 4, "2026-11-02", BOB, ALICE, 300, "Venmo");

        expense(db, batch, GROUP_HOME, "dev-home-1", 1, "2026-10-01", "Rent", Category.HOUSING, BOB,
                SplitMethod.EQUAL, 150000, null, ALICE, 50000L, BOB, 50000L, CAROL, 50000L);
        expense(db, batch, GROUP_DISPOSABLE, "dev-disp-1", 1, "2026-11-01", "Snacks", Category.FOOD, ALICE,
                SplitMethod.EQUAL, 800, null, ALICE, 400L, BOB, 400L);

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    private static void user(FirebaseFirestore db, WriteBatch batch, String uid, String name, String email, String uscId) {
        batch.set(db.collection(FirestorePaths.USERS).document(uid), new User(uid, name, email, uscId));
    }

    private static void group(FirebaseFirestore db, WriteBatch batch, String id, String name, String description,
                              String ownerId, long seqCounter, String... otherMembers) {
        Group g = new Group(name, description, ownerId);
        for (String uid : otherMembers) {
            g.addMember(uid);
        }
        g.setGroupId(id);
        DocumentReference ref = db.collection(FirestorePaths.GROUPS).document(id);
        batch.set(ref, g);
        batch.set(ref, Collections.singletonMap(FirestorePaths.GROUP_SEQ_COUNTER, seqCounter), SetOptions.merge());
    }

    /** shares are given as uid, cents, uid, cents, ... */
    private static void expense(FirebaseFirestore db, WriteBatch batch, String groupId, String id, long seq, String date,
                                String title, Category category, String payerId, SplitMethod method,
                                long amountCents, String note, Object... shares) {
        Expense e = new Expense();
        e.setTitle(title);
        e.setCategory(category);
        e.setPayerId(payerId);
        e.setSplitMethod(method);
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < shares.length; i += 2) {
            map.put((String) shares[i], (Long) shares[i + 1]);
        }
        e.setShares(map);
        if (method == SplitMethod.EXACT_AMOUNT) {
            Map<String, Double> inputs = new LinkedHashMap<>();
            for (Map.Entry<String, Long> share : map.entrySet()) {
                inputs.put(share.getKey(), share.getValue() / 100.0);
            }
            e.setSplitInputs(inputs);
        }
        fill(e, groupId, id, seq, date, payerId, amountCents, note);
        write(db, batch, e);
    }

    private static void settlement(FirebaseFirestore db, WriteBatch batch, String groupId, String id, long seq,
                                   String date, String payerId, String recipientId, long amountCents, String note) {
        Settlement s = new Settlement();
        s.setPayerId(payerId);
        s.setRecipientId(recipientId);
        fill(s, groupId, id, seq, date, payerId, amountCents, note);
        write(db, batch, s);
    }

    private static void fill(Transaction t, String groupId, String id, long seq, String date, String creatorId,
                             long amountCents, String note) {
        t.setId(id);
        t.setGroupId(groupId);
        t.setSequenceNo(seq);
        t.setCreatedDate(date);
        t.setCreatorId(creatorId);
        t.setAmountCents(amountCents);
        t.setNote(note);
    }

    private static void write(FirebaseFirestore db, WriteBatch batch, Transaction t) {
        batch.set(db.collection(FirestorePaths.GROUPS).document(t.getGroupId())
                .collection(FirestorePaths.TRANSACTIONS).document(t.getId()), t);
    }
}
