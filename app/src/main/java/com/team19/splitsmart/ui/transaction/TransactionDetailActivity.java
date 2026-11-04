package com.team19.splitsmart.ui.transaction;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.service.ExpenseService;
import com.team19.splitsmart.service.GroupService;
import com.team19.splitsmart.service.HistoryService;
import com.team19.splitsmart.service.SettlementService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.MoneyUtil;

import java.util.HashMap;
import java.util.Map;

/** Part E: complete transaction details with creator-only actions and expense locking. */
public class TransactionDetailActivity extends AppCompatActivity {
    private final Map<String, User> members = new HashMap<>();
    private ExpenseService expenseService;
    private SettlementService settlementService;
    private HistoryService historyService;
    private GroupService groupService;
    private AuthService authService;
    private Transaction transaction;
    private String groupId;
    private String transactionId;
    private TextView details;
    private TextView status;
    private View editButton;
    private View deleteButton;
    private View retryButton;
    private boolean canModify;
    private boolean deleting;
    private int generation;
    private AlertDialog deleteDialog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transaction_detail);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        expenseService = new ExpenseService();
        settlementService = new SettlementService();
        historyService = new HistoryService();
        groupService = new GroupService();
        authService = new AuthService();
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        transactionId = getIntent().getStringExtra(Extras.TRANSACTION_ID);
        details = findViewById(R.id.textTransactionDetails);
        status = findViewById(R.id.textTransactionStatus);
        editButton = findViewById(R.id.buttonEdit);
        deleteButton = findViewById(R.id.buttonDelete);
        retryButton = findViewById(R.id.buttonDetailRetry);
        editButton.setOnClickListener(v -> onEditClicked());
        deleteButton.setOnClickListener(v -> onDeleteClicked());
        retryButton.setOnClickListener(v -> loadTransaction());
    }

    @Override protected void onResume() {
        super.onResume();
        if (!deleting) loadTransaction();
    }

    @Override protected void onPause() {
        super.onPause();
        generation++; // Ignore late reads/lock checks from the previous visible screen.
        setCanModify(false);
        if (deleteDialog != null) deleteDialog.dismiss();
    }

    private void loadTransaction() {
        int request = ++generation;
        transaction = null;
        details.setText("");
        setCanModify(false);
        retryButton.setVisibility(View.GONE);
        status.setText(R.string.e_loading_details);
        if (groupId == null || groupId.isEmpty() || transactionId == null || transactionId.isEmpty()) {
            status.setText(R.string.e_missing_transaction_ids);
            return;
        }
        groupService.getGroup(groupId, new Callback<Group>() {
            @Override public void onSuccess(Group group) {
                if (!isCurrent(request)) return;
                if (group == null) {
                    status.setText(R.string.e_group_missing);
                    return;
                }
                if (!group.hasMember(authService.getCurrentUserId())) {
                    status.setText(R.string.e_members_only);
                    return;
                }
                groupService.getMembers(group, new Callback<Map<String, User>>() {
                    @Override public void onSuccess(Map<String, User> users) {
                        if (!isCurrent(request)) return;
                        members.clear();
                        members.putAll(users);
                        loadDetails(request);
                    }
                    @Override public void onFailure(Exception e) { if (isCurrent(request)) showError(e); }
                });
            }
            @Override public void onFailure(Exception e) { if (isCurrent(request)) showError(e); }
        });
    }

    private void loadDetails(int request) {
        historyService.getTransaction(groupId, transactionId, new Callback<Transaction>() {
            @Override public void onSuccess(Transaction value) {
                if (!isCurrent(request)) return;
                transaction = value;
                if (value == null) {
                    status.setText(R.string.e_transaction_missing);
                    return;
                }
                renderDetails(value);
                if (value instanceof Expense) {
                    status.setText(R.string.e_checking_lock);
                    settlementService.isLocked((Expense) value, new Callback<Boolean>() {
                        @Override public void onSuccess(Boolean locked) {
                            if (!isCurrent(request)) return;
                            // An unknown lock state never enables destructive actions.
                            boolean unlocked = Boolean.FALSE.equals(locked);
                            status.setText(unlocked ? "" : getString(R.string.e_locked));
                            setCanModify(unlocked && isCreator());
                        }
                        @Override public void onFailure(Exception e) { if (isCurrent(request)) showError(e); }
                    });
                } else {
                    status.setText("");
                    setCanModify(isCreator());
                }
            }
            @Override public void onFailure(Exception e) { if (isCurrent(request)) showError(e); }
        });
    }

    private void renderDetails(Transaction value) {
        StringBuilder text = new StringBuilder();
        append(text, R.string.e_type, getString(value.isExpense() ? R.string.e_expense : R.string.e_settlement));
        if (value instanceof Expense) {
            Expense expense = (Expense) value;
            append(text, R.string.e_title, expense.getTitle());
            append(text, R.string.e_amount, MoneyUtil.format(expense.getAmountCents()));
            append(text, R.string.e_category, expense.getCategory() == null ? getString(R.string.e_not_available)
                    : getResources().getStringArray(R.array.e_categories)[expense.getCategory().ordinal()]);
            append(text, R.string.e_payer, memberName(expense.getPayerId()));
            text.append('\n').append(getString(R.string.e_participants)).append('\n');
            if (expense.getShares() != null) {
                for (Map.Entry<String, Long> share : expense.getShares().entrySet()) {
                    text.append(getString(R.string.e_detail_field, memberName(share.getKey()),
                            MoneyUtil.format(share.getValue() == null ? 0 : share.getValue()))).append('\n');
                }
            }
            text.append('\n');
            append(text, R.string.e_split_method, expense.getSplitMethod() == null ? getString(R.string.e_not_available)
                    : getResources().getStringArray(R.array.e_split_methods)[expense.getSplitMethod().ordinal()]);
        } else if (value instanceof Settlement) {
            Settlement settlement = (Settlement) value;
            append(text, R.string.e_payer, memberName(settlement.getPayerId()));
            append(text, R.string.e_recipient, memberName(settlement.getRecipientId()));
            append(text, R.string.e_amount, MoneyUtil.format(settlement.getAmountCents()));
        }
        append(text, R.string.e_note, value.getNote() == null || value.getNote().isEmpty()
                ? getString(R.string.e_no_note) : value.getNote());
        append(text, R.string.e_date, value.getCreatedDate());
        append(text, R.string.e_creator, memberName(value.getCreatorId()));
        details.setText(text.toString());
    }

    private void append(StringBuilder text, int label, String value) {
        text.append(getString(R.string.e_detail_field, getString(label),
                value == null ? getString(R.string.e_not_available) : value)).append('\n');
    }

    private String memberName(String uid) {
        User user = members.get(uid);
        return user != null && user.getName() != null && !user.getName().isEmpty() ? user.getName()
                : uid == null ? getString(R.string.e_unknown_member) : uid;
    }

    private boolean isCreator() {
        String uid = authService.getCurrentUserId();
        return transaction != null && uid != null && uid.equals(transaction.getCreatorId());
    }

    private boolean isCurrent(int request) { return request == generation && !isFinishing() && !isDestroyed(); }

    private void setCanModify(boolean allowed) {
        canModify = allowed;
        editButton.setVisibility(allowed ? View.VISIBLE : View.GONE);
        deleteButton.setVisibility(allowed ? View.VISIBLE : View.GONE);
        editButton.setEnabled(!deleting);
        deleteButton.setEnabled(!deleting);
    }

    private void showError(Exception e) {
        setCanModify(false);
        status.setText(getString(R.string.e_load_error, e.getMessage()));
        retryButton.setVisibility(View.VISIBLE);
        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void onEditClicked() {
        if (!canModify || !isCreator() || deleting) return;
        Intent intent = new Intent(this, transaction.isExpense() ? ExpenseFormActivity.class : SettlementFormActivity.class);
        intent.putExtra(Extras.GROUP_ID, groupId);
        intent.putExtra(Extras.TRANSACTION_ID, transactionId);
        startActivity(intent);
    }

    private void onDeleteClicked() {
        if (!canModify || !isCreator() || deleting) return;
        deleteDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.e_delete_title)
                .setMessage(R.string.e_delete_message)
                .setNegativeButton(R.string.e_cancel, null)
                .setPositiveButton(R.string.e_delete, (dialog, which) -> deleteTransaction())
                .show();
    }

    private void deleteTransaction() {
        if (!canModify || !isCreator() || deleting) return;
        deleting = true;
        setCanModify(false);
        status.setText(R.string.e_deleting);
        Callback<Void> callback = new Callback<Void>() {
            @Override public void onSuccess(Void unused) {
                deleting = false;
                if (!isDestroyed()) finish();
            }
            @Override public void onFailure(Exception e) {
                deleting = false;
                if (!isDestroyed() && !isFinishing()) showError(e);
            }
        };
        // The owning services recheck permission/locking against saved data before writing.
        if (transaction instanceof Expense) {
            expenseService.deleteExpense((Expense) transaction, authService.getCurrentUserId(), callback);
        } else if (transaction instanceof Settlement) {
            settlementService.deleteSettlement((Settlement) transaction, authService.getCurrentUserId(), callback);
        }
    }
}
