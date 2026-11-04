package com.team19.splitsmart.ui;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Keeps a screen's content clear of the status and navigation bars (edge-to-edge is on by default). */
public final class EdgeToEdgeUtil {

    private EdgeToEdgeUtil() {
    }

    /** Adds the system bar sizes to the view's own padding. Call once after setContentView. */
    public static void applySystemBarPadding(View root) {
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return insets;
        });
    }
}
