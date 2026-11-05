> Integration update (2026-11-04): the earlier isolated checks below are historical. Parts A-E now build together without stubs; see [integrated validation](integration-validation.md).

# Part E: history and transaction details

Implemented against `main` commit `a2381d4`:

- Case-sensitive substring search across expense titles and both transaction notes.
- Type, expense category and involved-member filters combined with AND; empty filters ignored.
- Sequence-number sorting in both directions, shared by service reads and live snapshots.
- A ListView with type/title, whole-cent amount, date and involved member names. The search/filter header scrolls with the list so it remains accessible inside the shared group shell on small displays.
- Lifecycle-managed history listener, current-filter reapplication after changes, loading/empty/error states, retry and guards against stale asynchronous responses.
- Full expense and settlement details, reloaded in `onResume`; group membership checked independently of transaction participation.
- Creator-only edit/delete actions, expense lock checks through Part D, type-specific edit intents, and confirmation before deletion through Part B/D services. A failed lock check keeps actions hidden.

No shared files or other parts' implementations were changed. `HistoryService.getTransaction`, `listen` and `getMonthlySummary` remain unchanged.

## Automated verification

27 new JUnit tests passed: 22 in `HistoryFilterTest` and 5 in `HistoryServiceTest`. They cover both transaction types, all categories, title/note case sensitivity, literal whitespace, nullable fields, intersecting filters, zero-share participants, uninvolved creators, both sort orders, same-day ordering, long sequence values, source-list immutability and asynchronous repository errors.

The intended integrated command is:

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug --no-watch-fs
```

The current repository snapshot cannot complete that command because it references files absent from `main`:

- Part A: `GroupMembersSection`, `section_group_members.xml`.
- Part C: `ExpenseSplitSection`, `section_expense_split.xml`, `MonthlySummaryCalculator`.
- Shared development tools: `dev.DevSession`, `dev.DevActivity`.

To isolate Part E, local verification supplied temporary stand-ins for only those missing classes/layouts through an external Gradle init script. With those stand-ins, all 27 Part E tests (plus the existing sample test), Java compilation, Android resource linking and `lintDebug` passed. None of those stand-ins are committed. This is **not** a successful integrated app build or an emulator/Firebase end-to-end test.

## Integration/demo checklist

Run these after the missing skeleton files and Parts B/D are available. The README's Developer tools are currently absent from this checkout.

1. Seed the README's Trip fixture. Expect Settlement, Movie, Taxi, Dinner in newest-first order; reverse with Oldest first, including the two records on November 2. Check row names, amounts and dates, then tap each row.
2. Search `Dinner`, `Thai`, `IMAX`, and `Venmo`; `imax` must not match `IMAX`. Search an absent term and expect `No matching transactions.` Clear search to restore results.
3. Select each type, each category and each member. Food + Settlement must produce no matches. David must have no matches, but as a group member he must still be able to open all records with the member filter cleared.
4. Check expense title/amount/category/payer, every participant/share, split method, note/date/creator. Check settlement payer/recipient, amount/note/date/creator.
5. As the creator, check edit navigation includes both IDs and opens the appropriate form. Returning from an edit must reload details. Non-creators must not see Edit/Delete.
6. Once Part D implements locking, Dinner and Taxi must show `Locked by a later settlement` and hide actions; Movie must remain editable by its creator. Deleting settlement 4 must unlock the covered expenses on reopening.
7. Cancel deletion and confirm the record remains. Confirm deletion and verify the service succeeds, detail closes and history updates. A failed deletion must display the service error and allow retry/reload.
8. Create/edit/delete through Parts B/D, then reopen the group and switch users to verify persistence, names and live history updates. Exercise connection errors and retries.

Part D currently returns `false` unconditionally from `isLocked`; Part B/D delete methods currently return `Not implemented yet`. Part E calls those contracts without replacing their business logic. Consequently real locking and successful edit/delete/persistence demos remain integration checks.
