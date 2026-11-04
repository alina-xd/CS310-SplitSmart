package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.model.Transaction;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class HistoryFilterTest {
    private HistoryFilter filter;
    private Expense dinner;
    private Expense movie;
    private Settlement settlement;
    private List<Transaction> history;

    @Before public void setUp() {
        filter = new HistoryFilter();
        dinner = expense("Dinner", "Thai place", Category.FOOD, "alice", 1);
        movie = expense("Movie", "IMAX", Category.ENTERTAINMENT, "carol", 3);
        settlement = new Settlement();
        settlement.setPayerId("bob");
        settlement.setRecipientId("alice");
        settlement.setCreatorId("carol");
        settlement.setNote("Venmo");
        settlement.setSequenceNo(4);
        settlement.setCreatedDate("2026-11-02");
        history = Collections.unmodifiableList(Arrays.asList(dinner, settlement, movie));
    }

    private Expense expense(String title, String note, Category category, String payer, long sequence) {
        Expense expense = new Expense();
        expense.setTitle(title);
        expense.setNote(note);
        expense.setCategory(category);
        expense.setPayerId(payer);
        expense.setSequenceNo(sequence);
        expense.setCreatedDate("2026-11-02");
        Map<String, Long> shares = new LinkedHashMap<>();
        shares.put(payer, 334L);
        shares.put("bob", 333L);
        expense.setShares(shares);
        return expense;
    }

    @Test public void unsetFiltersMatchBothTypes() {
        assertTrue(filter.matches(dinner));
        assertTrue(filter.matches(settlement));
    }
    @Test public void emptyStringsAreIgnored() {
        filter.setKeyword(""); filter.setMemberId(""); filter.setType("");
        assertEquals(3, filter.apply(history).size());
    }
    @Test public void titleSearchUsesSubstring() {
        filter.setKeyword("inn");
        assertEquals(Collections.singletonList(dinner), filter.apply(history));
    }
    @Test public void expenseNoteSearchIsCaseSensitive() {
        filter.setKeyword("IMAX"); assertTrue(filter.matches(movie));
        filter.setKeyword("imax"); assertFalse(filter.matches(movie));
    }
    @Test public void titleSearchIsCaseSensitive() {
        filter.setKeyword("dinner"); assertFalse(filter.matches(dinner));
    }
    @Test public void settlementNoteIsSearchable() {
        filter.setKeyword("Ven"); assertTrue(filter.matches(settlement));
    }
    @Test public void settlementLabelIsNotAKeywordField() {
        filter.setKeyword("Settlement"); assertFalse(filter.matches(settlement));
    }
    @Test public void missingTitleAndNoteDoNotCrash() {
        dinner.setTitle(null); dinner.setNote(null); settlement.setNote(null);
        filter.setKeyword("test");
        assertFalse(filter.matches(dinner)); assertFalse(filter.matches(settlement));
    }
    @Test public void whitespaceInKeywordIsLiteral() {
        filter.setKeyword(" ");
        assertTrue(filter.matches(dinner)); assertFalse(filter.matches(movie));
    }
    @Test public void typeCanSelectEitherKind() {
        filter.setType(Transaction.TYPE_EXPENSE); assertEquals(2, filter.apply(history).size());
        filter.setType(Transaction.TYPE_SETTLEMENT); assertEquals(Collections.singletonList(settlement), filter.apply(history));
    }
    @Test public void categoryExcludesSettlements() {
        filter.setCategory(Category.FOOD);
        assertEquals(Collections.singletonList(dinner), filter.apply(history));
    }
    @Test public void allFiveCategoriesCanBeSelected() {
        for (Category category : Category.values()) {
            dinner.setCategory(category); filter.setCategory(category);
            assertTrue(filter.matches(dinner)); assertFalse(filter.matches(settlement));
        }
    }
    @Test public void settlementTypeAndCategoryHaveNoMatches() {
        filter.setType(Transaction.TYPE_SETTLEMENT); filter.setCategory(Category.FOOD);
        assertTrue(filter.apply(history).isEmpty());
    }
    @Test public void memberMatchesPayerAndParticipantsIncludingZeroShares() {
        dinner.getShares().put("david", 0L);
        filter.setMemberId("alice"); assertTrue(filter.matches(dinner));
        filter.setMemberId("bob"); assertTrue(filter.matches(dinner));
        filter.setMemberId("david"); assertTrue(filter.matches(dinner));
    }
    @Test public void settlementMatchesBothPartiesButNotUninvolvedCreator() {
        filter.setMemberId("bob"); assertTrue(filter.matches(settlement));
        filter.setMemberId("alice"); assertTrue(filter.matches(settlement));
        filter.setMemberId("carol"); assertFalse(filter.matches(settlement));
    }
    @Test public void uninvolvedMemberDoesNotMatch() {
        filter.setMemberId("david"); assertTrue(filter.apply(history).isEmpty());
    }
    @Test public void activeFiltersAreCombinedWithAnd() {
        filter.setKeyword("IMAX"); filter.setType(Transaction.TYPE_EXPENSE);
        filter.setCategory(Category.ENTERTAINMENT); filter.setMemberId("bob");
        assertEquals(Collections.singletonList(movie), filter.apply(history));
        filter.setMemberId("alice"); assertTrue(filter.apply(history).isEmpty());
    }
    @Test public void newestFirstIsDefaultAndUsesSequenceForSameDay() {
        assertEquals(Arrays.asList(settlement, movie, dinner), filter.apply(history));
    }
    @Test public void oldestFirstUsesSequenceRatherThanDateOrInputOrder() {
        dinner.setCreatedDate("2027-01-01");
        filter.setNewestFirst(false);
        assertEquals(Arrays.asList(dinner, movie, settlement), filter.apply(history));
    }
    @Test public void sortingDoesNotOverflowLongOrMutateSource() {
        dinner.setSequenceNo(Long.MIN_VALUE); movie.setSequenceNo(Long.MAX_VALUE);
        assertEquals(Arrays.asList(movie, settlement, dinner), filter.apply(history));
        assertEquals(Arrays.asList(dinner, settlement, movie), history);
    }
    @Test public void clearingFiltersRestoresFullHistory() {
        filter.setKeyword("absent"); assertTrue(filter.apply(history).isEmpty());
        filter.setKeyword(null); assertEquals(3, filter.apply(history).size());
    }
    @Test public void emptyHistoryAndNullEntryAreSafe() {
        assertTrue(filter.apply(Collections.emptyList()).isEmpty());
        assertFalse(filter.matches(null));
    }
}
