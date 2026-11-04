package com.team19.splitsmart.ui.group;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.ui.summary.BalanceActivity;
import com.team19.splitsmart.ui.summary.MonthlySummaryActivity;
import com.team19.splitsmart.ui.transaction.ExpenseFormActivity;
import com.team19.splitsmart.ui.transaction.SettlementFormActivity;

/**
 * C2 Group Screens. Shared shell, nobody needs to edit it: it only holds the navigation buttons
 * and hosts two sections. Members section: Part A (GroupMembersSection). History section:
 * Part E (GroupHistorySection).
 */
public class GroupActivity extends AppCompatActivity {

    private String groupId;
    private GroupMembersSection membersSection;
    private GroupHistorySection historySection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_group);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        membersSection = new GroupMembersSection(this, groupId);
        historySection = new GroupHistorySection(this, groupId);
        findViewById(R.id.buttonAddExpense).setOnClickListener(v -> open(ExpenseFormActivity.class));
        findViewById(R.id.buttonRecordSettlement).setOnClickListener(v -> open(SettlementFormActivity.class));
        findViewById(R.id.buttonBalances).setOnClickListener(v -> open(BalanceActivity.class));
        findViewById(R.id.buttonMonthlySummary).setOnClickListener(v -> open(MonthlySummaryActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        membersSection.refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        historySection.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        historySection.stop();
    }

    private void open(Class<?> screen) {
        Intent intent = new Intent(this, screen);
        intent.putExtra(Extras.GROUP_ID, groupId);
        startActivity(intent);
    }
}
