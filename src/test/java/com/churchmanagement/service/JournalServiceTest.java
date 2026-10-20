package com.churchmanagement.service;

import com.churchmanagement.dto.finance.AccountLedger;
import com.churchmanagement.dto.finance.JournalEntryRequest;
import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.dto.finance.LedgerLine;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.testsupport.FinanceFakes;
import com.churchmanagement.validation.JournalValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalServiceTest {
    private static final LocalDate DAY = LocalDate.of(2027, 1, 10);

    private FinanceFakes.FakeJournalRepository journalRepository;
    private FinanceFakes.FakeFiscalYearRepository fiscalYearRepository;
    private FinanceFakes.FakeFinanceSettings settings;
    private FinanceFakes.RecordingActivityLog activityLog;
    private JournalService service;

    @BeforeEach
    void setUp() {
        journalRepository = new FinanceFakes.FakeJournalRepository();
        fiscalYearRepository = new FinanceFakes.FakeFiscalYearRepository();
        settings = new FinanceFakes.FakeFinanceSettings();
        activityLog = new FinanceFakes.RecordingActivityLog();
        FinanceFakes.FakeAccountRepository accounts = new FinanceFakes.FakeAccountRepository()
                .with(new Account(1L, "1100", "Cash in Hand", AccountType.ASSET, 100L, false, CashFlowClass.CASH,
                        "CASH_IN_HAND", null, Account.Status.ACTIVE))
                .with(FinanceFakes.account(2L, "4400", "Rent Income", AccountType.INCOME, 400L, false));
        FinanceFakes.FakeCategoryRepository categories = new FinanceFakes.FakeCategoryRepository();
        categories.active.add(FinanceFakes.category(10L, "A"));
        service = new JournalService(journalRepository, accounts, categories, new FiscalYearService(fiscalYearRepository),
                settings, activityLog, Clock.fixed(Instant.parse("2027-01-10T09:00:00Z"), ZoneId.of("UTC")));
        AuthContext.setCurrentUser(FinanceFakes.user(7L, "finance.menu.view", "finance.entry"));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void savesDraftWithoutNumber() {
        JournalEntry draft = service.saveDraft(rentRequest(null, "500"));

        assertEquals(JournalStatus.DRAFT, draft.status());
        assertNull(draft.entryNo());
        assertEquals(7L, draft.preparedBy());
        assertEquals(2027, draft.fiscalYear());
        assertEquals(List.of(com.churchmanagement.service.ActivityLogService.JOURNAL_DRAFT_SAVED), activityLog.actions);
    }

    @Test
    void updatesDraft() {
        JournalEntry draft = service.saveDraft(rentRequest(null, "500"));

        JournalEntry updated = service.saveDraft(rentRequest(draft.id(), "750"));

        assertEquals(draft.id(), updated.id());
        assertEquals(new BigDecimal("750"), updated.totalDebit());
    }

    @Test
    void invalidDraftIsRejected() {
        assertThrows(JournalValidationException.class, () -> service.saveDraft(new JournalEntryRequest(null,
                JournalSourceType.JOURNAL, DAY, "Rent", List.of(line(1L, "500", null, null), line(2L, null, "400", 10L)))));
    }

    @Test
    void postAssignsSequentialNumbersPerYear() {
        JournalEntry first = service.post(service.saveDraft(rentRequest(null, "500")).id());
        JournalEntry second = service.post(service.saveDraft(rentRequest(null, "600")).id());

        assertEquals("JE-2027-00001", first.entryNo());
        assertEquals("JE-2027-00002", second.entryNo());
        assertEquals(JournalStatus.POSTED, second.status());
    }

    @Test
    void postedEntryCannotBeEditedOrDeleted() {
        JournalEntry posted = service.post(service.saveDraft(rentRequest(null, "500")).id());

        assertThrows(FinanceException.class, () -> service.saveDraft(rentRequest(posted.id(), "900")));
        assertThrows(FinanceException.class, () -> service.deleteDraft(posted.id()));
    }

    @Test
    void approvalOnPreparerCannotPost() {
        settings.approvalRequired = true;
        AuthContext.setCurrentUser(FinanceFakes.user(7L, "finance.menu.view", "finance.entry", "finance.approve"));
        long id = service.saveDraft(rentRequest(null, "500")).id();

        FinanceException exception = assertThrows(FinanceException.class, () -> service.post(id));

        assertEquals("You prepared this entry; another approver must post it.", exception.getMessage());
    }

    @Test
    void approvalOnApproverPosts() {
        settings.approvalRequired = true;
        long id = service.saveDraft(rentRequest(null, "500")).id();
        AuthContext.setCurrentUser(FinanceFakes.user(8L, "finance.menu.view", "finance.approve"));

        assertEquals(JournalStatus.POSTED, service.post(id).status());
    }

    @Test
    void approvalOnEntryPermissionAloneCannotPost() {
        settings.approvalRequired = true;
        long id = service.saveDraft(rentRequest(null, "500")).id();
        AuthContext.setCurrentUser(FinanceFakes.user(9L, "finance.menu.view", "finance.entry"));

        assertThrows(FinanceException.class, () -> service.post(id));
    }

    @Test
    void closedYearIsRejected() {
        fiscalYearRepository.close(2027);

        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.saveDraft(rentRequest(null, "500")));

        assertEquals("The 2027 accounting year is closed.", exception.getMessage());
    }

    @Test
    void reverseSwapsSidesAndLinksOriginal() {
        JournalEntry posted = service.post(service.saveDraft(rentRequest(null, "500")).id());

        JournalEntry reversal = service.reverse(posted.id(), DAY.plusDays(1), "Wrong amount");

        assertEquals(JournalSourceType.REVERSAL, reversal.sourceType());
        assertEquals(posted.id(), reversal.reversalOfId());
        assertEquals(new BigDecimal("500"), reversal.lines().getFirst().credit());
        assertEquals(new BigDecimal("500"), reversal.lines().get(1).debit());
        assertEquals(Long.valueOf(10L), reversal.lines().get(1).categoryId());
        assertEquals("Reversal of JE-2027-00001 — Wrong amount", reversal.narration());
        assertTrue(reversal.isPosted());
    }

    @Test
    void entryCanBeReversedOnlyOnce() {
        JournalEntry posted = service.post(service.saveDraft(rentRequest(null, "500")).id());
        JournalEntry reversal = service.reverse(posted.id(), DAY, "Duplicate");

        assertThrows(FinanceException.class, () -> service.reverse(posted.id(), DAY, "Again"));
        assertThrows(FinanceException.class, () -> service.reverse(reversal.id(), DAY, "Undo"));
    }

    @Test
    void reversalNeedsReasonAndDateNotBeforeOriginal() {
        JournalEntry posted = service.post(service.saveDraft(rentRequest(null, "500")).id());

        assertThrows(FinanceException.class, () -> service.reverse(posted.id(), DAY, " "));
        assertThrows(FinanceException.class, () -> service.reverse(posted.id(), DAY.minusDays(1), "Early"));
    }

    @Test
    void draftCannotBeSavedWithoutEntryPermission() {
        AuthContext.setCurrentUser(FinanceFakes.user(3L, "finance.menu.view"));

        assertThrows(FinanceException.class, () -> service.saveDraft(rentRequest(null, "500")));
    }

    @Test
    void onlyJournalAndOpeningDraftsCanBeSaved() {
        assertThrows(FinanceException.class, () -> service.saveDraft(new JournalEntryRequest(null,
                JournalSourceType.RECEIPT, DAY, "Receipt", rentRequest(null, "1").lines())));
    }

    @Test
    void canPostAndCanReverseFollowPermissions() {
        JournalEntry draft = service.saveDraft(rentRequest(null, "500"));
        assertTrue(service.canPost(draft));
        settings.approvalRequired = true;
        assertFalse(service.canPost(draft));
    }

    @Test
    void ledgerRunsBalancesInNormalDirection() {
        journalRepository.openingNetDebit = new BigDecimal("-100.00");
        journalRepository.ledger.add(new LedgerLine(1L, "JE-2027-00001", DAY, "Rent", null, "A", null,
                BigDecimal.ZERO, new BigDecimal("500.00")));
        journalRepository.ledger.add(new LedgerLine(2L, "JE-2027-00002", DAY, "Refund", null, "A", null,
                new BigDecimal("50.00"), BigDecimal.ZERO));

        AccountLedger ledger = service.ledger(2L, DAY, DAY);

        assertEquals(new BigDecimal("100.00"), ledger.openingBalance());
        assertEquals(new BigDecimal("600.00"), ledger.rows().getFirst().balance());
        assertEquals(new BigDecimal("550.00"), ledger.closingBalance());
    }

    private static JournalEntryRequest rentRequest(Long id, String amount) {
        return new JournalEntryRequest(id, JournalSourceType.JOURNAL, DAY, "Hall rent",
                List.of(line(1L, amount, null, null), line(2L, null, amount, 10L)));
    }

    private static JournalLineRequest line(long accountId, String debit, String credit, Long categoryId) {
        return new JournalLineRequest(accountId, debit == null ? null : new BigDecimal(debit),
                credit == null ? null : new BigDecimal(credit), categoryId, null, null);
    }
}
