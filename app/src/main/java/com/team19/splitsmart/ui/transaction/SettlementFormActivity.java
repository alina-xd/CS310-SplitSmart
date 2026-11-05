package com.team19.splitsmart.ui.transaction;

import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.*;
import com.team19.splitsmart.service.*;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.MoneyUtil;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** Records a payment by either party; editing preserves the original parties and date (UC-4). */
public class SettlementFormActivity extends AppCompatActivity {
    private final SettlementService settlementService = new SettlementService();
    private final BalanceService balanceService = new BalanceService();
    private final GroupService groupService = new GroupService();
    private final HistoryService historyService = new HistoryService();
    private final Map<String, User> members = new LinkedHashMap<>();
    private final Map<String, Long> balances = new LinkedHashMap<>();
    private final List<String> memberIds = new ArrayList<>();
    private final List<Long> limits = new ArrayList<>();
    private String groupId, transactionId, userId;
    private Settlement original;
    private RadioGroup direction;
    private Spinner member;
    private EditText amount, note;
    private TextView status, maximum;
    private Button save;
    private boolean ready, saving, filled;
    private int generation;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settlement_form);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
        transactionId = getIntent().getStringExtra(Extras.TRANSACTION_ID);
        userId = new AuthService().getCurrentUserId();
        direction = findViewById(R.id.settlementDirection);
        member = findViewById(R.id.spinnerSettlementMember);
        amount = findViewById(R.id.inputSettlementAmount);
        note = findViewById(R.id.inputSettlementNote);
        status = findViewById(R.id.textSettlementStatus);
        maximum = findViewById(R.id.textSettlementMaximum);
        save = findViewById(R.id.buttonSave);
        filled = state != null && state.getBoolean("filled");
        direction.setOnCheckedChangeListener(
                (group, checked) -> {
                    if (ready) rebuildMembers();
                });
        member.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        showMaximum();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        showMaximum();
                    }
                });
        save.setOnClickListener(v -> onSaveClicked());
        findViewById(R.id.buttonSettlementRetry).setOnClickListener(v -> load());
    }

    @Override
    protected void onStart() {
        super.onStart();
        load();
    }

    @Override
    protected void onStop() {
        generation++;
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("filled", filled);
        super.onSaveInstanceState(state);
    }

    private void load() {
        final int request = ++generation;
        ready = false;
        setSaving(false);
        status.setText(R.string.d_loading);
        status.setVisibility(View.VISIBLE);
        findViewById(R.id.buttonSettlementRetry).setVisibility(View.GONE);
        if (groupId == null || userId == null) {
            fail(new IllegalStateException("Select a group and sign in first"));
            return;
        }
        groupService.getGroup(
                groupId,
                receive(
                        request,
                        group -> {
                            if (group == null || !group.hasMember(userId)) {
                                fail(new IllegalStateException("This group is unavailable"));
                                return;
                            }
                            groupService.getMembers(
                                    group,
                                    receive(
                                            request,
                                            users -> {
                                                members.clear();
                                                members.putAll(users);
                                                if (transactionId == null) loadBalances(request);
                                                else
                                                    historyService.getTransaction(
                                                            groupId,
                                                            transactionId,
                                                            receive(
                                                                    request,
                                                                    transaction -> {
                                                                        if (!(transaction
                                                                                instanceof
                                                                                Settlement)) {
                                                                            fail(
                                                                                    new IllegalStateException(
                                                                                            "This settlement"
                                                                                                + " no longer"
                                                                                                + " exists"));
                                                                            return;
                                                                        }
                                                                        original =
                                                                                (Settlement)
                                                                                        transaction;
                                                                        if (!userId.equals(
                                                                                original
                                                                                        .getCreatorId())) {
                                                                            fail(
                                                                                    new ValidationException(
                                                                                            "permission",
                                                                                            "Only the"
                                                                                                + " creator"
                                                                                                + " can edit"
                                                                                                + " this"
                                                                                                + " settlement"));
                                                                            return;
                                                                        }
                                                                        ((TextView)
                                                                                        findViewById(
                                                                                                R.id
                                                                                                        .textSettlementHeading))
                                                                                .setText(
                                                                                        R.string
                                                                                                .d_edit);
                                                                        TextView date =
                                                                                findViewById(
                                                                                        R.id
                                                                                                .textSettlementDate);
                                                                        date.setText(
                                                                                getString(
                                                                                        R.string
                                                                                                .d_date,
                                                                                        original
                                                                                                .getCreatedDate()));
                                                                        date.setVisibility(
                                                                                View.VISIBLE);
                                                                        direction.check(
                                                                                userId.equals(
                                                                                                original
                                                                                                        .getPayerId())
                                                                                        ? R.id
                                                                                                .radioIPaid
                                                                                        : R.id
                                                                                                .radioPaidMe);
                                                                        if (!filled) {
                                                                            amount.setText(
                                                                                    BigDecimal
                                                                                            .valueOf(
                                                                                                    original
                                                                                                            .getAmountCents(),
                                                                                                    2)
                                                                                            .toPlainString());
                                                                            note.setText(
                                                                                    original
                                                                                            .getNote());
                                                                            filled = true;
                                                                        }
                                                                        loadBalances(request);
                                                                    }));
                                            }));
                        }));
    }

    private void loadBalances(int request) {
        balanceService.getSummary(
                groupId,
                userId,
                receive(
                        request,
                        summary -> {
                            balances.clear();
                            for (PairBalance pair : summary.getPairBalances())
                                balances.put(pair.getOtherUserId(), pair.getAmountCents());
                            if (original != null) {
                                String other =
                                        userId.equals(original.getPayerId())
                                                ? original.getRecipientId()
                                                : original.getPayerId();
                                long oldEffect =
                                        userId.equals(original.getPayerId())
                                                ? original.getAmountCents()
                                                : -original.getAmountCents();
                                balances.put(
                                        other,
                                        Math.subtractExact(
                                                balances.getOrDefault(other, 0L), oldEffect));
                            }
                            ready = true;
                            status.setVisibility(View.GONE);
                            rebuildMembers();
                            setSaving(false);
                        }));
    }

    private void rebuildMembers() {
        String previous = selectedMember();
        memberIds.clear();
        limits.clear();
        List<String> labels = new ArrayList<>();
        boolean userPaid = direction.getCheckedRadioButtonId() == R.id.radioIPaid;
        String editOther =
                original == null
                        ? null
                        : userPaid ? original.getRecipientId() : original.getPayerId();
        for (Map.Entry<String, User> entry : members.entrySet()) {
            String uid = entry.getKey();
            if (userId.equals(uid)) continue;
            long pair = balances.getOrDefault(uid, 0L);
            long limit = Math.max(0, userPaid ? -pair : pair);
            if (original == null ? limit <= 0 : !uid.equals(editOther)) continue;
            memberIds.add(uid);
            limits.add(limit);
            labels.add(
                    getString(
                            R.string.d_member_amount,
                            entry.getValue().getName(),
                            MoneyUtil.format(limit)));
        }
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        member.setAdapter(adapter);
        int position = memberIds.indexOf(previous);
        if (position >= 0) member.setSelection(position);
        if (memberIds.isEmpty()) {
            status.setText(R.string.d_no_debts);
            status.setVisibility(View.VISIBLE);
        } else if (original != null) {
            status.setText(R.string.d_edit_hint);
            status.setVisibility(View.VISIBLE);
        } else status.setVisibility(View.GONE);
        showMaximum();
        setSaving(saving);
    }

    private String selectedMember() {
        int position = member.getSelectedItemPosition();
        return position >= 0 && position < memberIds.size() ? memberIds.get(position) : null;
    }

    private void showMaximum() {
        int position = member.getSelectedItemPosition();
        long limit = position >= 0 && position < limits.size() ? limits.get(position) : 0L;
        maximum.setText(getString(R.string.d_maximum, MoneyUtil.format(limit)));
    }

    private void onSaveClicked() {
        if (!ready || saving || selectedMember() == null) return;
        amount.setError(null);
        final long cents;
        try {
            cents = MoneyUtil.parseToCents(amount.getText().toString());
        } catch (NumberFormatException | ArithmeticException e) {
            amount.setError(getString(R.string.d_bad_amount));
            return;
        }
        Settlement settlement = new Settlement();
        settlement.setGroupId(groupId);
        settlement.setCreatorId(userId);
        boolean userPaid = direction.getCheckedRadioButtonId() == R.id.radioIPaid;
        settlement.setPayerId(userPaid ? userId : selectedMember());
        settlement.setRecipientId(userPaid ? selectedMember() : userId);
        settlement.setAmountCents(cents);
        settlement.setNote(note.getText().toString().trim());
        if (original != null) settlement.setId(original.getId());
        setSaving(true);
        final int request = generation;
        Callback<Void> callback =
                new Callback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        if (!isDestroyed()) finish();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (request != generation) return;
                        setSaving(false);
                        if (e instanceof ValidationException
                                && "amount".equals(((ValidationException) e).getField())) {
                            amount.setError(e.getMessage());
                            amount.requestFocus();
                        } else {
                            status.setText(e.getMessage());
                            status.setVisibility(View.VISIBLE);
                        }
                    }
                };
        if (original == null) settlementService.createSettlement(settlement, callback);
        else settlementService.updateSettlement(settlement, userId, callback);
    }

    private void setSaving(boolean value) {
        saving = value;
        save.setText(value ? R.string.d_saving : R.string.d_save);
        save.setEnabled(ready && !value && !memberIds.isEmpty());
        member.setEnabled(ready && !value && original == null);
        for (int i = 0; i < direction.getChildCount(); i++)
            direction.getChildAt(i).setEnabled(ready && !value && original == null);
        amount.setEnabled(ready && !value);
        note.setEnabled(ready && !value);
    }

    private void fail(Exception e) {
        ready = false;
        setSaving(false);
        status.setText(e.getMessage());
        status.setVisibility(View.VISIBLE);
        findViewById(R.id.buttonSettlementRetry).setVisibility(View.VISIBLE);
    }

    private <T> Callback<T> receive(int request, Consumer<T> success) {
        return new Callback<T>() {
            @Override
            public void onSuccess(T result) {
                if (request == generation) success.accept(result);
            }

            @Override
            public void onFailure(Exception e) {
                if (request == generation) fail(e);
            }
        };
    }
}
