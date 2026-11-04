package com.team19.splitsmart.ui.transaction;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.service.ExpenseService;
import com.team19.splitsmart.service.GroupService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.util.Callback;

import java.util.ArrayList;
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
    private ExpenseSplitSection splitSection;
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
        groupService = new GroupService();
        authService = new AuthService();
        splitSection = new ExpenseSplitSection(this);
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        transactionId = getIntent().getStringExtra(Extras.TRANSACTION_ID);
        findViewById(R.id.buttonSave).setOnClickListener(v -> onSaveClicked());
        loadMembers();
    }

    private void loadMembers() {
        groupService.getGroup(groupId, new Callback<Group>() {
            @Override
            public void onSuccess(Group group) {
                if (group == null) {
                    return;
                }
                groupService.getMembers(group, new Callback<Map<String, User>>() {
                    @Override
                    public void onSuccess(Map<String, User> members) {
                        splitSection.setMembers(members);
                        // TODO (Part B): fill the payer and participant pickers from members.
                        // Placeholder so Part C can test: $30.00, current user pays, all members
                        // take part. Replace with the real values whenever the fields change.
                        splitSection.setParticipants(3000, authService.getCurrentUserId(),
                                new ArrayList<>(group.getMemberIds()));
                    }

                    @Override
                    public void onFailure(Exception e) {
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
            }
        });
    }

    private void onSaveClicked() {
        // TODO (Part B): build the Expense (shares from splitSection.getShares(), method from
        // splitSection.getMethod(), splitInputs from splitSection.getInputs()), then
        // createExpense(...) or updateExpense(...), finish() on success.
        finish();
    }
}
