package com.team19.splitsmart.ui.transaction;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.service.ExpenseService;
import com.team19.splitsmart.service.GroupService;
import com.team19.splitsmart.service.HistoryService;
import com.team19.splitsmart.service.ValidationException;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.MoneyUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C3 Transaction Screens. Owner: Part B (this file and activity_expense_form.xml).
 * The split method and preview live in ExpenseSplitSection (Part C).
 * Creates an expense, or edits one when TRANSACTION_ID is passed.
 */
public class ExpenseFormActivity extends AppCompatActivity {

    private ExpenseService expenseService;
    private GroupService groupService;
    private AuthService authService;
    private HistoryService historyService;
    private ExpenseSplitSection splitSection;
    private Expense expense;
    private String groupId;
    private String transactionId; // null when creating a new expense

    private EditText titleInput;
    private EditText amountInput;
    private EditText noteInput;
    private Spinner categorySpinner;
    private Spinner payerSpinner;
    private LinearLayout participantsLayout;
    private TextView payerError;
    private TextView participantsError;
    private TextView splitError;
    private TextView formError;
    private View saveButton;

    /** Group members in group order, and one checkbox per member (same keys). */
    private final List<String> memberIds = new ArrayList<>();
    private final Map<String, CheckBox> participantBoxes = new LinkedHashMap<>();
    private String payerId;
    /** True while the form is being filled in code, so listeners don't push partial updates. */
    private boolean filling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_expense_form);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        expenseService = new ExpenseService();
        groupService = new GroupService();
        authService = new AuthService();
        historyService = new HistoryService();
        splitSection = new ExpenseSplitSection(this);
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        transactionId = getIntent().getStringExtra(Extras.TRANSACTION_ID);

        titleInput = findViewById(R.id.editExpenseTitle);
        amountInput = findViewById(R.id.editExpenseAmount);
        noteInput = findViewById(R.id.editExpenseNote);
        categorySpinner = findViewById(R.id.spinnerExpenseCategory);
        payerSpinner = findViewById(R.id.spinnerExpensePayer);
        participantsLayout = findViewById(R.id.layoutParticipants);
        payerError = findViewById(R.id.textPayerError);
        participantsError = findViewById(R.id.textParticipantsError);
        splitError = findViewById(R.id.textSplitError);
        formError = findViewById(R.id.textFormError);
        saveButton = findViewById(R.id.buttonSave);

        if (transactionId != null) {
            ((TextView) findViewById(R.id.textExpenseHeading)).setText(R.string.b_edit_expense);
        }
        setUpCategorySpinner();
        amountInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSplit();
            }
        });
        // Disabled until the members (and, when editing, the expense) are loaded.
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(v -> onSaveClicked());
        loadMembers();
    }

    private void setUpCategorySpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.b_categories));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);
        categorySpinner.setSelection(Category.OTHER.ordinal()); // FR-2.2: new expenses default to Other
    }

    private void loadMembers() {
        groupService.getGroup(groupId, new Callback<Group>() {
            @Override
            public void onSuccess(Group group) {
                if (group == null) {
                    showErrorAndFinish(getString(R.string.b_group_missing));
                    return;
                }
                groupService.getMembers(group, new Callback<Map<String, User>>() {
                    @Override
                    public void onSuccess(Map<String, User> members) {
                        splitSection.setMembers(members);
                        setUpMemberPickers(group, members);
                        if (transactionId == null) {
                            updateSplit();
                            saveButton.setEnabled(true);
                        } else {
                            loadExpense();
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        showErrorAndFinish(e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                showErrorAndFinish(e.getMessage());
            }
        });
    }

    /** Payer spinner and participant checkboxes list only this group's members. */
    private void setUpMemberPickers(Group group, Map<String, User> members) {
        memberIds.clear();
        if (group.getMemberIds() != null) {
            memberIds.addAll(group.getMemberIds());
        }
        List<String> names = new ArrayList<>();
        for (String uid : memberIds) {
            names.add(memberName(members, uid));
        }

        filling = true;
        participantsLayout.removeAllViews();
        participantBoxes.clear();
        for (int i = 0; i < memberIds.size(); i++) {
            String uid = memberIds.get(i);
            CheckBox box = new CheckBox(this);
            box.setText(names.get(i));
            box.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
            box.setChecked(true); // a new expense starts with every member taking part
            box.setOnCheckedChangeListener((button, checked) -> updateSplit());
            participantsLayout.addView(box);
            participantBoxes.put(uid, box);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        payerSpinner.setAdapter(adapter);
        String currentUserId = authService.getCurrentUserId();
        int payerIndex = memberIds.indexOf(currentUserId);
        selectPayer(memberIds.isEmpty() ? null : memberIds.get(Math.max(payerIndex, 0)));
        payerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = memberIds.get(position);
                if (!selected.equals(payerId)) {
                    selectPayer(selected);
                    updateSplit();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        filling = false;
    }

    /** The payer is always a participant: their box is checked and can't be unchecked. */
    private void selectPayer(String newPayerId) {
        boolean wasFilling = filling;
        filling = true;
        payerId = newPayerId;
        int index = memberIds.indexOf(newPayerId);
        if (index >= 0 && payerSpinner.getSelectedItemPosition() != index) {
            payerSpinner.setSelection(index);
        }
        for (Map.Entry<String, CheckBox> entry : participantBoxes.entrySet()) {
            boolean isPayer = entry.getKey().equals(newPayerId);
            if (isPayer) {
                entry.getValue().setChecked(true);
            }
            entry.getValue().setEnabled(!isPayer);
        }
        filling = wasFilling;
    }

    private void loadExpense() {
        historyService.getTransaction(groupId, transactionId, new Callback<Transaction>() {
            @Override
            public void onSuccess(Transaction transaction) {
                if (!(transaction instanceof Expense)) {
                    showErrorAndFinish(getString(R.string.b_expense_missing));
                    return;
                }
                expense = (Expense) transaction;
                fillForm(expense);
                saveButton.setEnabled(true);
            }

            @Override
            public void onFailure(Exception e) {
                showErrorAndFinish(e.getMessage());
            }
        });
    }

    private void fillForm(Expense saved) {
        filling = true;
        titleInput.setText(saved.getTitle());
        amountInput.setText(BigDecimal.valueOf(saved.getAmountCents(), 2).toPlainString());
        Category category = saved.getCategory() == null ? Category.OTHER : saved.getCategory();
        categorySpinner.setSelection(category.ordinal());
        noteInput.setText(saved.getNote());
        Map<String, Long> shares = saved.getShares();
        for (Map.Entry<String, CheckBox> entry : participantBoxes.entrySet()) {
            entry.getValue().setChecked(shares != null && shares.containsKey(entry.getKey()));
        }
        selectPayer(saved.getPayerId());
        filling = false;
        updateSplit();
        splitSection.load(saved);
    }

    /** Called whenever the amount, payer or participants change. */
    private void updateSplit() {
        if (filling) {
            return;
        }
        long amountCents = 0;
        try {
            amountCents = Math.max(0, MoneyUtil.parseToCents(amountInput.getText().toString()));
        } catch (NumberFormatException | ArithmeticException e) {
            // Invalid or unfinished input previews as $0.00; the error is shown on save.
        }
        splitSection.setParticipants(amountCents, payerId, selectedParticipantIds());
    }

    private List<String> selectedParticipantIds() {
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, CheckBox> entry : participantBoxes.entrySet()) {
            if (entry.getValue().isChecked()) {
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    private void onSaveClicked() {
        clearErrors();
        long amountCents;
        try {
            amountCents = MoneyUtil.parseToCents(amountInput.getText().toString());
        } catch (NumberFormatException | ArithmeticException e) {
            showFieldError(new ValidationException("amount", getString(R.string.b_invalid_amount)));
            return;
        }

        Expense toSave = new Expense();
        toSave.setId(transactionId);
        toSave.setGroupId(groupId);
        toSave.setCreatorId(authService.getCurrentUserId());
        toSave.setTitle(titleInput.getText().toString().trim());
        toSave.setAmountCents(amountCents);
        toSave.setCategory(Category.values()[categorySpinner.getSelectedItemPosition()]);
        toSave.setPayerId(payerId);
        String note = noteInput.getText().toString().trim();
        toSave.setNote(note.isEmpty() ? null : note);
        try {
            toSave.setShares(splitSection.getShares());
        } catch (ValidationException e) {
            showFieldError(e);
            return;
        }
        toSave.setSplitMethod(splitSection.getMethod());
        toSave.setSplitInputs(splitSection.getInputs());

        saveButton.setEnabled(false);
        Callback<Void> callback = new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                finish();
            }

            @Override
            public void onFailure(Exception e) {
                saveButton.setEnabled(true);
                if (e instanceof ValidationException) {
                    showFieldError((ValidationException) e);
                } else {
                    Toast.makeText(ExpenseFormActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        };
        if (transactionId == null) {
            expenseService.createExpense(toSave, callback);
        } else {
            expenseService.updateExpense(toSave, authService.getCurrentUserId(), callback);
        }
    }

    /** Shows a ValidationException next to the input named by its field. */
    private void showFieldError(ValidationException e) {
        String field = e.getField() == null ? "" : e.getField();
        switch (field) {
            case "title":
                titleInput.setError(e.getMessage());
                titleInput.requestFocus();
                break;
            case "amount":
                amountInput.setError(e.getMessage());
                amountInput.requestFocus();
                break;
            case "payer":
                showText(payerError, e.getMessage());
                break;
            case "participants":
                showText(participantsError, e.getMessage());
                break;
            case "split":
                showText(splitError, e.getMessage());
                break;
            default:
                showText(formError, e.getMessage());
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                break;
        }
    }

    private void clearErrors() {
        titleInput.setError(null);
        amountInput.setError(null);
        payerError.setVisibility(View.GONE);
        participantsError.setVisibility(View.GONE);
        splitError.setVisibility(View.GONE);
        formError.setVisibility(View.GONE);
    }

    private static void showText(TextView view, String message) {
        view.setText(message);
        view.setVisibility(View.VISIBLE);
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private static String memberName(Map<String, User> members, String uid) {
        User user = members.get(uid);
        return user != null && user.getName() != null && !user.getName().isEmpty() ? user.getName() : uid;
    }
}
