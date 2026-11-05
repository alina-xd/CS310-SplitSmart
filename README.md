# SplitSmart (Team 19)

CSCI 310 Fall 2026, Project A. Android app in Java, using Firebase Authentication and Cloud Firestore.

Team: Chenyang Wang, Alina Du, Dawi Yoon, William Wang, Yang Chen.

This README is everything you need to start your part. Read sections 1–5 once (about 10 minutes), then go to your part in section 6. When every part's "Done when" list passes, the app covers every item in the 2.3 demo checklist.

---

## 1. What the app does

SplitSmart lets USC students share expenses in groups and see who owes whom.

- **Accounts.** Register with name, `@usc.edu` email, 10-digit USC ID and password. Log in with email and password. Only the name can be changed later.
- **Groups.** The creator is the owner. Only the owner can add members (by USC email), rename the group or delete it. Deleting a group also deletes its expenses and settlements. A user can be in many groups, and every member can see the group and its member list.
- **Expenses.** Title, amount (USD, more than 0, at most 2 decimal places), one category (Food, Housing, Transportation, Entertainment, Other; default Other), exactly one payer, at least 2 participants (the payer is always one of them), a split method, an optional note, and a creation date filled in automatically as `YYYY-MM-DD`. Any member can record an expense, even one they're not part of. Only the creator can edit or delete it, and editing keeps the original date.
- **Splits.** Equal, exact amount, or percentage. Shares are whole cents and always add up to the full amount (for example $10.00 / 3 = $3.34 + $3.33 + $3.33). Every participant except the payer owes the payer their share.
- **Balances.** Per group, the user sees each member they have a nonzero balance with, plus two separate totals: what they'll receive and what they owe. Debts between the same two people add up and cancel out. Debts are never moved to a third person (if Alice owes Bob and Bob owes Carol, Alice does not owe Carol).
- **Settlements.** A recorded payment between two members. Either the person who paid or the person who received can record it. The amount must be more than 0 and no more than what is currently owed. Only the creator can edit or delete it.
- **Settled-expense policy (we chose locking).** An expense is **locked** once a later settlement exists between its payer and one of its participants. Locked expenses can't be edited or deleted. Deleting that settlement unlocks them again.
- **History.** All expenses and settlements together, newest first, with search (case-sensitive), filters and sort. Tap one to see its full details.
- **Monthly summary.** For a chosen month: number of expenses, total spending and per-category totals. Settlements are not counted.
- **Updates.** The acting user's screens update by themselves after a change. Other users see it after reopening the screen.

## 2. Getting started

1. Clone the repo and open the folder in Android Studio.
2. `app/google-services.json` is committed; it connects the app to Firebase project `splitsmartgroup19`.
3. **File → Sync Project with Gradle Files**, then run on an emulator (API 24 or newer).
4. Register an account (or log in), create a group, and add a second account as a member.
5. Find your part in section 6 and start.

**Firestore rules.** The database was created in test mode, which allows all reads and writes only until about 3 December 2026. Before then, someone must open Firebase console → Firestore → Rules and change the date in `request.time < timestamp.date(...)` to `2027, 1, 15`. Only extend the date; don't require sign-in in the rules, because registration checks for duplicate emails and USC IDs before the user is signed in.

## 3. Working without waiting for each other

**Developer tools** (test-data seeding and "Act as" users) were used during development and have been removed. Test with real accounts.

**Integration status.** Parts A-E are implemented. Settlement validation and expense locking are active. See [integrated validation](docs/integration-validation.md), [test commands](tests/README.md), and [implementation design changes](docs/implementation.md). The per-part instructions below remain the implementation checklist.

**Already implemented, use freely:** every repository method, `GroupService.getGroup`, `GroupService.getMembers`, `HistoryService.getTransaction`, `HistoryService.listen`, `HistoryService.getMonthlySummary` (calls Part C's calculator), `ExpenseService.previewShares` (calls Part C's strategies), `MoneyUtil`, `DateUtil`.

### Example data (good for the demo)

Register Alice, Bob, Carol and David, create a group "Trip" owned by Alice with all four as members, then add these **in this order**:

| # | Transaction | Shares |
|---|---|---|
| 1 | Dinner $30.00, Food, Alice paid, equal, note "Thai place" (Alice records it) | Alice $10.00, Bob $10.00, Carol $10.00 |
| 2 | Taxi $20.00, Transportation, Bob paid, exact (Bob records it) | Bob $15.00, Alice $5.00 |
| 3 | Movie $10.00, Entertainment, Carol paid, equal, note "IMAX" (Carol records it) | Carol $3.34, Alice $3.33, Bob $3.33 (the payer gets the extra cent) |
| 4 | Settlement Bob → Alice $3.00, note "Venmo" (Bob records "I paid someone") | |

The correct results:

- **Alice:** Bob owes her $2.00, Carol owes her $6.67. To receive $8.67, to pay $0.00.
- **Bob:** owes Alice $2.00, owes Carol $3.33. To pay $5.33.
- **Carol:** owes Alice $6.67, Bob owes her $3.33. To receive $3.33, to pay $6.67. **David:** no balances.
- **Locks:** Dinner and Taxi are locked by settlement 4 (between Bob and Alice). Movie is not locked.
- **Monthly summary:** all four are dated today. To show a second month, change Dinner's and Taxi's `createdDate` to last month in the Firebase console; that month then shows 2 expenses, $50.00 (Food $30.00, Transportation $20.00), and the settlement is never counted.
- **Group boundaries:** a second group where Bob pays $1,500.00 rent split equally with Alice and Carol must not change Trip balances.

## 4. How the code is organized

Screens call services, services call repositories, and repositories talk to Firestore. Screens never touch Firestore directly.

```
app/src/main/java/com/team19/splitsmart/
  ui/account/       LoginActivity, RegisterActivity, ProfileActivity
  ui/group/         GroupListActivity, GroupFormActivity,
                    GroupActivity (shell) + GroupMembersSection + GroupHistorySection
  ui/transaction/   ExpenseFormActivity + ExpenseSplitSection,
                    SettlementFormActivity, TransactionDetailActivity
  ui/summary/       BalanceActivity, MonthlySummaryActivity
  ui/Extras         Intent keys GROUP_ID and TRANSACTION_ID
  service/          AuthService, GroupService, ExpenseService, SettlementService,
                    BalanceService, HistoryService, HistoryFilter, MonthlySummaryCalculator,
                    result classes (BalanceSummary, PairBalance, MonthlySummary), ValidationException
  service/split/    SplitStrategy + EqualSplit, ExactAmountSplit, PercentageSplit, SplitStrategyFactory
  data/model/       User, Group, Transaction (abstract), Expense, Settlement, Category, SplitMethod
  data/repository/  UserRepository, GroupRepository, TransactionRepository, TransactionListener, FirestorePaths
  util/             Callback, MoneyUtil, DateUtil
```

Each screen already exists as a placeholder with buttons that navigate. Replace the placeholder contents of the files you own.

**Screen flow:** Log in ↔ Register → Group list → (Profile, New group, Group). From the Group screen: Add expense, Record settlement, Balances, Monthly summary, Edit group, and history rows → Transaction details → Edit.

## 5. Data and conventions

### Firestore layout

```
users/{uid}                          User: uid, name, uscEmail (lower case), uscId
groups/{groupId}                     Group: groupId, name, description, ownerId, memberIds[], seqCounter
groups/{groupId}/transactions/{id}   Expense or Settlement; the "type" field is "EXPENSE" or "SETTLEMENT"
```

Common fields on every transaction: `id`, `type`, `groupId`, `creatorId`, `amountCents`, `createdDate` ("yyyy-MM-dd"), `sequenceNo`, `note`.
Expense adds `title`, `category`, `payerId`, `splitMethod`, `shares` (uid → cents, one entry per participant, payer included) and `splitInputs` (what was typed: dollars or percents).
Settlement adds `payerId` (who paid) and `recipientId` (who received).

### Rules everyone follows

- **Money is `long` cents.** Never use `double` for money. Parse input with `MoneyUtil.parseToCents("12.34")`, which throws `NumberFormatException` for bad input or more than 2 decimals. Display with `MoneyUtil.format(1234)` → "$12.34".
- **Dates:** `DateUtil.today()` → "2026-11-03" (device local date). `DateUtil.monthOf(date)` → "2026-11".
- **Emails are stored and compared in lower case.**
- **Async results** come back through `Callback<T>` (`onSuccess` / `onFailure`) on the main thread, so you can update views inside the callback.
- **Rule violations:** pass a `ValidationException(field, message)` to `onFailure`. `field` names the input (for example `"amount"`), so the screen can show the message next to it. For any other error, show `e.getMessage()` in a Toast.
- **Order:** every new transaction gets `sequenceNo` from `TransactionRepository.nextSequenceNo(groupId, ...)`, where higher means later. History order and locking use `sequenceNo`, not the date. Editing keeps the original `sequenceNo` and `createdDate`.
- **Current user:** `new AuthService().getCurrentUserId()`.
- **Member names:** `groupService.getGroup(groupId, ...)` then `groupService.getMembers(group, ...)` gives a `Map<uid, User>`.
- **Automatic updates:** `historyService.listen(groupId, listener)` calls `listener.onTransactionsChanged(list)` right away and after every change. Call it in `onStart`, and call `remove()` on the result in `onStop` (see `GroupHistorySection`).
- **Passing IDs between screens:** `intent.putExtra(Extras.GROUP_ID, ...)` and `Extras.TRANSACTION_ID`.

### Balance rules (Part D implements; others rely on them)

The balance for user U with another member X is one signed number in cents. Positive means X owes U; negative means U owes X. Go through the group's transactions:

- **Expense paid by P, with share(p) for each participant p:**
  - If U is P: for each participant p other than U, add share(p) to U's balance with p.
  - If U is a participant other than P: subtract share(U) from U's balance with P.
  - Everyone else is unaffected.
- **Settlement where A paid B some amount:**
  - If U is A: add the amount to U's balance with B (U now owes B less).
  - If U is B: subtract the amount from U's balance with A.

To receive = sum of the positive balances. To pay = sum of the absolute values of the negative balances. The two totals are never netted against each other.

### Locking rule (Part D implements; Parts B and E use it)

An expense E (payer P) is locked if some settlement S has `S.sequenceNo > E.sequenceNo`, and S is between P and some participant p ≠ P whose share is more than 0, in either direction. This is calculated every time (nothing is stored), so deleting S unlocks E automatically.

## 6. The five parts

Each file has exactly one owner. **Only edit files you own.** If you need a change in someone else's file, ask them.

**Shared files, which nobody edits without telling the whole team first:** `data/model/*`, `data/repository/*`, `util/*`, `ui/Extras`, `ui/EdgeToEdgeUtil`, `GroupActivity` + `activity_group.xml` (it only hosts the sections), `AndroidManifest.xml`, the Gradle files and `strings.xml`. Put your strings in your own file, for example `res/values/strings_part_a.xml`; Android merges them. RecyclerView is already included through the Material library.

Each part's work is split into **implement** (service code), **screens** (UI), and **done when** (checklist items you can demo).

---

### Part A: Accounts and groups (checklist Feature 1)

**You own:** `AuthService`, `GroupService`, `LoginActivity`, `RegisterActivity`, `ProfileActivity`, `GroupListActivity`, `GroupFormActivity`, `GroupMembersSection`, and the layouts `activity_login`, `activity_register`, `activity_profile`, `activity_group_list`, `activity_group_form`, `section_group_members`.

**Implement, in `AuthService`:**
- `isValidUscEmail`: has text before `@usc.edu`, case-insensitive. `isValidUscId`: exactly 10 digits.
- `register(name, email, uscId, password, cb)`, in this order:
  1. Check the inputs: blank name → `ValidationException("name", ...)`, bad email → `"email"`, bad ID → `"uscId"`, blank password → `"password"`.
  2. Lower-case the email.
  3. Check duplicates: `userRepo.findByEmail` not null → `("email", "An account with this email already exists")`; `userRepo.findByUscId` not null → `("uscId", "An account with this USC ID already exists")`.
  4. `auth.createUserWithEmailAndPassword`. Firebase requires a password of 6 or more characters; show its error on `"password"`.
  5. `userRepo.save(new User(uid, name, email, uscId))`.
- `login(email, password, cb)`: `auth.signInWithEmailAndPassword(email in lower case, password)`, then `userRepo.findById(uid)`.
- `logout()`: `auth.signOut()`. `getCurrentUser(cb)`: `userRepo.findById(getCurrentUserId())`. `updateName(newName, cb)`: reject blank, load the user, `setName`, save.
- Add `isSignedIn()` (`auth.getCurrentUser() != null`). The login screen uses it to skip straight to the group list.

**Implement, in `GroupService`:**
- `createGroup`: blank name → `ValidationException("name", ...)`, then `groupRepo.save(new Group(name, description, ownerId))`. The description may be empty.
- `updateGroup` and `deleteGroup`: load the group; if the requester isn't the owner → `ValidationException("permission", "Only the owner can ...")`. Otherwise `groupRepo.save` or `groupRepo.delete` (which already deletes the group's transactions too).
- `addMember(groupId, email, requesterId, cb)`:
  - Requester isn't the owner → error.
  - `userRepo.findByEmail(lower case)` is null → `("email", "No registered user with this email")`.
  - Already a member → `("email", "This user is already a member")`.
  - Otherwise `group.addMember(uid)` and save.
- `getGroupsFor(userId, cb)`: `groupRepo.findByMember(userId)`.

**Screens:**
- **Log in:** email and password fields and a Log in button. If `isSignedIn()`, go straight to the group list. Show a login error under the form.
- **Register:** name, email, USC ID and password fields. Show each `ValidationException` message next to its field.
- **Profile:** the name in an editable field with a Save button. Email and USC ID shown as plain text, not editable. Log out clears the back stack (already done).
- **Group list:** list the user's groups (reload in `onResume`); tapping a row calls `onGroupSelected(groupId)`. Keep the New group and Profile buttons. Delete the placeholder button.
- **Group form:** name and optional description. With `GROUP_ID`, prefill the fields and save with `updateGroup`.
- **Members section (on the group screen):** group name, description and owner, plus the member list by name. Show an email field with **Add member**, plus **Edit group** and **Delete group**, only to the owner; the service rejects non-owners anyway. After deleting, `activity.finish()`.

**Done when:**
- [ ] Registering with a non-`@usc.edu` email, a 9- or 11-digit ID, or a 10-character ID containing a letter is rejected with a message.
- [ ] An existing email with a new ID is rejected, and an existing ID with a new email is rejected.
- [ ] Log in, view and edit your name, log out. Email and USC ID can't be edited.
- [ ] Create a group; the creator is the owner; it's still there after closing and reopening the app.
- [ ] The owner adds members by email. An unknown email and an existing member each get a message. A non-owner can't add members. A user can be in two groups, and another member sees the group and its members.
- [ ] The owner renames the group; another member can't edit or delete it. Deleting a group removes its expenses and settlements (try it with the Disposable group, then check the Firebase console).

---

### Part B: Expenses (checklist Feature 2)

**You own:** `ExpenseService`, `ExpenseFormActivity`, `activity_expense_form.xml`.

**Implement, in `ExpenseService`** (add a `GroupRepository` field if you need the group's members; it's your file):
- `validate(expense)`, throwing `ValidationException` with these fields:
  - `"title"` if blank.
  - `"amount"` if 0 or less.
  - `"payer"` if there's no payer or the payer isn't a member.
  - `"participants"` if there are fewer than 2, any isn't a member, or the payer isn't among them.
  - `"split"` if `!expense.sharesAddUp()`.
  - The category is never null (the default is OTHER).
- `createExpense`: `validate`, then `txRepo.nextSequenceNo`, set `sequenceNo` and `createdDate = DateUtil.today()`, then `txRepo.save`.
- `updateExpense(expense, editorId, cb)`:
  1. Load the saved version (`txRepo.findById`).
  2. Reject if `editorId` isn't its creator.
  3. Reject if `settlementService.isLocked(saved)` is true ("This expense is locked by a later settlement").
  4. Copy `createdDate`, `sequenceNo` and `creatorId` from the saved version.
  5. `validate`, then `txRepo.update`.
- `deleteExpense(expense, editorId, cb)`: same creator and lock checks, then `txRepo.delete`. The confirmation dialog is on the detail screen (Part E).

**Screen (`ExpenseFormActivity`):**
- **Fields:**
  - Title.
  - Amount: parse with `MoneyUtil.parseToCents`; on `NumberFormatException` show "Enter a valid amount with at most 2 decimals".
  - Category spinner with all 5 values, defaulting to Other.
  - Payer spinner (group members).
  - Participant checkboxes (group members). The payer is always checked and its box disabled, so it can't be unchecked.
  - Note (optional).
- Whenever the amount, payer or participants change, call `splitSection.setParticipants(amountCents or 0, payerId, participantIds)`. Delete the $30.00 placeholder call.
- **Save:**
  1. Build an `Expense`: groupId, creatorId = current user, title, amountCents, category, payerId, note, `shares = splitSection.getShares()` (catch `ValidationException` and show it), `splitMethod = splitSection.getMethod()`, `splitInputs = splitSection.getInputs()`.
  2. Call `createExpense`, or `updateExpense` when editing. Call `finish()` on success.
  3. Show any `ValidationException` next to its field.
- **Edit mode** (`TRANSACTION_ID` given): `historyService.getTransaction(...)`, fill the fields, call `splitSection.load(expense)`, and save with `updateExpense`.

**Done when:**
- [ ] A complete expense saves with title, amount, category, one payer, participants, split method, optional note and today's date in `YYYY-MM-DD`. A member can record an expense where someone else pays and they themselves aren't a participant.
- [ ] All 5 categories are available, a new expense defaults to Other, and each expense has exactly one category.
- [ ] Amounts of 0, negative, or with 3 decimals can't be saved; a valid amount can.
- [ ] Payer and participants come only from the group, fewer than 2 participants is rejected, and the payer can't be unchecked.
- [ ] The creator edits the payer, participants and amount of an unlocked expense, and the date stays the same. Another member can view it but can't edit or delete it (with Part E's detail screen). In the example data (section 3), Movie is unlocked and Dinner is locked.
- [ ] Data is still there after reopening the app, and another member sees changes after reopening the screen.

---

### Part C: Splits and monthly summary (checklist Feature 3, plus the monthly summary item of Feature 5)

**You own:** `service/split/*`, `ExpenseSplitSection`, `section_expense_split.xml`, `MonthlySummaryCalculator`, `MonthlySummaryActivity`, `activity_monthly_summary.xml`.

**Implement:**
- **`EqualSplit`:** with n participants, each gets `total / n` cents. The first `total % n` participants (in map order) get 1 extra cent. $10.00 / 3 → 334, 333, 333.
- **`ExactAmountSplit`:** inputs are dollars. Convert each with `Math.round(value * 100)`. Negative values → `IllegalArgumentException`. If the sum isn't equal to the total → `IllegalArgumentException("Shares add up to $X but the expense is $Y")`. $0.00 shares are allowed.
- **`PercentageSplit`:** inputs are percents (0 is allowed, negatives are not). If the sum differs from 100 by more than 0.0001 → `IllegalArgumentException`. Each share is `floor(total × percent / 100)` cents; then give the leftover cents one at a time, in map order, so the shares add up exactly to the total.
- **`MonthlySummaryCalculator.compute(transactions, month)`:** only expenses whose `createdDate` starts with `month`. Return the count, the total cents and an `EnumMap` with all 5 categories (0 where there are none).

**Screens:**
- **`ExpenseSplitSection`** (inside Part B's expense form; keep the public method signatures):
  - Three radio buttons (Equal, Exact amount, Percentage) with exactly one selected; Equal is the default.
  - One row per participant: name, an input box (hidden for Equal), and the computed share.
  - Recompute the preview on every change with `expenseService.previewShares(...)`, catching `IllegalArgumentException` to show its message.
  - `getShares()` returns the shares, or throws `ValidationException("split", message)` when the inputs are invalid.
  - `getInputs()` returns the typed values (an empty map for Equal).
  - `load(expense)` restores the method and inputs from `expense.getSplitInputs()`.
  - Delete the stand-in code in `getShares()`.
- **`MonthlySummaryActivity`:**
  - A month picker (for example a spinner with every month that has expenses, plus the current month).
  - Show the expense count, the total spending and all 5 category totals.
  - Use `historyService.listen` in `onStart` / `onStop` and recompute with `MonthlySummaryCalculator.compute` on every change, so it updates by itself.

**Done when:**
- [ ] Equal, exact and percentage can each be chosen while creating or editing, with exactly one selected, and every participant's share shows before saving.
- [ ] $30.00 equal among 3 gives $10.00 each.
- [ ] $5 + $10 + $15 exact for $30.00 works, a $0.00 share works, and a mismatched total is rejected.
- [ ] 20 / 30 / 50 % of $30.00 gives $6.00, $9.00 and $15.00, a 0% share works, and a total other than 100% is rejected.
- [ ] $10.00 equal three ways gives $3.34, $3.33 and $3.33. Shares always add up to the full amount, including with percentages.
- [ ] Alice pays $30.00 equally for Alice, Bob and Carol: Bob owes Alice $10.00, Carol owes Alice $10.00, and Alice owes herself nothing (shown on Part D's balance page).
- [ ] The monthly summary for October 2026 in the Trip group shows 2 expenses, $50.00, Food $30.00, Transportation $20.00. Switching to November shows 1 expense, $10.00. Editing an expense updates the summary by itself, and the expense stays in its original month.

---

### Part D: Balances and settlements (checklist Feature 4, plus the settlement items of Feature 5)

**You own:** `BalanceService`, `SettlementService`, `BalanceSummary`, `PairBalance`, `BalanceActivity`, `SettlementFormActivity`, `activity_balance.xml`, `activity_settlement_form.xml`.

**Implement, in `BalanceService`** (follow "Balance rules" in section 5):
- `getSummary(groupId, userId, cb)`: `txRepo.findByGroup`, then go through every transaction with `applyExpense` / `applySettlement` into a uid → cents map. Return a `BalanceSummary` with the nonzero `PairBalance`s and the two separate totals.
- `getPairBalance(groupId, userId, otherId, cb)`: the same calculation, for one member.

**Implement, in `SettlementService`:**
- `getMaxAllowed(groupId, payerId, recipientId, cb)`: what the payer currently owes the recipient, which is `max(0, -pairBalance(payer, recipient))`.
- `createSettlement(s, cb)`:
  - Reject if amount ≤ 0 (`"amount"`), payer = recipient, or either one isn't a member.
  - Reject if amount > `getMaxAllowed` → `("amount", "They only owe $X")`.
  - Otherwise `nextSequenceNo`, `createdDate = DateUtil.today()`, then `txRepo.save`.
- `updateSettlement(s, editorId, cb)`: creator only. The limit is `getMaxAllowed` plus the saved settlement's own amount (if the payer and recipient are unchanged), so editing $5 to $6 against $11 owed is allowed. Keep `createdDate`, `sequenceNo` and `creatorId`.
- `deleteSettlement(s, editorId, cb)`: creator only, then `txRepo.delete`.
- `isLocked(expense, cb)`: follow "Locking rule" in section 5. Replace the `onSuccess(false)` stand-in.

**Screens:**
- **`BalanceActivity`:**
  - Show "To receive" and "To pay" totals.
  - One line per nonzero member, using names from `getMembers`: "Bob owes you $2.00" or "You owe Carol $3.33".
  - Use `historyService.listen` in `onStart` / `onStop` and call `getSummary` on every change, so it updates by itself.
- **`SettlementFormActivity`:**
  - A direction choice: "I paid someone" (current user is the payer) or "Someone paid me" (current user is the recipient).
  - A member picker listing only members with a debt in that direction (from `getSummary`), plus amount and note fields.
  - Show "Maximum: $X" and show errors next to the amount.
  - Set `creatorId` to the current user, then save and `finish()`.
  - **Edit mode** (`TRANSACTION_ID`): load with `historyService.getTransaction`, prefill, and save with `updateSettlement`.

**Done when:**
- [ ] As Alice in Trip, the balance page shows Bob owes her $2.00 and Carol owes her $6.66, to receive $8.66 and to pay $0.00. Zero balances (David) may be hidden.
- [ ] Debts add up and cancel out per pair (Alice owes Bob $10 + $5 and Bob owes Alice $4 → Alice owes Bob $11).
- [ ] Payable and receivable totals stay separate (receive $15.00 and pay $4.00 is shown as both, not netted to $11).
- [ ] Apartment's rent doesn't change Trip balances, and Alice→Bob plus Bob→Carol debts stay as they are (no Alice→Carol debt).
- [ ] Create, edit and delete a settlement; balances are right after each, and the page updates without a refresh.
- [ ] Create, edit and delete an unlocked expense; balances update by themselves. A locked expense can't be edited (Dinner).
- [ ] A settlement can be recorded by the person who paid and by the person who received. It shows payer, recipient, amount, automatic date and note. 0, negative and above-debt amounts are rejected; paying $5.00 against $11.00 owed leaves $6.00.
- [ ] The creator can edit and delete a settlement. Deleting settlement 4 unlocks Dinner and Taxi.
- [ ] You can explain the policy: "An expense locks once a later settlement exists between its payer and a participant; deleting that settlement unlocks it."

---

### Part E: History and details (rest of checklist Feature 5)

**You own:** `HistoryService`, `HistoryFilter`, `GroupHistorySection`, `section_group_history.xml`, `TransactionDetailActivity`, `activity_transaction_detail.xml`. (`getTransaction`, `listen` and `getMonthlySummary` in `HistoryService` are already done; leave them as they are.)

**Implement:**
- `HistoryFilter.matches(t)`: it must pass every filter that is set:
  - Keyword: a case-sensitive `contains` on the expense title, expense note or settlement note.
  - Type: `Transaction.TYPE_EXPENSE` or `TYPE_SETTLEMENT`.
  - Category: settlements never match when a category is set.
  - Member: in `t.getInvolvedUserIds()`.
  - Empty or null fields are ignored.
- `HistoryService.getHistory(groupId, filter, cb)`: `txRepo.findByGroup`, keep the transactions where `filter.matches` is true, and sort by `sequenceNo` (descending when `newestFirst`).

**Screens:**
- **`GroupHistorySection`:**
  - A list (RecyclerView or ListView). Each row shows the type, the expense title or "Settlement", the amount, the date, and the involved members' names. Tapping a row calls `openTransaction(id)`.
  - Above the list: a search box, a type spinner (All, Expense, Settlement), a category spinner (All + 5), a member spinner (All + members) and a sort toggle (Newest first / Oldest first).
  - Keep the latest list from `onTransactionsChanged` and re-apply the filter whenever a control changes. Sort by `sequenceNo`.
  - Delete the placeholder button.
- **`TransactionDetailActivity`:**
  - Load in `onResume` with `historyService.getTransaction`.
  - For an expense, show title, amount, category, payer, each participant with their share, split method, note, date and creator. For a settlement, show payer, recipient, amount, note, date and creator. Any group member can view either.
  - Show **Edit** and **Delete** only if the current user is the creator and, for expenses, `settlementService.isLocked` is false. If it is locked, show "Locked by a later settlement".
  - **Delete** opens an AlertDialog to confirm (required), then calls `expenseService.deleteExpense` or `settlementService.deleteSettlement`, then `finish()`.
  - **Edit** opens `ExpenseFormActivity` or `SettlementFormActivity` (depending on `t.isExpense()`) with `GROUP_ID` and `TRANSACTION_ID`.

**Done when:**
- [ ] The Trip history shows all 4 transactions, newest first, each with type, title or "Settlement", amount, `YYYY-MM-DD` date and involved users. Tapping one shows full details, and David (not in any expense) can view them.
- [ ] Deleting asks for confirmation. After creating, editing or deleting, the history updates by itself; after reopening, the data persists and another member sees it.
- [ ] Search finds expense titles, expense notes and settlement notes, and is case-sensitive ("IMAX" matches; "imax" doesn't).
- [ ] The type, category and member filters each change the list, and newest-first and oldest-first both work.

## 7. Contracts between parts

These are fixed, so parts can be written in parallel. Don't change them without agreeing with the other part.

| Between | Contract |
|---|---|
| B ↔ C | `ExpenseSplitSection` public methods: `setMembers(Map<String, User>)`, `setParticipants(long totalCents, String payerId, List<String> participantIds)`, `load(Expense)`, `getMethod()`, `getShares()` (throws `ValidationException`), `getInputs()`. Until C is done, `getShares()` gives the whole amount to the payer so B can save. Until B is done, the form passes $30.00 and all members so C can test. |
| E → B, D | The detail screen opens `ExpenseFormActivity` / `SettlementFormActivity` with `GROUP_ID` + `TRANSACTION_ID` (edit mode) and calls `ExpenseService.deleteExpense` / `SettlementService.deleteSettlement`. Both reject non-creators and locked expenses themselves. |
| B, E → D | `SettlementService.isLocked(expense, cb)`. |
| Everyone | The service method signatures in the skeleton, the Firestore layout and the rules in section 5. |

## 8. Before the demo (whole team, last few days)

1. Merge every part into `main` and run through the full checklist once, with two people switching users.
2. ~~Remove the Developer tools~~ (done).
3. Register 4 real accounts (for example Alice, Bob, Carol, David) and create in the app:
   - two groups with different owners and members, plus a disposable group to delete
   - varied expenses and settlements with different categories, members, titles, notes and dates
4. The monthly summary needs records in more than one month. Past-month records may be added beforehand; in the Firebase console, change the `createdDate` of a few expenses to last month.
5. Write down the expected splits, balances and monthly totals, and practice the run-through, including closing and reopening the app and switching users.

## 9. Git workflow

- `main` must always build and run.
- One branch per part: `part-a-accounts`, `part-b-expenses`, `part-c-splits`, `part-d-balances`, `part-e-history`.
- Pull `main` into your branch often. Open a pull request when a "Done when" item works, and have a teammate try it before merging.
- Don't commit `local.properties`, `build/` or `.idea/` (they're in `.gitignore`).

**Before you merge, every time:**
1. Run the unit tests: right-click `app/src/test/java` → **Run 'Tests in ...'**. All must pass.
2. Walk through your part's "Done when" list on the emulator, including empty boxes, wrong input and the Back button.
3. Run the random-tap crash test from Android Studio's Terminal tab, with the app installed and the emulator open. Log in with a test account first, because it taps buttons at random and may add or delete data:
   ```
   ~/Library/Android/sdk/platform-tools/adb shell monkey -p com.team19.splitsmart --throttle 300 -v 500
   ```
   It must end with "Monkey finished". If it says "CRASH", open the **Logcat** tab, filter by "FATAL EXCEPTION", and fix the line it points to.

## 10. Design changes from 2.2 (for the implementation document)

Add to this list whenever you change something from the 2.2 design.

1. **Async service methods.** Firebase is asynchronous, so service methods take a `Callback<T>` instead of returning values.
2. **Package name** is `com.team19.splitsmart`, grouped into `ui`, `service`, `service/split`, `data/model`, `data/repository` and `util`.
3. **Transactions are stored as a subcollection** of their group, with a `type` field, so one query and one listener cover a group's whole history.
4. **A per-group sequence counter** (`seqCounter` on the group document, incremented in a Firestore transaction) gives the order used for history and locking.
5. **Money is stored as integer cents** to avoid rounding errors.
6. **Deleting a group also deletes its transactions** (Firestore doesn't delete subcollections automatically).
7. **`ValidationException`** carries the name of the invalid field, so screens can show the error in the right place.
8. **`EdgeToEdgeUtil` and `Extras`** were added as small UI helper classes.
9. **Screen sections.** The group screen and the expense form are split into section classes (`GroupMembersSection`, `GroupHistorySection`, `ExpenseSplitSection`) with their own layouts.
10. **Keyword search moved** from `Transaction` into `HistoryFilter`, and monthly totals into `MonthlySummaryCalculator`, so the logic is in one place and testable without Firebase.
11. **`Expense.splitInputs`** stores what the user typed (dollars or percents), so the form can be refilled when editing.
12. **`HistoryService.getTransaction`** was added so the detail and edit screens can load one transaction.
13. **Passwords need at least 6 characters.** Firebase Authentication requires this, so FR-1.1 ("no rules on password length") can't be met exactly. Report it under "Requirements Change".
