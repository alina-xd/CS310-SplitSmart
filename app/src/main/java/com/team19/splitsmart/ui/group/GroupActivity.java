package com.team19.splitsmart.ui.group;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;
import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.repository.TransactionListener;
import com.team19.splitsmart.service.GroupService;
import com.team19.splitsmart.service.HistoryFilter;
import com.team19.splitsmart.service.HistoryService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.ui.summary.BalanceActivity;
import com.team19.splitsmart.ui.summary.MonthlySummaryActivity;
import com.team19.splitsmart.ui.transaction.ExpenseFormActivity;
import com.team19.splitsmart.ui.transaction.SettlementFormActivity;
import com.team19.splitsmart.ui.transaction.TransactionDetailActivity;

import java.util.List;

/** C2 Group Screens. Owners: Feature 1 (members, add member, edit/delete group) and Feature 5
 * (history list, search, filter, sort). Put the history list in its own adapter class to avoid conflicts. */
public class GroupActivity extends AppCompatActivity implements TransactionListener {

    private GroupService groupService;
    private HistoryService historyService;
    private Group group;
    private HistoryFilter filter = new HistoryFilter();
    private String groupId;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_group);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        groupService = new GroupService();
        historyService = new HistoryService();
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        findViewById(R.id.buttonAddExpense).setOnClickListener(v -> open(ExpenseFormActivity.class));
        findViewById(R.id.buttonRecordSettlement).setOnClickListener(v -> open(SettlementFormActivity.class));
        findViewById(R.id.buttonBalances).setOnClickListener(v -> open(BalanceActivity.class));
        findViewById(R.id.buttonMonthlySummary).setOnClickListener(v -> open(MonthlySummaryActivity.class));
        findViewById(R.id.buttonEditGroup).setOnClickListener(v -> open(GroupFormActivity.class));
        findViewById(R.id.buttonAddMember).setOnClickListener(v -> onAddMemberClicked(""));
        findViewById(R.id.buttonDeleteGroup).setOnClickListener(v -> onDeleteGroupClicked());
        findViewById(R.id.buttonOpenTransactionPlaceholder).setOnClickListener(v -> openTransaction("placeholder-transaction"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Redraws the history whenever this group's transactions change (NFR-2).
        registration = historyService.listen(groupId, this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    @Override
    public void onTransactionsChanged(List<Transaction> transactions) {
        // TODO (Feature 5): apply the filter and redraw the history list.
    }

    private void onAddMemberClicked(String email) {
        // TODO (Feature 1): owner only; groupService.addMember(...).
        Toast.makeText(this, "Not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private void onDeleteGroupClicked() {
        // TODO (Feature 1): owner only; groupService.deleteGroup(...), then finish().
        Toast.makeText(this, "Not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private void applyFilter(HistoryFilter filter) {
        // TODO (Feature 5)
    }

    private void open(Class<?> screen) {
        Intent intent = new Intent(this, screen);
        intent.putExtra(Extras.GROUP_ID, groupId);
        startActivity(intent);
    }

    private void openTransaction(String transactionId) {
        Intent intent = new Intent(this, TransactionDetailActivity.class);
        intent.putExtra(Extras.GROUP_ID, groupId);
        intent.putExtra(Extras.TRANSACTION_ID, transactionId);
        startActivity(intent);
    }
}
