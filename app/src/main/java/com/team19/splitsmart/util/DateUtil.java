package com.team19.splitsmart.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Creation dates are stored as "YYYY-MM-DD" strings from the device's local date (FR-2.1). */
public final class DateUtil {

    private DateUtil() {
    }

    /** Today's local date, for example "2026-11-03". */
    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** "2026-11-03" -> "2026-11", the key used by the monthly summary. */
    public static String monthOf(String date) {
        return date.substring(0, 7);
    }
}
