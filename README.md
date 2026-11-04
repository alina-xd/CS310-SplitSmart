# SplitSmart — Team 19

CSCI 310 Fall 2026, Project A. Android (Java), Firebase Authentication + Cloud Firestore.

Team: Chenyang Wang, Alina Du, Dawi Yoon, William Wang, Yang Chen.

## Getting started

1. Clone the repo and open the folder in Android Studio.
2. `app/google-services.json` is already committed (it connects the app to the Firebase project `splitsmartgroup19`).
3. **File → Sync Project with Gradle Files**, then run on an emulator (API 24+).
4. The app opens on a placeholder Log in screen. Every button navigates; nothing is saved yet.

## What is already done (skeleton)

- Package renamed to `com.team19.splitsmart` (it must match `google-services.json`).
- Firebase BoM, Auth and Firestore dependencies added.
- All 11 screens exist as placeholder Activities with their own layout, registered in the manifest, and linked by buttons.
- Model classes, repositories (Firestore reads and writes), and service classes with the method signatures from our 2.2 design.
- Service methods are **stubs** that throw `UnsupportedOperationException("Not implemented yet (Feature N)")`. Implementing them is your feature's work. Screens never call a stub yet, so the app runs.

## Who owns what (by feature, matching the demo checklist)

| Feature | Service code (implement the stubs) | Screens (replace the placeholders) |
|---|---|---|
| 1. Accounts & groups | `AuthService`, `GroupService` | `ui/account/*`, `GroupListActivity`, `GroupFormActivity`, members part of `GroupActivity` |
| 2. Expenses | `ExpenseService` (create / update / delete / validate) | `ExpenseFormActivity` fields + save, expense part of `TransactionDetailActivity` |
| 3. Split methods | `service/split/*`, `ExpenseService.previewShares` | split method + preview part of `ExpenseFormActivity` |
| 4. Settlements & balances | `SettlementService`, `BalanceService` | `SettlementFormActivity`, `BalanceActivity`, settlement part of `TransactionDetailActivity` |
| 5. History & summary | `HistoryService`, `HistoryFilter.matches`, `Transaction.matchesKeyword` | history list in `GroupActivity`, `MonthlySummaryActivity` |

Shared screens (`GroupActivity`, `ExpenseFormActivity`, `TransactionDetailActivity`) have comments marking whose part is whose. Put new UI pieces (adapters, dialogs) in **new files** to avoid merge conflicts.

Repositories (`data/repository`) are shared and already work. If you need a new query, add a new method rather than changing an existing one, and tell the team.

## Firestore layout

```
users/{uid}                          User: uid, name, uscEmail (lower case), uscId
groups/{groupId}                     Group: groupId, name, description, ownerId, memberIds[], seqCounter
groups/{groupId}/transactions/{id}   Expense or Settlement, told apart by the "type" field
                                     ("EXPENSE" / "SETTLEMENT")
```

The database is in **test mode**, which expires about 30 days after it was created. Before then, someone must update the security rules in the Firebase console (Firestore → Rules), or every read and write will start failing.

## Conventions

- **Money is `long` cents.** Never use `double` for amounts. Use `MoneyUtil.parseToCents("12.34")` for input and `MoneyUtil.format(1234)` for display.
- **Dates are `"yyyy-MM-dd"` strings.** Use `DateUtil.today()` and `DateUtil.monthOf(date)` (gives `"yyyy-MM"`).
- **Async results use `Callback<T>`** (`onSuccess` / `onFailure`). Firebase calls return on the main thread, so you can update views directly in the callback.
- **Validation errors**: throw or pass a `ValidationException(field, message)`; screens show the message next to that field.
- **Ordering**: every transaction gets a per-group `sequenceNo` from `TransactionRepository.nextSequenceNo` (higher = later). Locking and history order use it.
- **Balance sign**: in `PairBalance`, positive = the other member owes the current user; negative = the current user owes them.
- **Locking** is computed, not stored: an expense is locked for a participant when there is a later settlement (higher `sequenceNo`) between that participant and the payer.
- **Automatic updates**: `GroupActivity` already listens to the group's transactions (`HistoryService.listen`) and removes the listener in `onStop`. Reuse the same pattern for other screens that must refresh.
- Pass IDs between screens with the keys in `ui/Extras` (`GROUP_ID`, `TRANSACTION_ID`).

## Git workflow

- `main` must always build and run.
- One branch per feature: `feature-1-accounts`, `feature-2-expenses`, `feature-3-splits`, `feature-4-settlements`, `feature-5-history`.
- Pull `main` often (`git pull origin main` into your branch), open a pull request when a checklist item works, and have one teammate try it before merging.
- Do not commit `local.properties`, `build/` or `.idea/` changes (already in `.gitignore`).

## Design changes so far (for the implementation document)

Record every change from the 2.2 design here as you make it; the 2.3 report needs them.

1. **Async service methods.** Firebase is asynchronous, so service methods take a `Callback<T>` instead of returning values directly.
2. **Package name** is `com.team19.splitsmart`, grouped into `ui`, `service`, `service/split`, `data/model`, `data/repository`, `util`.
3. **Transactions stored as a subcollection** of their group, with a `type` field, so one query and one listener cover a group's whole history.
4. **Per-group sequence counter** (`seqCounter` on the group document, incremented in a Firestore transaction) gives the ordering used for locking and history.
5. **Money stored as integer cents** to avoid rounding errors.
6. **Deleting a group** also deletes its transactions (Firestore does not delete subcollections automatically).
7. **`ValidationException`** carries the name of the invalid field so screens can show the error in the right place.
8. **`EdgeToEdgeUtil` and `Extras`** added as small UI helper classes.
