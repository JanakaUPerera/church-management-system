package com.churchmanagement.service;

import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.dto.finance.OpeningBalanceForm;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.testsupport.FinanceFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpeningBalanceServiceTest {
    private FinanceFakes.FakeJournalRepository journalRepository;
    private FinanceFakes.FakeAccountRepository accounts;
    private OpeningBalanceService service;
    private JournalService journalService;

    @BeforeEach
    void setUp() {
        journalRepository = new FinanceFakes.FakeJournalRepository();
        accounts = new FinanceFakes.FakeAccountRepository()
                .with(new Account(1L, "1100", "Cash in Hand", AccountType.ASSET, 100L, false, CashFlowClass.CASH,
                        "CASH_IN_HAND", null, Account.Status.ACTIVE))
                .with(FinanceFakes.account(2L, "2100", "Loans Payable", AccountType.LIABILITY, 200L, false))
                .with(new Account(3L, "3100", "Accumulated Fund", AccountType.EQUITY, 300L, false,
                        CashFlowClass.FINANCING, "ACCUMULATED_FUND", null, Account.Status.ACTIVE))
                .with(FinanceFakes.account(4L, "4400", "Rent Income", AccountType.INCOME, 400L, false));
        FinanceFakes.FakeCategoryRepository categories = new FinanceFakes.FakeCategoryRepository();
        categories.active.add(FinanceFakes.category(10L, "A"));
        categories.active.add(FinanceFakes.category(11L, "B"));
        FinanceFakes.FakeFinanceSettings settings = new FinanceFakes.FakeFinanceSettings();
        journalService = new JournalService(journalRepository, accounts, categories,
                new FiscalYearService(new FinanceFakes.FakeFiscalYearRepository()), settings,
                new FinanceFakes.RecordingActivityLog(),
                Clock.fixed(Instant.parse("2027-01-02T09:00:00Z"), ZoneId.of("UTC")));
        service = new OpeningBalanceService(journalService, journalRepository, accounts, categories, settings);
        AuthContext.setCurrentUser(FinanceFakes.user(7L, "finance.menu.view", "finance.entry"));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void buildsDebitsForAssetsAndCreditsForLiabilitiesAndFunds() {
        Map<Long, BigDecimal> accountAmounts = new LinkedHashMap<>();
        accountAmounts.put(1L, new BigDecimal("1000"));
        accountAmounts.put(2L, new BigDecimal("300"));
        Map<Long, BigDecimal> fundAmounts = new LinkedHashMap<>();
        fundAmounts.put(10L, new BigDecimal("800"));
        fundAmounts.put(11L, new BigDecimal("-100"));

        List<JournalLineRequest> lines = OpeningBalanceService.buildLines(accountAmounts, fundAmounts,
                accounts.accounts, 3L);

        assertEquals(new BigDecimal("1000"), lines.get(0).debit());
        assertEquals(new BigDecimal("300"), lines.get(1).credit());
        assertEquals(new BigDecimal("800"), lines.get(2).credit());
        assertEquals(Long.valueOf(10L), lines.get(2).categoryId());
        assertEquals(new BigDecimal("100"), lines.get(3).debit());
        assertEquals(Long.valueOf(11L), lines.get(3).categoryId());
    }

    @Test
    void zeroAmountsAreSkipped() {
        List<JournalLineRequest> lines = OpeningBalanceService.buildLines(Map.of(1L, BigDecimal.ZERO),
                Map.of(10L, BigDecimal.ZERO), accounts.accounts, 3L);

        assertEquals(0, lines.size());
    }

    @Test
    void savesOneOpeningDraftDatedOnGoLive() {
        JournalEntry draft = service.saveDraft(Map.of(1L, new BigDecimal("1000")), Map.of(10L, new BigDecimal("1000")));
        JournalEntry again = service.saveDraft(Map.of(1L, new BigDecimal("1200")), Map.of(10L, new BigDecimal("1200")));

        assertEquals(draft.id(), again.id());
        assertEquals(JournalSourceType.OPENING, again.sourceType());
        assertEquals(LocalDate.of(2027, 1, 1), again.entryDate());
        assertEquals(1, journalRepository.entries.size());
    }

    @Test
    void unbalancedOpeningIsRejected() {
        assertThrows(RuntimeException.class,
                () -> service.saveDraft(Map.of(1L, new BigDecimal("1000")), Map.of(10L, new BigDecimal("900"))));
    }

    @Test
    void postedOpeningCannotBeReplaced() {
        service.saveDraft(Map.of(1L, new BigDecimal("1000")), Map.of(10L, new BigDecimal("1000")));
        service.post();

        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.saveDraft(Map.of(1L, new BigDecimal("5")), Map.of(10L, new BigDecimal("5"))));

        assertEquals("Opening balances are already posted (JE-2027-00001). Reverse that entry to correct them.",
                exception.getMessage());
    }

    @Test
    void afterReversalANewOpeningCanBeEntered() {
        service.saveDraft(Map.of(1L, new BigDecimal("1000")), Map.of(10L, new BigDecimal("1000")));
        JournalEntry posted = service.post();
        journalService.reverse(posted.id(), LocalDate.of(2027, 1, 1), "Wrong cash figure");

        JournalEntry fresh = service.saveDraft(Map.of(1L, new BigDecimal("900")), Map.of(10L, new BigDecimal("900")));

        assertEquals(JournalStatus.DRAFT, fresh.status());
    }

    @Test
    void loadShowsSavedAmountsByAccountAndCategory() {
        service.saveDraft(Map.of(1L, new BigDecimal("1000"), 2L, new BigDecimal("200")),
                Map.of(10L, new BigDecimal("800")));

        OpeningBalanceForm form = service.load();

        assertEquals(0, new BigDecimal("1000").compareTo(form.accountAmounts().get(1L)));
        assertEquals(0, new BigDecimal("200").compareTo(form.accountAmounts().get(2L)));
        assertEquals(0, new BigDecimal("800").compareTo(form.fundAmounts().get(10L)));
        assertEquals(List.of("1100", "2100"), form.balanceAccounts().stream().map(Account::code).toList());
    }

    @Test
    void loadWithoutEntryHasNoEntry() {
        assertNull(service.load().entry());
    }
}
