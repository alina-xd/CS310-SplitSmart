# Integrated validation: Parts A-E

Validated on 4 November 2026 on an Apple Silicon Mac with an ARM64 Android 16 / API 36 emulator. The latest full run checks application code from commit `ec69c63`, including Parts A-E, final repository and interface fixes, and removal of developer tools. No temporary source stubs were used.

## Results

| Check | Result |
| --- | --- |
| JVM unit tests | 81 tests; 0 failures, 0 errors |
| Android instrumentation | 8 tests; 0 failures, 0 errors, 0 skipped |
| Debug APK | Built successfully |
| Release APK | Built successfully, unsigned |
| Android lint | Completed; 0 errors, 74 warnings across the project |
| Whitespace / patch check | `git diff --check` passed |

Unit tests include 31 balance/settlement cases, five integration regression cases, and the existing split, summary, and history suites. One unit test and one instrumentation test are the original Android template smoke checks. The seven other instrumentation scenarios exercise the real Firebase SDK against local Auth and Firestore emulators.

## Coverage

- Accounts/groups: invalid email and USC IDs, duplicate email and USC ID, case-insensitive email login, wrong password, profile name update, owner-only member addition and deletion, duplicate/missing members, group rename and membership visibility.
- Expenses/splits: creation by a member who is not a participant, equal/exact/percentage splits, date/sequence preservation, creator-only changes, negative-share rejection, zero-amount rejection, service deletion, and group deletion cascading to transactions.
- Balances: the original development fixture for all four users, separate receive/pay totals, cancellation within a pair, no third-person transfer, group isolation, zero shares, overflow reporting, listener-driven UI updates after creating/deleting a settlement, and screen recreation.
- Settlements: payer/recipient recording permissions, positive amount, maximum debt, repayment direction, edit allowance after undoing the saved record, immutable parties/date/sequence/creator, noncreator denial, deletion, and missing records. UI tests enter zero, negative, sub-cent, excessive, and valid amounts; create and edit a payment; cancel deletion; then confirm deletion.
- Locks: later sequence rather than displayed date, either direction, positive shares, unrelated participant pairs, multiple covering settlements, and automatic unlocking after deletion. Integration tests confirm ExpenseService rejects modifying or deleting a locked expense and the detail screen hides its edit action.
- History/monthly summary: case-sensitive title/note searches, transaction type/category/member filters, sort direction, monthly expense totals excluding settlements, a server-source Firestore read, monthly screen rendering, and a visible group-history row after creating an expense through the form.
- Phone UI: balance screen, settlement form, transaction details, expense form with all three split previews, group history, and monthly summary. A swipe-based test verifies the expense Save button remains reachable after changing split modes.

## Integration fixes

1. The group overview and history filters scroll with transaction rows. Owner controls no longer squeeze the history list into the bottom of the screen.
2. Expense validation now rejects negative shares, shares exceeding the total, creators outside the group, and using the create API to overwrite a saved ID.
3. Percentage sums accepted by the existing tolerance are normalized before rounding. Large totals still conserve every cent, and 0% shares remain zero.
4. BalanceSummary copies and protects its result list. Balance arithmetic rejects overflow rather than wrapping around.
5. Settlement editing removes the original signed effect before computing its allowance; this remains correct after a later expense reverses the pair's debt.

## Reproduction and limits

See `tests/README.md` for the exact local commands and report locations. The test runner redirects Firebase only during instrumentation; ordinary app launches use the existing Firebase project. Test rules are not deployed, and the team's cloud records were not reset.

This run covers one Android API level and local Firebase emulators, not two physical devices or the deployed Firebase security rules. Developer tools have been removed. The shared Firestore test-mode rules, simultaneous multi-client writes, and release signing remain separate release concerns. Lint warnings are not presented as a clean lint report.

The assignment calls for a demo video, a project ZIP, and an implementation PDF. `docs/implementation.md` supplies the editable report with revised diagrams, and `output/pdf/SplitSmart_Project_2.3_Implementation_Team19.pdf` is the prepared implementation document. The project ZIP should contain the complete version-controlled project, including the Gradle wrapper, source, resources, Firebase client configuration, and tests. Local build caches and machine-specific settings are not needed. The demonstration video is a separate deliverable.
