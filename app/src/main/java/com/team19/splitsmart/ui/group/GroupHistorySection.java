package com.team19.splitsmart.ui.group;

import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;
import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.data.repository.TransactionListener;
import com.team19.splitsmart.service.GroupService;
import com.team19.splitsmart.service.HistoryFilter;
import com.team19.splitsmart.service.HistoryService;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.ui.transaction.TransactionDetailActivity;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.MoneyUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Part E's history section. The group shell forwards start/stop to own the listener. */
public class GroupHistorySection implements TransactionListener {
    private final AppCompatActivity activity;
    private final String groupId;
    private final HistoryService historyService = new HistoryService();
    private final GroupService groupService = new GroupService();
    private final HistoryFilter filter = new HistoryFilter();
    private final Map<String, User> members = new HashMap<>();
    private final List<String> memberIds = new ArrayList<>();
    private List<Transaction> latest = new ArrayList<>();
    private final ArrayAdapter<Transaction> adapter;
    private final Spinner memberSpinner;
    private final TextView status;
    private final Button retry;
    private ListenerRegistration registration;
    private boolean active;
    private boolean loaded;
    private String memberError;
    private int generation;
    private int snapshotVersion;

    public GroupHistorySection(AppCompatActivity activity, String groupId) {
        this.activity = activity;
        this.groupId = groupId;
        ListView list = activity.findViewById(R.id.listHistory);
        // Filters scroll with the rows so the shared group shell still works on small screens.
        View controls = activity.getLayoutInflater().inflate(R.layout.header_group_history, list, false);
        list.addHeaderView(controls, null, false);
        status = controls.findViewById(R.id.textHistoryStatus);
        retry = controls.findViewById(R.id.buttonHistoryRetry);
        retry.setOnClickListener(v -> { stop(); start(); });
        memberSpinner = controls.findViewById(R.id.spinnerHistoryMember);
        setOptions(memberSpinner, Collections.singletonList(activity.getString(R.string.e_all_members)));
        Spinner type = controls.findViewById(R.id.spinnerHistoryType);
        Spinner category = controls.findViewById(R.id.spinnerHistoryCategory);
        setOptions(type, java.util.Arrays.asList(activity.getResources().getStringArray(R.array.e_history_types)));
        List<String> categories = new ArrayList<>();
        categories.add(activity.getString(R.string.e_all_categories));
        Collections.addAll(categories, activity.getResources().getStringArray(R.array.e_categories));
        setOptions(category, categories);

        adapter = new ArrayAdapter<Transaction>(activity, R.layout.row_history_transaction, new ArrayList<>()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView == null ? activity.getLayoutInflater()
                        .inflate(R.layout.row_history_transaction, parent, false) : convertView;
                Transaction t = getItem(position);
                String title = t instanceof Expense ? ((Expense) t).getTitle()
                        : activity.getString(R.string.e_settlement);
                ((TextView) row.findViewById(R.id.textHistoryTitle)).setText(activity.getString(
                        R.string.e_row_title, activity.getString(t.isExpense()
                                ? R.string.e_expense : R.string.e_settlement), title));
                ((TextView) row.findViewById(R.id.textHistoryAmountDate)).setText(activity.getString(
                        R.string.e_amount_date, MoneyUtil.format(t.getAmountCents()), t.getCreatedDate()));
                List<String> names = new ArrayList<>();
                for (String uid : t.getInvolvedUserIds()) names.add(memberName(uid));
                ((TextView) row.findViewById(R.id.textHistoryMembers)).setText(android.text.TextUtils.join(", ", names));
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setItemsCanFocus(true);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            if (item instanceof Transaction) openTransaction(((Transaction) item).getId());
        });
        type.setOnItemSelectedListener(selection(position -> {
            filter.setType(position == 1 ? Transaction.TYPE_EXPENSE
                    : position == 2 ? Transaction.TYPE_SETTLEMENT : null);
            render();
        }));
        category.setOnItemSelectedListener(selection(position -> {
            filter.setCategory(position == 0 ? null : Category.values()[position - 1]);
            render();
        }));
        memberSpinner.setOnItemSelectedListener(selection(position -> {
            filter.setMemberId(position > 0 && position <= memberIds.size() ? memberIds.get(position - 1) : null);
            render();
        }));
        Spinner sort = controls.findViewById(R.id.spinnerHistorySort);
        setOptions(sort, java.util.Arrays.asList(activity.getResources().getStringArray(R.array.e_history_sort)));
        sort.setOnItemSelectedListener(selection(position -> {
            filter.setNewestFirst(position == 0);
            render();
        }));
        ((EditText) controls.findViewById(R.id.editHistorySearch)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter.setKeyword(s.toString());
                render();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    public void start() {
        if (active) return;
        active = true;
        int request = ++generation;
        loaded = false;
        memberError = null;
        retry.setVisibility(View.GONE);
        status.setText(R.string.e_loading_history);
        if (groupId == null || groupId.isEmpty()) {
            showError(new IllegalArgumentException(activity.getString(R.string.e_missing_group)));
            return;
        }
        loadMembers(request);
        int version = snapshotVersion;
        registration = historyService.listen(groupId, transactions -> {
            if (isCurrent(request)) onTransactionsChanged(transactions);
        });
        // The shared listener only logs errors. A read also gives the UI an actionable error.
        historyService.getHistory(groupId, null, new Callback<List<Transaction>>() {
            @Override public void onSuccess(List<Transaction> transactions) {
                if (isCurrent(request) && version == snapshotVersion) onTransactionsChanged(transactions);
            }
            @Override public void onFailure(Exception e) {
                if (isCurrent(request) && version == snapshotVersion) showError(e);
            }
        });
    }

    public void stop() {
        active = false;
        generation++;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    @Override public void onTransactionsChanged(List<Transaction> transactions) {
        if (!active) return;
        snapshotVersion++;
        loaded = true;
        latest = new ArrayList<>(transactions);
        retry.setVisibility(memberError == null ? View.GONE : View.VISIBLE);
        render();
    }

    private void loadMembers(int request) {
        groupService.getGroup(groupId, new Callback<Group>() {
            @Override public void onSuccess(Group group) {
                if (!isCurrent(request)) return;
                if (group == null) {
                    showMemberError(new IllegalStateException(activity.getString(R.string.e_group_missing)));
                    return;
                }
                groupService.getMembers(group, new Callback<Map<String, User>>() {
                    @Override public void onSuccess(Map<String, User> users) {
                        if (!isCurrent(request)) return;
                        String selected = filter.getMemberId();
                        members.clear();
                        members.putAll(users);
                        memberIds.clear();
                        memberIds.addAll(group.getMemberIds());
                        memberIds.sort((a, b) -> {
                            int byName = memberName(a).compareToIgnoreCase(memberName(b));
                            return byName == 0 ? a.compareTo(b) : byName;
                        });
                        List<String> names = new ArrayList<>();
                        names.add(activity.getString(R.string.e_all_members));
                        for (String uid : memberIds) names.add(memberName(uid));
                        setOptions(memberSpinner, names);
                        memberSpinner.setSelection(selected == null ? 0 : memberIds.indexOf(selected) + 1);
                        render();
                    }
                    @Override public void onFailure(Exception e) { if (isCurrent(request)) showMemberError(e); }
                });
            }
            @Override public void onFailure(Exception e) { if (isCurrent(request)) showMemberError(e); }
        });
    }

    private void render() {
        adapter.setNotifyOnChange(false);
        adapter.clear();
        adapter.addAll(filter.apply(latest));
        adapter.notifyDataSetChanged();
        if (memberError != null) {
            status.setText(memberError);
        } else if (retry.getVisibility() != View.VISIBLE) {
            if (!loaded) {
                status.setText(R.string.e_loading_history);
            } else if (adapter.isEmpty()) {
                status.setText(R.string.e_no_matches);
            } else {
                status.setText(activity.getResources().getQuantityString(
                        R.plurals.e_transaction_count, adapter.getCount(), adapter.getCount()));
            }
        }
    }

    private boolean isCurrent(int request) { return active && request == generation; }

    private void showMemberError(Exception e) {
        memberError = activity.getString(R.string.e_load_error, e.getMessage());
        showError(e);
    }

    private void showError(Exception e) {
        status.setText(activity.getString(R.string.e_load_error, e.getMessage()));
        retry.setVisibility(View.VISIBLE);
        Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private String memberName(String uid) {
        User user = members.get(uid);
        return user != null && user.getName() != null && !user.getName().isEmpty() ? user.getName()
                : uid == null ? activity.getString(R.string.e_unknown_member) : uid;
    }

    private void openTransaction(String id) {
        Intent intent = new Intent(activity, TransactionDetailActivity.class);
        intent.putExtra(Extras.GROUP_ID, groupId);
        intent.putExtra(Extras.TRANSACTION_ID, id);
        activity.startActivity(intent);
    }

    private void setOptions(Spinner spinner, List<String> labels) {
        ArrayAdapter<String> options = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, labels);
        options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(options);
    }

    private interface Selection { void select(int position); }
    private AdapterView.OnItemSelectedListener selection(Selection callback) {
        return new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { callback.select(position); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
    }
}
