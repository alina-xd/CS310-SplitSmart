# Part B testing (expenses)

Author of every test below: **Chenyang Wang** (cwang120@usc.edu). Covers Feature 2 (recording, editing and deleting expenses: UC-2, FR-2.1 to FR-2.3) and the creator-only, not-settled edit rule of FR-4.2 as it applies to expenses.

| Type | Tool | File |
|---|---|---|
| White-box | JUnit 4 (local unit tests) | `app/src/test/java/com/team19/splitsmart/service/PartBWhiteBoxTest.java` |
| Black-box | Espresso (instrumented tests) | `app/src/androidTest/java/com/team19/splitsmart/PartBBlackBoxTest.java` |

## How to run

**White-box** (no emulator needed), from the project root:

```sh
./gradlew testDebugUnitTest --tests "com.team19.splitsmart.service.PartBWhiteBoxTest" --no-watch-fs
```

Or in Android Studio: open `PartBWhiteBoxTest`, then click the green arrow next to the class name and choose **Run**. The report is written to `app/build/reports/tests/testDebugUnitTest/index.html`.

**Black-box** (needs an Android emulator and the Firebase emulators, see `tests/README.md`). Start the Firebase emulators in one terminal:

```sh
firebase emulators:start --only auth,firestore --project demo-splitsmart --config tests/firebase/firebase.json
```

Then, with the Android emulator running:

```sh
./gradlew connectedDebugAndroidTest --no-watch-fs -Pandroid.testInstrumentationRunnerArguments.class=com.team19.splitsmart.PartBBlackBoxTest
```

To run a single test, add `#` and the test name to the class, for example `...PartBBlackBoxTest#bbB5_creatorEditsUnlockedExpense_dateAndOrderStayTheSame`. To keep the screenshots, add `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`. Turning off the emulator's animations makes Espresso runs more reliable.

The tests register their own users (Alice, Bob, Carol, David and Eve, with random emails), create a fresh group owned by Alice with Bob, Carol and David as members, and delete the group afterwards. Eve has an account but is never added to the group. The tests never touch the shared database; they fail right away if Firestore isn't pointed at the local emulator. Screenshots are saved on the emulator under `/sdcard/Android/data/com.team19.splitsmart/files/test-evidence/` (`bb-b1-complete-expense.png` and so on).

## Black-box tests

Designed from the requirements only (UC-2, FR-2.1 to FR-2.3, FR-4.2), using equivalence classes (valid and invalid inputs) and boundary values. Each test drives the real expense form with Espresso: typing into the fields, picking from the spinners and tapping checkboxes and Save. It then checks what the user sees and what was saved in Firestore.

### BB-B1 `bbB1_completeExpense_savesEveryFieldWithTodaysDate`
- **What it does:** As Alice, opens Add expense. Before typing, checks that the category list is exactly Food, Housing, Transportation, Entertainment, Other, that Other is selected, and that the payer is Alice. Then enters "Dinner", $30.00, picks Food, types the note "Birthday", keeps all 4 members and the Equal split, and saves. Checks the saved expense: title, 3000 cents, Food, payer and creator Alice, the note, an Equal split of $7.50 each, and today's date as `YYYY-MM-DD`.
- **Rationale:** The main success path of UC-2 with every field filled in, which is the valid class for every input at once. It also checks the defaults the customer asked for (FR-2.2: Other is the default and there are exactly 5 categories) and that the date is set automatically (FR-2.1). $30.00 among 4 people divides evenly, so any wrong share would show up as a value other than 750.
- **Result:** passes (Pixel 4 emulator, Android 13, local Firebase emulators, 2026-11-17).
- **Bugs found:** none.

### BB-B2 `bbB2_invalidTitleAmountAndSplit_areRefused_andOneCentSaves`
- **What it does:**
  - A title of only spaces with a valid amount: Save shows "Enter a title" on the title field.
  - With a valid title, Save is tried with the amounts `0`, `-5.00`, `12.345` and an empty box. Each shows its error on the amount field: "The amount must be more than $0.00" for the first two, and "Enter a valid amount with at most 2 decimals" for the last two. Nothing is saved.
  - $12.00 with an Exact split of 3 / 3 / 3 / 1 (sum $10.00). The split section says "Shares add up to $10.00 but the expense is $12.00". Save must show a different message below it, not the same text again.
  - Switches back to Equal and saves $0.01, which must save with the default category Other and shares that add up to 1 cent.
- **Rationale:** One input from each invalid class in UC-2 alternate path 5.2.1: an empty title, an amount that isn't greater than zero (0 is the boundary, -5.00 is clearly below it), an amount with more than two decimals (3 decimals is the boundary), and no amount at all. $0.01 is the smallest valid amount, the boundary on the valid side. Splitting 1 cent among 4 people also checks that shares of $0.00 are accepted. The split case is the regression test for the bug below.
- **Result:** passes (Pixel 4 emulator, Android 13, local Firebase emulators, 2026-11-17).
- **Bug found (first reported by Part C's BB-C2, confirmed by this test):**
  - **What it was:** when Save was pressed with an invalid split, the same error appeared twice. The split section already shows it in its status line, and `ExpenseFormActivity.onSaveClicked` passed the same exception to `showFieldError`, which copied the message into `textSplitError` right below.
  - **Status:** fixed. When `splitSection.getShares()` rejects the split, the form now shows "Fix the split above before saving." in `textSplitError` instead of repeating the message (new string `b_fix_split` in `strings_part_b.xml`). Errors about the split that come from `ExpenseService` are still shown in full, because the section doesn't show those.
  - **Test:** the "not repeated" assertion fails on the old code (checked: "Actual: Shares add up to $10.00 but the expense is $12.00") and passes on the fixed code. Part C's black-box tests still pass after the fix.

### BB-B3 `bbB3_payerAndParticipants_comeFromGroup_andPayerCantBeUnchecked`
- **What it does:**
  - Checks that the payer list and the participant checkboxes are exactly Alice, Bob, Carol, David. Eve has an account but isn't in the group, so she must not appear.
  - Checks that Alice (the payer) is checked and disabled, while Bob is checked and enabled.
  - Picks Bob as the payer. Bob's box must become checked and disabled, and Alice's box enabled.
  - Picks Alice again, then unchecks Bob, Carol and David, leaving only the payer. The split section must say "Select at least one participant besides the payer", Save must be refused with an error, the form must stay open, and nothing is saved.
- **Rationale:** FR-2.3 says the payer and participants must be group members, and the payer is always a participant. Eve is the "registered user who isn't a member" class, the input most likely to leak into the lists by mistake. Switching the payer checks that the locked box moves with the payer. One participant (the payer alone) is the boundary just below the minimum of 2 (UC-2 5.2.1).
- **Result:** passes (Pixel 4 emulator, Android 13, local Firebase emulators, 2026-11-17).
- **Bugs found:** none.

### BB-B4 `bbB4_memberRecordsExpensePaidBySomeoneElse_withoutThemselves`
- **What it does:** As Alice, records "Taxi" for $20.00 paid by Bob, then unchecks herself and David so that only Bob and Carol take part. Checks that the saved expense has Alice as creator, Bob as payer, and exactly Bob and Carol as participants, with $10.00 each.
- **Rationale:** The README's Part B checklist says a member can record an expense where someone else pays and they themselves aren't a participant. This is a separate valid class from BB-B1, where the creator, the payer and a participant are all the same person. It catches code that assumes the person filling in the form is always the payer or always a participant.
- **Result:** passes (Pixel 4 emulator, Android 13, local Firebase emulators, 2026-11-17).
- **Bugs found:** none.

### BB-B5 `bbB5_creatorEditsUnlockedExpense_dateAndOrderStayTheSame`
- **What it does:** Creates "Dinner" ($30.00, Food, paid by Alice, equal among Alice, Bob and Carol) and backdates it to 2026-10-12. As Alice, opens it in the edit form:
  - Checks that the heading says "Edit expense" and that the form shows the saved values: $30.00, Food, payer Alice, and David unchecked.
  - Changes the amount to $40.00, the payer to Bob, unchecks Carol and checks David, then saves.
  - Checks that the same expense was updated (same ID, still only one expense), that its date is still 2026-10-12 with the same sequence number and creator, and that it now has $40.00, payer Bob, and participants Alice, Bob and David, with shares adding up to $40.00.
- **Rationale:** UC-2 alternate path 8.1 lets the creator change any field, and FR-2.1 says editing must not change the creation date. The expense is backdated so that a wrong implementation that resets the date to today would fail. Changing the amount, the payer and the participants at once covers the three edits the README checklist names.
- **Result:** passes (Pixel 4 emulator, Android 13, local Firebase emulators, 2026-11-17).
- **Bugs found:** none.

### BB-B6 `bbB6_nonCreatorAndSettledExpense_cannotBeEdited`
- **What it does:** Creates Alice's "Dinner" as in BB-B5.
  - Logs in as Bob, opens it in the edit form and tries to rename it. Save must show "Only the creator can edit this expense", the form must stay open, and the title must still be "Dinner".
  - Bob then records a $3.00 settlement to Alice, which settles Dinner.
  - Logs in as Alice, opens it and tries to change the amount to $36.00. Save must show "This expense is locked by a later settlement", and the saved amount must still be $30.00.
- **Rationale:** Two invalid classes of editor: someone who isn't the creator, and the creator of an expense that has been settled (UC-2 alternate path 8.3, FR-4.2). Normally the detail screen hides Edit in both cases. This test opens the form directly, as if the screen had been opened before the settlement was recorded, to check that `ExpenseService` enforces the rule itself when saving.
- **Result:** passes (Pixel 4 emulator, Android 13, local Firebase emulators, 2026-11-17).
- **Bugs found:** none.

## White-box tests

Written from the source code of `ExpenseService` (Part B's methods `createExpense`, `updateExpense` and `deleteExpense`, and their private helpers `validate`, `loadEditable` and `withGroup`). **Coverage criterion: branch coverage** (every true and false outcome of every `if`, every `||` operand and every loop condition), measured with JaCoCo.

Firebase is replaced by small in-memory fakes of `TransactionRepository`, `GroupRepository` and `SettlementService`, so the tests run in a few milliseconds without an emulator. Each fake can be told to fail, which reaches the `onFailure` paths that a real database rarely takes.

### WB-B1 `wbB1_createExpense_coversEveryCallbackPathAndCategoryDefault`
- **What it does:**
  - An expense that already has an ID is rejected (the `id != null` branch).
  - The group lookup fails, and then the group has been deleted (the `withGroup` failure and `group == null` branches).
  - `nextSequenceNo` fails, then `save` fails. Each error must reach the caller, `onSuccess` must never be called, and nothing is saved.
  - A successful save with a null category must get sequence number 7 from the counter, today's date in `YYYY-MM-DD` form, and category Other.
  - A successful save with Food must keep Food.
- **Result:** passes. Together with WB-B2 and WB-B3, it covers every branch of `createExpense`.
- **Bugs found:** none. While measuring coverage, the first version of this test missed the `category == null` branch, because `new Expense()` already defaults to Other. The test now sets the category to null explicitly.

### WB-B2 `wbB2_validate_coversTitleAmountCreatorAndPayerBranches`
- **What it does:** Makes each early check in `validate` true once, while all earlier ones are false:
  - the title is null, then blank;
  - the amount is 0, then -1;
  - the creator isn't a member;
  - the payer is null, then empty, then not a member.

  Each must fail with the right field and message, and nothing is saved. Then all the checks are false for David, who is a member but neither the payer nor a participant, and his expense must save.
- **Result:** passes.
- **Bugs found:** none.

### WB-B3 `wbB3_validate_coversParticipantAndShareBranches`
- **What it does:**
  - **Participant checks:** shares are null; only one participant; a participant who isn't a member; the payer not among the participants.
  - **Each condition in the share loop** (`share == null || share < 0 || share > amount - total`), triggered one at a time:
    - a null share;
    - a negative share, placed first so that `share < 0` decides;
    - two shares of $20.00 on a $30.00 expense, so the second is larger than what is left.
  - **After the loop:** shares that add up to $29.99.
  - **Valid boundaries:** a $0.00 share is allowed, and a 1-cent expense split as 1 / 0 saves.
- **Result:** passes.
- **Bugs found:** none. While measuring coverage, the first version gave the negative share second, after a $31.00 share, so the "too much" check fired first and `share < 0` was never true. Reordering the shares fixed the test.

### WB-B4 `wbB4_editAndDelete_coverEveryPermissionAndLockBranch`
- **What it does:** Goes through every branch of `loadEditable`, using both `updateExpense` and `deleteExpense`:
  - reading the saved expense fails;
  - no transaction has that ID;
  - the ID belongs to a settlement, not an expense;
  - the editor is null, then not the creator;
  - the lock check fails;
  - the lock check answers true, then null (anything other than false must count as locked).

  None of these may update or delete anything. Finally, with the lock check answering false, the creator deletes the expense. The object deleted must be the saved version loaded from the database, not the possibly outdated copy the screen passed in.
- **Result:** passes.
- **Bugs found:** none.

### WB-B5 `wbB5_updateExpense_revalidatesAndKeepsOriginalDateOrderAndCreator`
- **What it does:** Goes through the branches of `updateExpense` after the permission checks pass:
  - The group has been deleted.
  - An edit with an amount of 0 is rejected by `validate`, so edits are validated just like new expenses.
  - A valid edit whose object has a different date (2030-01-01) and sequence number (99) is saved. It must keep the saved date (2026-10-12), sequence number (3) and creator. The title, amount, category, payer and participants must come from the edit.
- **Result:** passes.
- **Bugs found:** none.

### Coverage achieved

Measured with JaCoCo (`createDebugUnitTestCoverageReport`) running only `PartBWhiteBoxTest`:

| Scope | Line coverage | Branch coverage |
|---|---|---|
| Part B methods of `ExpenseService` (`createExpense`, `updateExpense`, `deleteExpense`, `validate`, `loadEditable`, `withGroup` and their callbacks) | 100% | 48/48 (100%) |
| Whole `ExpenseService.java` file | 103/108 (95%) | 48/48 (100%) |

The 5 lines not covered are outside Part B's logic. Four belong to the two convenience constructors (`ExpenseService()` and `ExpenseService(txRepo, settlementService)`), which create real Firebase repositories and can't run in a local unit test. The fifth is `previewShares`, which belongs to Part C and is covered by Part C's tests. Part B's screen (`ExpenseFormActivity`) depends on Android views, so it is covered by the black-box Espresso tests above rather than by JUnit.

To reproduce the numbers, turn on `enableUnitTestCoverage = true` for the debug build type. A Gradle init script can do this without changing `build.gradle.kts`:

```groovy
allprojects {
    plugins.withId("com.android.application") {
        android.buildTypes.debug.enableUnitTestCoverage = true
    }
}
```

Then run `./gradlew -I coverage.init.gradle testDebugUnitTest --tests "com.team19.splitsmart.service.PartBWhiteBoxTest" createDebugUnitTestCoverageReport` and open `app/build/reports/coverage/test/debug/index.html`. In Android Studio, **Run 'PartBWhiteBoxTest' with Coverage** shows the same result.
