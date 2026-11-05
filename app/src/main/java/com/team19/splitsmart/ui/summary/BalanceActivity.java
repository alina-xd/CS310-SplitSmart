package com.team19.splitsmart.ui.summary;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;
import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.service.*;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.MoneyUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-member balances and separate payable/receivable totals for the selected group. */
public class BalanceActivity extends AppCompatActivity {
    private final BalanceService balanceService = new BalanceService();
    private final GroupService groupService = new GroupService();
    private final HistoryService historyService = new HistoryService();
    private final Map<String, User> members = new LinkedHashMap<>();
    private String groupId, userId;
    private ListenerRegistration registration;
    private int generation, balanceRequest;
    private TextView status, receive, pay;
    private LinearLayout rows;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_balance);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        userId = new AuthService().getCurrentUserId();
        status = findViewById(R.id.textBalanceStatus);
        receive = findViewById(R.id.textToReceive);
        pay = findViewById(R.id.textToPay);
        rows = findViewById(R.id.balanceRows);
        findViewById(R.id.buttonBalanceRetry).setOnClickListener(v -> load());
    }

    @Override
    protected void onStart() {
        super.onStart();
        load();
    }

    @Override
    protected void onStop() {
        generation++;
        balanceRequest++;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        super.onStop();
    }

    private void load() {
        final int request = ++generation;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        status.setText(R.string.d_loading);
        status.setVisibility(View.VISIBLE);
        findViewById(R.id.buttonBalanceRetry).setVisibility(View.GONE);
        if (groupId == null || userId == null) {
            showError("Select a group and sign in first");
            return;
        }
        groupService.getGroup(
                groupId,
                new Callback<Group>() {
                    @Override
                    public void onSuccess(Group group) {
                        if (request != generation) return;
                        if (group == null || !group.hasMember(userId)) {
                            showError("This group is unavailable");
                            return;
                        }
                        groupService.getMembers(
                                group,
                                new Callback<Map<String, User>>() {
                                    @Override
                                    public void onSuccess(Map<String, User> users) {
                                        if (request != generation) return;
                                        members.clear();
                                        members.putAll(users);
                                        refresh(request);
                                        registration =
                                                historyService.listen(
                                                        groupId,
                                                        transactions -> {
                                                            if (request == generation)
                                                                refresh(request);
                                                        });
                                    }

                                    @Override
                                    public void onFailure(Exception e) {
                                        if (request == generation) showError(e.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (request == generation) showError(e.getMessage());
                    }
                });
    }

    private void refresh(int generationAtStart) {
        final int request = ++balanceRequest;
        balanceService.getSummary(
                groupId,
                userId,
                new Callback<BalanceSummary>() {
                    @Override
                    public void onSuccess(BalanceSummary summary) {
                        if (generationAtStart != generation || request != balanceRequest) return;
                        showBalances(summary);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (generationAtStart == generation && request == balanceRequest)
                            showError(e.getMessage());
                    }
                });
    }

    private void showBalances(BalanceSummary summary) {
        receive.setText(
                getString(R.string.d_receive, MoneyUtil.format(summary.getTotalToReceiveCents())));
        pay.setText(getString(R.string.d_pay, MoneyUtil.format(summary.getTotalToPayCents())));
        status.setVisibility(View.GONE);
        findViewById(R.id.buttonBalanceRetry).setVisibility(View.GONE);
        Map<String, Long> amounts = new LinkedHashMap<>();
        for (PairBalance pair : summary.getPairBalances())
            amounts.put(pair.getOtherUserId(), pair.getAmountCents());
        rows.removeAllViews();
        for (Map.Entry<String, User> entry : members.entrySet()) {
            if (entry.getKey().equals(userId)) continue;
            long amount = amounts.getOrDefault(entry.getKey(), 0L);
            TextView row = new TextView(this);
            int pad = Math.round(12 * getResources().getDisplayMetrics().density);
            row.setPadding(0, pad, 0, pad);
            row.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            row.setText(
                    getString(
                            amount > 0
                                    ? R.string.d_they_owe
                                    : amount < 0 ? R.string.d_you_owe : R.string.d_settled,
                            entry.getValue().getName(),
                            MoneyUtil.format(Math.abs(amount))));
            rows.addView(row);
        }
        if (rows.getChildCount() == 0) {
            status.setText(R.string.d_no_members);
            status.setVisibility(View.VISIBLE);
        }
    }

    private void showError(String message) {
        status.setText(message == null ? "Could not load balances" : message);
        status.setVisibility(View.VISIBLE);
        findViewById(R.id.buttonBalanceRetry).setVisibility(View.VISIBLE);
    }
}
