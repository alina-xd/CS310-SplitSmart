package com.team19.splitsmart.ui.transaction;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.service.ExpenseService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;

import java.util.Map;

/** C3 Transaction Screens. Owners: Feature 2 (fields, validation, save) and Feature 3 (split
 * method and share preview). Creates an expense, or edits one when TRANSACTION_ID is passed. */
public class ExpenseFormActivity extends AppCompatActivity {

    private ExpenseService expenseService;
    private Expense expense;
    private String groupId;
    private String transactionId; // null when creating a new expense

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_expense_form);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        expenseService = new ExpenseService();
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        transactionId = getIntent().getStringExtra(Extras.TRANSACTION_ID);
        findViewById(R.id.buttonSave).setOnClickListener(v -> onSaveClicked());
    }

    private void onSplitInputChanged() {
        // TODO (Feature 3): expenseService.previewShares(...) then showPreview(shares).
    }

    private void showPreview(Map<String, Long> shares) {
        // TODO (Feature 3)
    }

    private void onSaveClicked() {
        // TODO (Feature 2): createExpense(...) or updateExpense(...), then finish() on success.
        finish();
    }
}
