# Part A testing: accounts and groups (Alina Du)

Five white-box tests (JUnit) and five black-box tests (Espresso) for Part A: registration, login, profile, groups and members. They are written to fill gaps left by the existing tests: William Wang's `UITest` already covers USC email and ID formats and register's first error message, and `FullAppIntegrationTest` covers Part A through the services but not these screen flows.

## How to run

**White-box (no emulator needed).** In Android Studio, right-click `app/src/test/java/com/team19/splitsmart/service/PartAAccountGroupTest.java` and choose **Run 'PartAAccountGroupTest'**. To see coverage, choose **More Run/Debug → Run 'PartAAccountGroupTest' with Coverage**. From a terminal:

```sh
./gradlew testDebugUnitTest --tests "com.team19.splitsmart.service.PartAAccountGroupTest"
```

**Black-box.** Start an Android emulator and the Firebase emulators exactly as described in `tests/README.md`. The tests never touch the shared cloud database. Then right-click `app/src/androidTest/java/com/team19/splitsmart/PartAAccountsGroupsTest.java` and choose **Run**, or:

```sh
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.team19.splitsmart.PartAAccountsGroupsTest
```

## White-box tests (JUnit)

File: `app/src/test/java/com/team19/splitsmart/service/PartAAccountGroupTest.java`

The repositories are replaced by in-memory fakes that record every lookup, so each test can check which branch ran and that the code stopped where it should. `AuthService` gets no Firebase sign-in object. If a rejected registration ever went past its checks it would crash, so these tests also prove that a rejected registration never tries to create an account.

| # | Test | What it does | Result |
|---|---|---|---|
| WB-A1 | `register_duplicateEmail_isCheckedFirstIgnoringCaseAndSpaces` | Registers `"  ALICE@USC.edu "` while `alice@usc.edu` exists. Expects the email field to be rejected with "An account with this USC email or USC ID already exists." The only lookup is `email:alice@usc.edu`, which shows the email was trimmed and lower-cased and the USC ID check never ran. | Pass |
| WB-A2 | `register_duplicateUscIdOrLookupFailure_stopsBeforeCreatingAccount` | With a free email and a taken USC ID typed with spaces, expects a rejection on the USC ID field after exactly two lookups. Then makes the email lookup fail and expects that same error to be passed back unchanged, exactly once. | Pass |
| WB-A3 | `createGroup_rejectsBlankNameOrMissingOwner_andMakesOwnerTheOnlyMember` | A blank name is rejected ("Enter a group name"), and a missing owner gives "Not signed in"; neither saves anything. `"  Roommates  "` with no description is saved as "Roommates" with description `""`, the creator as owner and only member. It is then read back with `getGroup` and `getMembers`. | Pass |
| WB-A4 | `addMember_coversEveryBranchInOrder` | Runs every path through `addMember` in order: blank email, missing group, database failure, non-owner (stopped before any email lookup), unregistered email, a successful add with `"  BOB@usc.edu "` (lower-cased lookup), and adding the same member again. Only the successful add saves. | Pass |
| WB-A5 | `updateAndDeleteGroup_areOwnerOnly_andStaleEditKeepsNewerMembers` | Takes a copy of a group, then Carol is added. Renaming from the old copy is rejected for a non-owner and accepted for the owner, and Carol is still a member afterwards (only name and description are copied). Also checks a blank rename, the case-insensitive sorted group list, the signed-out list, non-owner delete (rejected), and owner delete. | Pass |

**Checking the tests catch real bugs.** Each of these deliberate bugs, put into the code one at a time, made at least one test fail:

| Deliberate bug | Caught by |
|---|---|
| Email not lower-cased before the duplicate check | WB-A1 |
| USC ID checked before the email | WB-A1, WB-A2 |
| Rename saves the whole old copy | WB-A5 |
| Owner check removed | WB-A4, WB-A5 |
| Adding an existing member allowed | WB-A4 |

### Coverage

The criterion is **branch coverage**: every decision must be exercised both true and false.

- **`GroupService`: 100% of branches.** The 11 decisions are createGroup's blank name and missing owner; updateGroup's blank name; the shared lookup's missing group and non-owner; addMember's blank email, unknown user and existing member; getGroupsFor's missing user; `trim`'s null input; and a repository failure. Every line runs except the no-argument constructor, which needs a live Firebase connection and is used by the black-box tests.
- **`AuthService.register` up to the Firebase call: every branch.** This combines these tests with `UITest`. William's tests cover each invalid field; WB-A1 and WB-A2 cover both duplicate checks and a lookup failure.
- **Not reachable without a live Firebase connection:** creating the account, `login`, `logout` and `updateName`. The black-box tests below cover these.

Exact numbers come from Android Studio's **Run with Coverage**.

## Black-box tests (Espresso)

File: `app/src/androidTest/java/com/team19/splitsmart/PartAAccountsGroupsTest.java`

Each test drives the real screens and checks only what a user would see: error text on a field, on-screen text, which screen is showing, and whether a control is visible. Firebase calls are asynchronous, so checks wait up to 20 seconds. Each run uses new random accounts, and groups are deleted afterwards.

| # | Test | What it does | Rationale |
|---|---|---|---|
| BB-A1 | `register_rejectsWrongEmailAndIdFormats_keepsInput_createsNoAccount` | On Register, enters `format.tester@gmail.com` and USC IDs `123456789`, `12345678901` and `123456789a`, tapping Create account each time. Each attempt must show "Use your @usc.edu email" or "USC ID must be exactly 10 digits". The typed values must stay, and nobody may end up signed in. The old error is cleared before each retry, so every check proves the error appeared again. | Equivalence classes for invalid input: a non-USC domain, plus the three invalid ID classes the checklist names. 9 and 11 digits are the boundary values either side of 10. |
| BB-A2 | `register_rejectsDuplicateEmailThenDuplicateUscId` | Registers with an existing account's email typed in capitals and a new ID, then with a new email and the existing ID. Each must show "An account with this USC email or USC ID already exists." on the right field, and stay on Register. | The checklist's two duplicate cases. Capitals check that emails are compared ignoring case. |
| BB-A3 | `register_viewProfile_logOut_andLogBackIn` | Registers a new account through the screen, which must land on an empty group list. Profile must show the name in an editable field, but "USC email: …" and "USC ID: …" as plain text, not editable fields. Logs out to the Log in screen. A wrong password must show "Incorrect email or password". The right password, with the email in capitals, must open the group list as the same user. | Covers register, log in, view, and log out end to end, plus the rule that email and USC ID can't be edited. Wrong-then-right password is the minimal pair for the login check. |
| BB-A4 | `owner_addsMembersWithMessages_memberSeesGroupsButNoOwnerControls` | As owner on the group screen: an unregistered email must show "No SplitSmart account uses this email". The member's email in capitals must add them to the list. Adding them again must show "This user is already in the group". As the member: the group list must show both of their groups, the group page must list "… (owner)" and "… (you)", and the owner controls must be hidden. | Checklist items for adding and viewing members: both error messages, case-insensitive email, a non-owner can't add, a user can be in several groups, and another member can view. |
| BB-A5 | `owner_renamesGroup_cancelsThenConfirmsDelete_removingItsRecords` | A group with one expense and one settlement is renamed through Edit group, and the new name must appear. Delete group → Cancel must keep the group. Delete group → Delete must remove the group and both of its records, checked in the database afterwards. | Checklist items for group permissions and deletion: rename as owner, deletion confirmation, and removal of the group's expenses and settlements. Cancel-then-confirm checks both dialog outcomes. |

## Bugs found while testing Part A

These were found while testing Part A against the requirements (UC-1) and the demo checklist, and are already fixed in the code.

1. **Register flagged only the first invalid field.** UC-1 4.1.2 requires an error next to each invalid field. Fixed: `RegisterActivity.checkAllFields` now marks every invalid field at once and keeps what was typed (covered by BB-A1).
2. **Error wording differed from UC-1.** Changed to "An account with this USC email or USC ID already exists.", "No SplitSmart account uses this email" and "This user is already in the group" (covered by WB-A1, WB-A2, WB-A4, BB-A2, BB-A4).
3. **Creating a group returned to the list instead of opening the group** (UC-1 step 7). Fixed: `GroupFormActivity` now opens the new group's page.
4. **Double-tapping Save created two groups, and double-tapping Add showed "already in the group" right after a success.** Fixed: both buttons are disabled while their request is running.
5. **Firebase requires passwords of 6+ characters, while FR-1.1 has no password rules.** This can't be changed, so it is recorded as a requirements change and the message is shown on the password field.
