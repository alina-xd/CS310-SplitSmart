package com.team19.splitsmart.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.ui.summary.MonthlySummaryActivity;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.MoneyUtil;

import org.junit.Test;

import java.lang.reflect.Method;

public class UITest {

    @Test
    public void uscEmailValidation_acceptsCampusAddress_andRejectsAnythingElse() {
        AuthService auth = new AuthService(null, null);

        assertTrue(auth.isValidUscEmail("student@usc.edu"));
        assertTrue(auth.isValidUscEmail("Student@USC.EDU"));
        assertFalse(auth.isValidUscEmail("student@gmail.com"));
        assertFalse(auth.isValidUscEmail("student@usc.edu.com"));
        assertFalse(auth.isValidUscEmail("student@other.edu"));
        assertFalse(auth.isValidUscEmail(""));
        assertFalse(auth.isValidUscEmail(null));
    }

    @Test
    public void uscIdValidation_requiresExactlyTenDigits() {
        AuthService auth = new AuthService(null, null);

        assertTrue(auth.isValidUscId("1234567890"));
        assertTrue(auth.isValidUscId(" 0123456789 "));
        assertFalse(auth.isValidUscId("12345"));
        assertFalse(auth.isValidUscId("123456789A"));
        assertFalse(auth.isValidUscId("123-456-7890"));
        assertFalse(auth.isValidUscId(""));
        assertFalse(auth.isValidUscId(null));
    }

    @Test
    public void registerValidationReportsTheCorrectFieldAndMessage() {
        AuthService auth = new AuthService(null, null);

        expectFailure(auth, "", "student@usc.edu", "1234567890", "secret",
                "name", "Enter your name");
        expectFailure(auth, "Student", "student@gmail.com", "1234567890", "secret",
                "email", "Use your @usc.edu email");
        expectFailure(auth, "Student", "student@usc.edu", "12345", "secret",
                "uscId", "USC ID must be exactly 10 digits");
        expectFailure(auth, "Student", "student@usc.edu", "1234567890", "",
                "password", "Enter a password");
    }

    @Test
    public void summaryScreenFormatsMonthAndCategoryLabelsForRendering() throws Exception {
        Method monthLabel = MonthlySummaryActivity.class.getDeclaredMethod("monthLabel", String.class);
        Method categoryLabel = MonthlySummaryActivity.class.getDeclaredMethod("categoryLabel", Category.class);
        monthLabel.setAccessible(true);
        categoryLabel.setAccessible(true);

        assertEquals("November 2026", monthLabel.invoke(null, "2026-11"));
        assertEquals("Food", categoryLabel.invoke(null, Category.FOOD));
        assertEquals("Transportation", categoryLabel.invoke(null, Category.TRANSPORTATION));
    }

    @Test
    public void moneyFormattingCreatesScreenFriendlyStringsForSummaryViews() {
        assertEquals("$10.00", MoneyUtil.format(1000));
        assertEquals("$0.00", MoneyUtil.format(0));
        assertEquals("-$1.25", MoneyUtil.format(-125));
        assertEquals("$123.45", MoneyUtil.format(12345));
    }

    private static void expectFailure(AuthService auth, String name, String email, String uscId,
                                      String password, String expectedField, String expectedMessage) {
        final String[] field = new String[1];
        final String[] message = new String[1];

        auth.register(name, email, uscId, password, new Callback<User>() {
            @Override
            public void onSuccess(User result) {
                throw new AssertionError("Expected validation failure");
            }

            @Override
            public void onFailure(Exception e) {
                ValidationException validation = (ValidationException) e;
                field[0] = validation.getField();
                message[0] = validation.getMessage();
            }
        });

        assertEquals(expectedField, field[0]);
        assertEquals(expectedMessage, message[0]);
    }
}
