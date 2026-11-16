# Part C testing (splits and monthly summary)

Author of every test below: **Dawi Yoon** (dawiyoon@usc.edu). Covers Feature 3 (expense splitting, FR-2.4 and FR-2.5) and the monthly summary of Feature 5 (FR-5.2).

| Type | Tool | File |
|---|---|---|
| White-box | JUnit 4 (local unit tests) | `app/src/test/java/com/team19/splitsmart/service/PartCWhiteBoxTest.java` |
| Black-box | Espresso (instrumented tests) | `app/src/androidTest/java/com/team19/splitsmart/PartCBlackBoxTest.java` |

Extra Part C unit tests written during implementation (not counted in the 5 + 5): `app/src/test/java/com/team19/splitsmart/PartCSplitAndSummaryTest.java`, 17 tests.

## How to run

**White-box** (no emulator needed), from the project root:

```sh
./gradlew testDebugUnitTest --tests "com.team19.splitsmart.service.PartCWhiteBoxTest" --no-watch-fs
```

Or in Android Studio: open `PartCWhiteBoxTest`, then click the green arrow next to the class name and choose **Run**. The report is written to `app/build/reports/tests/testDebugUnitTest/index.html`.

**Black-box** (needs an Android emulator and the Firebase emulators, see `tests/README.md`). Start the Firebase emulators in one terminal:

```sh
firebase emulators:start --only auth,firestore --project demo-splitsmart --config tests/firebase/firebase.json
```

Then, with the Android emulator running:

```sh
./gradlew connectedDebugAndroidTest --no-watch-fs -Pandroid.testInstrumentationRunnerArguments.class=com.team19.splitsmart.PartCBlackBoxTest
```

The tests create their own users and a fresh group, and they delete the group afterwards. They never touch the shared database; the test fails right away if Firestore isn't pointed at the local emulator. Screenshots are saved on the emulator under `/sdcard/Android/data/com.team19.splitsmart/files/test-evidence/` (`bb-c1-equal-split.png` and so on).

## Black-box tests

Designed from the requirements only, using equivalence classes (valid and invalid inputs) and boundary values. Each test drives the real expense form or monthly summary screen with Espresso, then checks what the user sees and what was saved.

### BB-C1 `bbC1_equalSplitOfTenDollarsThreeWays_showsExtraCentAndSaves`
- **What it does:** As Alice, opens Add expense, enters "Pizza" for $10.00, unchecks David, and leaves the default Equal split. Checks that the preview shows $3.34, $3.33, $3.33, that Bob's row says he owes Alice, and that the status says the shares add up to $10.00. Saves, then checks that the stored expense is an Equal split with 3 shares totalling exactly 1000 cents.
- **Rationale:** $10.00 / 3 is the customer's own example of an amount that can't be divided evenly. It's the boundary where rounding can lose or gain a cent. It also checks that Equal is the default (UC-2 step 2).
- **Bugs found:** none.

### BB-C2 `bbC2_exactAmountsNotAddingUp_blockSave_thenCorrectedAmountsSave`
- **What it does:** $30.00 split by exact amount as 5 / 10 / 10. Checks that the status shows "Shares add up to $25.00 but the expense is $30.00" and "$5.00 left to assign", that pressing Save shows the split error, and that nothing is saved. Then corrects the amounts to 5 / 10 / 15 and checks that the expense saves with shares of 500 / 1000 / 1500 cents.
- **Rationale:** One input from the invalid class (sum below the total) and one from the valid class (sum equal to the total), matching UC-2 alternate path 5.3. The 5 / 10 / 15 values come from the README's checklist.
- **Bugs found:** none.

### BB-C3 `bbC3_percentagesMustTotal100_andZeroPercentIsAllowed`
- **What it does:** $30.00 split by percentage as 20 / 30 / 40 %. Checks for "Percentages add up to 90%" and "10% left to assign", and that Save is refused. Then enters 0 / 50 / 50 % and checks the previews of $0.00 and $15.00, and that the saved shares are 0 / 1500 / 1500.
- **Rationale:** 90% falls in the invalid class (not 100), per UC-2 path 5.4. 0% is the lower boundary of a valid percentage, which FR-2.4.3 explicitly allows.
- **Bugs found:** none.

### BB-C4 `bbC4_blankExactAmount_showsMessageInsteadOfCrashing`
- **What it does:** $12.00 split by exact amount, with Carol's box left empty (6 / 6 / blank). Checks that the status says "Enter an amount for every participant", that Save shows the split error, that the form stays open, and that nothing is saved.
- **Rationale:** A blank field is the most common partly finished input. FR-2.4.2 says every participant must be assigned an amount.
- **Bug found:**
  - **What it was:** in `ExpenseSplitSection.recompute()`, the expression `method == EQUAL ? 0.0 : parseInput(uid)` mixed a primitive `0.0` with a `Double`. Java therefore unboxed the `null` that `parseInput` returns for a blank box, and the app crashed with a `NullPointerException` as soon as a box was empty.
  - **Status:** fixed in commit `e0da7d2` by writing `Double.valueOf(0.0)`, which keeps both sides boxed.
  - **Test:** this test is the regression test for that fix.

### BB-C5 `bbC5_monthlySummary_countsOnlyThisMonthsExpenses_andUpdatesLive`
- **What it does:**
  - Creates Food $30.00 and Transportation $20.00 this month, Housing $100.00 last month, and a $5.00 settlement this month.
  - Opens Monthly summary and checks that it starts on the current month with "2 expenses" and "Total spending: $50.00", that the Food and Transportation rows are shown, and that all 5 categories are listed.
  - With the screen still open, adds a $12.50 expense and checks that it updates by itself to 3 expenses and $62.50.
  - Switches to last month and checks that it shows 1 expense and $100.00.
- **Rationale:** Covers each rule of FR-5.2: settlements are excluded, an expense belongs to the month of its creation date, and the category totals add up to the total. It also covers the "updates immediately" requirement, without a refresh. An expense in a different month and a settlement in the same month are the two inputs most likely to be counted by mistake.
- **Bugs found:** none.

## White-box tests

Written from the source code. **Coverage criterion: branch coverage** (every true and false outcome of every `if`, loop condition, `||` operand and `switch` case), measured with JaCoCo on the five Part C logic classes.

### WB-C1 `wbC1_equalSplit_coversGuardsAndExtraCentBranch` (`EqualSplit`)
- **What it does:** Calls the method with null inputs, empty inputs and a negative total, which must each throw. Then $10.01 split four ways (only the first person takes the `i < extra` branch, giving 251 / 250 / 250 / 250), $9.00 split three ways (`extra == 0`), a single participant, and a $0.00 total.
- **Result:** passes. `EqualSplit` lines 15/15 (100%), branches 10/10 (100%).

### WB-C2 `wbC2_exactSplit_coversEveryRejectBranchAndRounding` (`ExactAmountSplit`)
- **What it does:** Hits every reject branch: null or empty inputs, a value that is null, NaN or infinite (one per operand of the `||`), a negative value, and a sum below and above the total (with exact error messages). Then succeeds on $0.29 + $0.71 + $0.00, where `Math.round` must fix the double error (0.29 × 100 = 28.999…).
- **Result:** passes. Lines 19/19 (100%), branches 16/16 (100%).

### WB-C3 `wbC3_percentageSplit_coversGuardsLeftoverLoopAndToleranceRegression` (`PercentageSplit`)
- **What it does:**
  - Every guard: null or empty inputs, a negative total, a null / NaN / infinite / negative percent, and a sum off 100 (message "99.9%").
  - The leftover-cent loop running 0 times (25 / 75 % of $40.00) and twice (thirds of $1.00), with a 0% participant skipped by the `percent > 0` check.
  - A regression case on a large total.
- **Result:** passes. Lines 37/37 (100%), branches 25/26 (96%). The one missed branch is the `!canTakeExtra.isEmpty()` loop condition being false while cents are still left over. That can't happen: leftover cents only come from rounding down positive percentages, so whenever cents are left over, someone above 0% exists. The branch is infeasible.
- **Bug found:**
  - **What it was:** the sum check accepts percentages within 0.0001 of 100, but each share was divided by exactly 100. For percentages a bit above 100, the floored shares could add up to more than the expense. With 50.00005% + 50.00004% of $10,000,000.00, the old code handed out $9.00 too much, breaking FR-2.5.
  - **Status:** fixed in commit `e0da7d2` by dividing by the actual percentage sum.
  - **Test:** the last assertion of this test fails on the old code and passes on the fixed code (checked).

### WB-C4 `wbC4_monthlySummary_coversEverySkipBranchAndCategoryFallback` (`MonthlySummaryCalculator`)
- **What it does:**
  - A null list and a null month.
  - Inputs for every skip branch: a settlement, an expense with no date, and an expense from another month.
  - An expense with a null category, which must be counted as Other.
  - The month-prefix boundary: "2026-1" must match neither "2026-10" nor "2026-11".
  - Checks that all 5 categories are always present and that the category totals add up to the total.
- **Result:** passes. Lines 18/18 (100%), branches 16/16 (100%).

### WB-C5 `wbC5_factory_coversEverySwitchCase_andSharesAlwaysAddUp` (`SplitStrategyFactory`)
- **What it does:** Checks that each `switch` case returns the right strategy class. Then, for 271 totals from $0.01 to $99.91, checks that Equal, Percentage and Exact shares always add up to the total (FR-2.5).
- **Result:** passes. Lines 5/5 (100%), branches 3/3 (100%).

### Coverage achieved

Measured with JaCoCo (`createDebugUnitTestCoverageReport`) over all unit tests:

| Class | Line coverage | Branch coverage |
|---|---|---|
| `EqualSplit` | 15/15 (100%) | 10/10 (100%) |
| `ExactAmountSplit` | 19/19 (100%) | 16/16 (100%) |
| `PercentageSplit` | 37/37 (100%) | 25/26 (96%), the 1 missed branch is infeasible |
| `SplitStrategyFactory` | 5/5 (100%) | 3/3 (100%) |
| `MonthlySummaryCalculator` | 18/18 (100%) | 16/16 (100%) |
| **Part C logic total** | **94/94 (100%)** | **70/71 (98.6%)** |

The Part C screens (`ExpenseSplitSection`, `MonthlySummaryActivity`) depend on Android views, so they are covered by the black-box Espresso tests above rather than by JUnit.

To reproduce the numbers: turn on `enableUnitTestCoverage = true` for the debug build type, which a Gradle init script can do without changing `build.gradle.kts`. Then run `./gradlew testDebugUnitTest createDebugUnitTestCoverageReport` and open `app/build/reports/coverage/test/debug/index.html`. In Android Studio, **Run 'PartCWhiteBoxTest' with Coverage** shows the same result.
