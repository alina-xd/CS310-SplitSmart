package com.team19.splitsmart.ui.transaction;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.SplitMethod;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.service.ExpenseService;
import com.team19.splitsmart.service.ValidationException;
import com.team19.splitsmart.util.MoneyUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The split part of the expense form (FR-2.4, FR-2.5). Owner: Part C (this file and
 * section_expense_split.xml). Part B's ExpenseFormActivity tells it the amount, payer and
 * participants; it shows one row per participant with an input (hidden for Equal) and the
 * computed share, and recomputes the preview on every change.
 *
 * Public methods are the B <-> C contract in the README: setMembers, setParticipants, load,
 * getMethod, getShares, getInputs.
 */
public class ExpenseSplitSection {

    private final AppCompatActivity activity;
    private final ExpenseService expenseService;
    private final RadioGroup radioMethod;
    private final LinearLayout rowsContainer;
    private final TextView statusText;
    private final int statusDefaultColor;

    private Map<String, User> members = new HashMap<>();
    private long totalCents;
    private String payerId;
    private List<String> participantIds = new ArrayList<>();
    private SplitMethod method = SplitMethod.EQUAL;

    /** What the user typed per participant, kept separately for each method so switching back keeps it. */
    private final Map<String, String> exactText = new HashMap<>();
    private final Map<String, String> percentText = new HashMap<>();

    /** The share TextView of each participant row, so recompute can update them in place. */
    private final Map<String, TextView> shareViews = new HashMap<>();

    private Map<String, Long> lastShares;
    private String lastError;

    public ExpenseSplitSection(AppCompatActivity activity) {
        this(activity, new ExpenseService());
    }

    public ExpenseSplitSection(AppCompatActivity activity, ExpenseService expenseService) {
        this.activity = activity;
        this.expenseService = expenseService;
        radioMethod = activity.findViewById(R.id.radioSplitMethod);
        rowsContainer = activity.findViewById(R.id.splitRows);
        statusText = activity.findViewById(R.id.textSplitStatus);
        statusDefaultColor = statusText.getCurrentTextColor();
        radioMethod.setOnCheckedChangeListener((group, checkedId) -> {
            SplitMethod chosen = methodFor(checkedId);
            if (chosen != method) {
                method = chosen;
                rebuildRows();
            }
        });
        rebuildRows();
    }

    // ---- Contract methods ----

    /** Group members by uid, used for the names on each row. */
    public void setMembers(Map<String, User> members) {
        this.members = members == null ? new HashMap<>() : members;
        rebuildRows();
    }

    /**
     * Called by the expense form whenever the amount, payer or participants change. Typed values
     * are kept for participants who stay selected. The payer is always treated as a participant.
     */
    public void setParticipants(long totalCents, String payerId, List<String> participantIds) {
        this.totalCents = totalCents;
        this.payerId = payerId;
        List<String> ids = new ArrayList<>();
        if (payerId != null) {
            ids.add(payerId);
        }
        if (participantIds != null) {
            for (String id : participantIds) {
                if (id != null && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        // Keep the order stable (payer first, then the order the form gives) so the extra cent
        // of an equal split doesn't jump around between participants.
        if (ids.equals(this.participantIds)) {
            recompute();
        } else {
            this.participantIds = ids;
            rebuildRows();
        }
    }

    /** Edit mode: restores the method, the typed inputs, and the amount, payer and participants. */
    public void load(Expense expense) {
        if (expense == null) {
            return;
        }
        method = expense.getSplitMethod() == null ? SplitMethod.EQUAL : expense.getSplitMethod();
        exactText.clear();
        percentText.clear();
        Map<String, Double> inputs = expense.getSplitInputs();
        if (inputs != null) {
            Map<String, String> target = method == SplitMethod.PERCENTAGE ? percentText : exactText;
            for (Map.Entry<String, Double> e : inputs.entrySet()) {
                if (e.getValue() != null) {
                    target.put(e.getKey(), formatNumber(e.getValue(), method == SplitMethod.EXACT_AMOUNT));
                }
            }
        }
        List<String> ids = expense.getShares() == null
                ? new ArrayList<>() : new ArrayList<>(expense.getShares().keySet());
        // Check the radio button without the listener rebuilding twice.
        radioMethod.check(radioIdFor(method));
        this.participantIds = new ArrayList<>(); // force a rebuild in setParticipants
        setParticipants(expense.getAmountCents(), expense.getPayerId(), ids);
    }

    public SplitMethod getMethod() {
        return method;
    }

    /**
     * The shares to save (uid -> cents, one per participant, adding up to the total).
     * Throws ValidationException("split", message) when the inputs are invalid.
     */
    public Map<String, Long> getShares() throws ValidationException {
        recompute();
        if (lastError != null) {
            throw new ValidationException("split", lastError);
        }
        return new LinkedHashMap<>(lastShares);
    }

    /** What was typed, per participant: dollars (exact) or percents (percentage); empty for Equal. */
    public Map<String, Double> getInputs() {
        Map<String, Double> inputs = new LinkedHashMap<>();
        if (method == SplitMethod.EQUAL) {
            return inputs;
        }
        for (String uid : participantIds) {
            try {
                Double value = parseInput(uid);
                if (value != null) {
                    inputs.put(uid, value);
                }
            } catch (IllegalArgumentException ignored) {
                // getShares already rejects invalid inputs before the form saves.
            }
        }
        return inputs;
    }

    // ---- Rows and preview ----

    private void rebuildRows() {
        rowsContainer.removeAllViews();
        shareViews.clear();
        boolean showInput = method != SplitMethod.EQUAL;
        for (String uid : participantIds) {
            rowsContainer.addView(buildRow(uid, showInput));
        }
        recompute();
    }

    private View buildRow(String uid, boolean showInput) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(4);
        row.setPadding(0, pad, 0, pad);

        TextView name = new TextView(activity);
        String label = nameOf(uid) + (uid.equals(payerId) ? " (payer)" : "");
        name.setText(label);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (showInput) {
            EditText input = new EditText(activity);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setHint(method == SplitMethod.PERCENTAGE ? "%" : "$0.00");
            input.setGravity(Gravity.END);
            Map<String, String> store = method == SplitMethod.PERCENTAGE ? percentText : exactText;
            String saved = store.get(uid);
            if (saved != null) {
                input.setText(saved);
            }
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    store.put(uid, s.toString());
                    recompute();
                }
            });
            row.addView(input, new LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        TextView share = new TextView(activity);
        share.setGravity(Gravity.END);
        row.addView(share, new LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT));
        shareViews.put(uid, share);
        return row;
    }

    /** Recomputes the shares with Part C's strategies and updates every row and the status line. */
    private void recompute() {
        lastShares = null;
        lastError = null;

        if (participantIds.size() < 2) {
            lastError = "Select at least one participant besides the payer";
        } else {
            try {
                Map<String, Double> inputs = new LinkedHashMap<>();
                for (String uid : participantIds) {
                    // Double.valueOf keeps both branches boxed; a bare 0.0 would unbox an empty
                    // box (null) and crash instead of showing "Enter an amount for every participant".
                    inputs.put(uid, method == SplitMethod.EQUAL ? Double.valueOf(0.0) : parseInput(uid));
                }
                lastShares = expenseService.previewShares(totalCents, method, inputs);
            } catch (IllegalArgumentException e) {
                lastError = e.getMessage() + differenceHint();
            }
        }

        String payerName = payerId == null ? "the payer" : nameOf(payerId);
        for (String uid : participantIds) {
            TextView view = shareViews.get(uid);
            if (view == null) {
                continue;
            }
            if (lastShares == null) {
                view.setText("—");
            } else {
                long cents = lastShares.get(uid);
                view.setText(uid.equals(payerId)
                        ? MoneyUtil.format(cents)
                        : MoneyUtil.format(cents) + "\nowes " + payerName);
            }
        }

        if (lastError != null) {
            statusText.setTextColor(0xFFB3261E); // Material error red
            statusText.setText(lastError);
        } else {
            statusText.setTextColor(statusDefaultColor);
            statusText.setText("Shares add up to " + MoneyUtil.format(totalCents));
        }
    }

    /** For exact and percentage splits, how far the entered values are from the target (UC-2 5.3, 5.4). */
    private String differenceHint() {
        if (method == SplitMethod.EQUAL) {
            return "";
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (String uid : participantIds) {
            try {
                Double value = parseInput(uid);
                if (value == null) {
                    return "";
                }
                sum = sum.add(BigDecimal.valueOf(value));
            } catch (IllegalArgumentException e) {
                return "";
            }
        }
        if (method == SplitMethod.EXACT_AMOUNT) {
            long diff = totalCents - sum.movePointRight(2).longValue();
            return diff == 0 ? "" : (diff > 0 ? "\n" + MoneyUtil.format(diff) + " left to assign"
                    : "\n" + MoneyUtil.format(-diff) + " too much");
        }
        BigDecimal diff = BigDecimal.valueOf(100).subtract(sum).stripTrailingZeros();
        if (diff.signum() == 0) {
            return "";
        }
        return diff.signum() > 0 ? "\n" + diff.toPlainString() + "% left to assign"
                : "\n" + diff.negate().toPlainString() + "% too much";
    }

    /**
     * The typed value for a participant, or null when the box is blank. Throws
     * IllegalArgumentException with a readable message when it isn't a valid number.
     */
    private Double parseInput(String uid) {
        Map<String, String> store = method == SplitMethod.PERCENTAGE ? percentText : exactText;
        String text = store.get(uid);
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        if (method == SplitMethod.EXACT_AMOUNT) {
            try {
                return MoneyUtil.parseToCents(text) / 100.0;
            } catch (NumberFormatException | ArithmeticException e) {
                throw new IllegalArgumentException(
                        "Enter a valid amount with at most 2 decimals for " + nameOf(uid));
            }
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid percentage for " + nameOf(uid));
        }
    }

    // ---- Helpers ----

    private String nameOf(String uid) {
        User user = members.get(uid);
        return user == null || user.getName() == null ? "Member" : user.getName();
    }

    private static SplitMethod methodFor(int radioId) {
        if (radioId == R.id.radioSplitExact) {
            return SplitMethod.EXACT_AMOUNT;
        }
        if (radioId == R.id.radioSplitPercent) {
            return SplitMethod.PERCENTAGE;
        }
        return SplitMethod.EQUAL;
    }

    private static int radioIdFor(SplitMethod method) {
        switch (method) {
            case EXACT_AMOUNT:
                return R.id.radioSplitExact;
            case PERCENTAGE:
                return R.id.radioSplitPercent;
            case EQUAL:
            default:
                return R.id.radioSplitEqual;
        }
    }

    /** 12.5 -> "12.50" for dollars, 12.5 -> "12.5" for percents. */
    private static String formatNumber(double value, boolean dollars) {
        BigDecimal number = BigDecimal.valueOf(value);
        return dollars ? number.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                : number.stripTrailingZeros().toPlainString();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
