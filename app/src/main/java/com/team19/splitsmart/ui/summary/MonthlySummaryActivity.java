package com.team19.splitsmart.ui.summary;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.service.HistoryService;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.Extras;

/** C4 Balance & Summary Screens. Owner: Feature 5. Monthly expense summary. */
public class MonthlySummaryActivity extends AppCompatActivity {

    private HistoryService historyService;
    private String groupId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_monthly_summary);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        historyService = new HistoryService();
        groupId = getIntent().getStringExtra(Extras.GROUP_ID);
    }

    private void onMonthSelected(String month) {
        // TODO (Feature 5): historyService.getMonthlySummary(groupId, month, ...). month is "YYYY-MM".
    }
}
