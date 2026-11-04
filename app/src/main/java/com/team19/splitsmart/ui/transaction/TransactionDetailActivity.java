package com.team19.splitsmart.ui.transaction;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.service.ExpenseService;
import com.team19.splitsmart.service.SettlementService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;

/** C3 Transaction Screens. Owners: Feature 2 (expense details) and Feature 4 (settlement details).
 * Shows one transaction; Edit and Delete only for its creator and only if allowed. */
public class TransactionDetailActivity extends AppCompatActivity {

    private ExpenseService expenseService;
    private SettlementService settlementService;
    private Transaction transaction;
    private String groupId;
    private String transactionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transaction_detail);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        expenseService = new ExpenseService();
        settlementService = new SettlementService();
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        transactionId = getIntent().getStringExtra(Extras.TRANSACTION_ID);
        findViewById(R.id.buttonEdit).setOnClickListener(v -> onEditClicked());
        findViewById(R.id.buttonDelete).setOnClickListener(v -> onDeleteClicked());
    }

    private void onEditClicked() {
        // TODO (Features 2 and 4): open SettlementFormActivity instead when the transaction is a settlement.
        Intent intent = new Intent(this, ExpenseFormActivity.class);
        intent.putExtra(Extras.GROUP_ID, groupId);
        intent.putExtra(Extras.TRANSACTION_ID, transactionId);
        startActivity(intent);
    }

    private void onDeleteClicked() {
        // TODO (Features 2 and 4): ask for confirmation first (required), then delete.
        Toast.makeText(this, "Not implemented yet", Toast.LENGTH_SHORT).show();
    }
}
