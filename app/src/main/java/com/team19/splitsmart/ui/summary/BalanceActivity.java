package com.team19.splitsmart.ui.summary;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.service.BalanceService;
import com.team19.splitsmart.service.BalanceSummary;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;

/** C4 Balance & Summary Screens. Owner: Part D. The user's balances in one group. */
public class BalanceActivity extends AppCompatActivity {

    private BalanceService balanceService;
    private String groupId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_balance);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        balanceService = new BalanceService();
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // TODO (Part D): balanceService.getSummary(groupId, currentUserId, ...) then showBalances.
        // Reloading here (or listening for changes) keeps the page current after edits.
    }

    private void showBalances(BalanceSummary summary) {
        // TODO (Part D)
    }
}
