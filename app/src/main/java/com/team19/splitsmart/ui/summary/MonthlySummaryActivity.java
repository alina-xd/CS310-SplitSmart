package com.team19.splitsmart.ui.summary;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;
import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.service.HistoryService;
import com.team19.splitsmart.service.MonthlySummary;
import com.team19.splitsmart.service.MonthlySummaryCalculator;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;
import com.team19.splitsmart.util.DateUtil;
import com.team19.splitsmart.util.MoneyUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * C4 Balance & Summary Screens. Owner: Part C. Monthly expense summary (FR-5.2).
 * The month picker lists every month that has expenses plus the current month, newest first,
 * with the current month selected by default. The screen listens to the group's transactions,
 * so creating, editing or deleting an expense updates it by itself.
 */
public class MonthlySummaryActivity extends AppCompatActivity {

    private HistoryService historyService;
    private String groupId;
    private ListenerRegistration registration;

    private Spinner spinnerMonth;
    private TextView textCount;
    private TextView textTotal;
    private LinearLayout categoryRows;
    private TextView textError;

    /** Month keys ("YYYY-MM") in the same order as the spinner entries. */
    private final List<String> monthKeys = new ArrayList<>();
    private ArrayAdapter<String> monthAdapter;
    private String selectedMonth;
    private List<Transaction> latest = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_monthly_summary);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        historyService = new HistoryService();
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);

        spinnerMonth = findViewById(R.id.spinnerMonth);
        textCount = findViewById(R.id.textExpenseCount);
        textTotal = findViewById(R.id.textTotalSpending);
        categoryRows = findViewById(R.id.categoryRows);
        textError = findViewById(R.id.textSummaryError);

        selectedMonth = DateUtil.monthOf(DateUtil.today());
        monthAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMonth.setAdapter(monthAdapter);
        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < monthKeys.size()) {
                    onMonthSelected(monthKeys.get(position));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        updateMonths();
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (groupId == null) {
            showError("No group selected");
            return;
        }
        registration = historyService.listen(groupId, transactions -> {
            latest = transactions == null ? new ArrayList<>() : transactions;
            textError.setVisibility(View.GONE);
            updateMonths();
            render();
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    private void onMonthSelected(String month) {
        if (month.equals(selectedMonth)) {
            return;
        }
        selectedMonth = month;
        render();
    }

    /** Rebuilds the month list from the latest transactions, keeping the selected month. */
    private void updateMonths() {
        TreeSet<String> months = new TreeSet<>(Collections.reverseOrder());
        months.add(DateUtil.monthOf(DateUtil.today()));
        months.add(selectedMonth);
        for (Transaction t : latest) {
            String date = t.getCreatedDate();
            if (t.isExpense() && date != null && date.length() >= 7) {
                months.add(DateUtil.monthOf(date));
            }
        }
        monthKeys.clear();
        monthKeys.addAll(months);
        List<String> labels = new ArrayList<>();
        for (String key : monthKeys) {
            labels.add(monthLabel(key));
        }
        monthAdapter.clear();
        monthAdapter.addAll(labels);
        monthAdapter.notifyDataSetChanged();
        spinnerMonth.setSelection(monthKeys.indexOf(selectedMonth), false);
    }

    private void render() {
        MonthlySummary summary = MonthlySummaryCalculator.compute(latest, selectedMonth);
        int count = summary.getExpenseCount();
        textCount.setText(count == 1 ? "1 expense" : count + " expenses");
        textTotal.setText("Total spending: " + MoneyUtil.format(summary.getTotalCents()));

        categoryRows.removeAllViews();
        for (Category category : Category.values()) {
            Long cents = summary.getCategoryTotals().get(category);
            categoryRows.addView(categoryRow(categoryLabel(category), cents == null ? 0 : cents));
        }
    }

    private View categoryRow(String label, long cents) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Math.round(4 * getResources().getDisplayMetrics().density);
        row.setPadding(0, pad, 0, pad);

        TextView name = new TextView(this);
        name.setText(label);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView amount = new TextView(this);
        amount.setText(MoneyUtil.format(cents));
        row.addView(amount, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    /** "2026-11" -> "November 2026". */
    private static String monthLabel(String key) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("MMMM yyyy", Locale.US);
            return out.format(in.parse(key));
        } catch (ParseException e) {
            return key;
        }
    }

    /** FOOD -> "Food". */
    private static String categoryLabel(Category category) {
        String name = category.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.US);
    }
}
