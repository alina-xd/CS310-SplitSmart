package com.team19.splitsmart.ui.transaction;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.service.BalanceService;
import com.team19.splitsmart.service.SettlementService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;

/** C3 Transaction Screens. Owner: Feature 4. Records a settlement, or edits one when TRANSACTION_ID is passed. */
public class SettlementFormActivity extends AppCompatActivity {

    private SettlementService settlementService;
    private BalanceService balanceService;
    private String groupId;
    private String transactionId; // null when recording a new settlement

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settlement_form);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        settlementService = new SettlementService();
        balanceService = new BalanceService();
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        transactionId = getIntent().getStringExtra(Extras.TRANSACTION_ID);
        findViewById(R.id.buttonSave).setOnClickListener(v -> onSaveClicked());
    }

    private void onDirectionChanged(boolean userPaid) {
        // TODO (Feature 4): list members with a balance in the chosen direction.
    }

    private void onSaveClicked() {
        // TODO (Feature 4): createSettlement(...) or updateSettlement(...), then finish() on success.
        finish();
    }
}
