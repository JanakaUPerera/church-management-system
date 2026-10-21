package com.churchmanagement.service;

import com.churchmanagement.dto.finance.AccountNode;
import com.churchmanagement.dto.finance.AccountRequest;
import com.churchmanagement.entity.Account;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.testsupport.FinanceFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceTest {
    private FinanceFakes.FakeAccountRepository repository;
    private FinanceFakes.RecordingActivityLog activityLog;
    private AccountService service;

    @BeforeEach
    void setUp() {
        repository = new FinanceFakes.FakeAccountRepository()
                .with(FinanceFakes.account(1L, "1000", "Assets", AccountType.ASSET, null, true))
                .with(new Account(2L, "1100", "Cash in Hand", AccountType.ASSET, 1L, false, CashFlowClass.CASH,
                        "CASH_IN_HAND", null, Account.Status.ACTIVE))
                .with(new Account(3L, "1200", "Bank Accounts", AccountType.ASSET, 1L, true, CashFlowClass.CASH,
                        null, null, Account.Status.ACTIVE))
                .with(FinanceFakes.account(4L, "4000", "Income", AccountType.INCOME, null, true))
                .with(FinanceFakes.account(5L, "4400", "Rent Income", AccountType.INCOME, 4L, false));
        activityLog = new FinanceFakes.RecordingActivityLog();
        service = new AccountService(repository, activityLog,
                Clock.fixed(Instant.parse("2027-01-05T09:00:00Z"), ZoneId.of("UTC")));
        AuthContext.setCurrentUser(FinanceFakes.user(1L, "finance.menu.view", "finance.accounts.manage"));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createsBankAccountUnderBankGroupWithItsTypeAndCashFlowClass() {
        Account created = service.create(new AccountRequest("1201", " BOC Current ", 3L, false, null, null));

        assertEquals(AccountType.ASSET, created.type());
        assertEquals(CashFlowClass.CASH, created.cashFlowClass());
        assertEquals("BOC Current", created.name());
        assertEquals(List.of(ActivityLogService.ACCOUNT_CREATED), activityLog.actions);
    }

    @Test
    void codeMustBeInsideTheGroupsRange() {
        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.create(new AccountRequest("1301", "Wrong", 3L, false, null, null)));

        assertEquals("Code 1301 is outside 1200 Bank Accounts (1201–1299).", exception.getMessage());
    }

    @Test
    void codeMustBeUnique() {
        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.create(new AccountRequest("1100", "Petty Cash", 1L, false, null, null)));

        assertEquals("Account code 1100 is already used.", exception.getMessage());
    }

    @Test
    void nameMustBeUniqueWithinTheGroup() {
        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.create(new AccountRequest("4401", "rent income", 4L, false, null, null)));

        assertEquals("4000 Income already has an account named rent income.", exception.getMessage());
    }

    @Test
    void parentMustBeAGroup() {
        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.create(new AccountRequest("1101", "Petty Cash", 2L, false, null, null)));

        assertEquals("1100 Cash in Hand is not a group.", exception.getMessage());
    }

    @Test
    void subGroupsOnlyUnderTopGroupsWithRoundCodes() {
        assertThrows(FinanceException.class,
                () -> service.create(new AccountRequest("1250", "Fixed Deposits", 3L, true, null, null)));
        Account group = service.create(new AccountRequest("1600", "Investments", 1L, true, CashFlowClass.INVESTING, null));
        assertTrue(group.group());
    }

    @Test
    void usedAccountKeepsCodeAndGroup() {
        repository.usedIds.add(5L);

        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.update(5L, new AccountRequest("4401", "Rent Income", 4L, false, null, null)));

        assertEquals("Code and group cannot change after the account has been used.", exception.getMessage());
        Account renamed = service.update(5L, new AccountRequest("4400", "Hall Rent", 4L, false, null, "Main hall"));
        assertEquals("Hall Rent", renamed.name());
    }

    @Test
    void systemAccountsCannotBeDeactivatedOrDeleted() {
        assertThrows(FinanceException.class, () -> service.deactivate(2L));
        assertThrows(FinanceException.class, () -> service.delete(2L));
    }

    @Test
    void groupWithActiveChildrenCannotBeDeactivated() {
        service.create(new AccountRequest("1201", "BOC Current", 3L, false, null, null));

        FinanceException exception = assertThrows(FinanceException.class, () -> service.deactivate(3L));

        assertEquals("Deactivate the accounts under 1200 Bank Accounts first.", exception.getMessage());
    }

    @Test
    void accountWithBalanceCannotBeDeactivated() {
        repository.netDebits.put(5L, new BigDecimal("-2500.00"));

        FinanceException exception = assertThrows(FinanceException.class, () -> service.deactivate(5L));

        assertEquals("4400 Rent Income has a balance of 2,500.00. Move it to another account with a journal entry "
                + "before deactivating.", exception.getMessage());
    }

    @Test
    void accountOnDraftEntryCannotBeDeactivated() {
        repository.draftIds.add(5L);

        FinanceException exception = assertThrows(FinanceException.class, () -> service.deactivate(5L));

        assertEquals("4400 Rent Income is used on draft entries. Post or delete them before deactivating.",
                exception.getMessage());
    }

    @Test
    void accountUsedInOpenYearCannotBeDeactivated() {
        repository.openYearUsage.put(5L, 2027);

        FinanceException exception = assertThrows(FinanceException.class, () -> service.deactivate(5L));

        assertEquals("4400 Rent Income has entries in the open 2027 accounting year. It can be deactivated after "
                + "that year is closed.", exception.getMessage());
    }

    @Test
    void zeroBalanceAccountUsedOnlyInClosedYearsCanBeDeactivated() {
        repository.usedIds.add(5L);
        repository.netDebits.put(5L, BigDecimal.ZERO);

        service.deactivate(5L);

        assertEquals(Account.Status.INACTIVE, repository.accounts.get(5L).status());
    }

    @Test
    void usedAccountCannotBeDeleted() {
        repository.usedIds.add(5L);

        FinanceException exception = assertThrows(FinanceException.class, () -> service.delete(5L));

        assertEquals("4400 Rent Income has entries and cannot be deleted; deactivate it instead.",
                exception.getMessage());
    }

    @Test
    void unusedAccountCanBeDeleted() {
        service.delete(5L);

        assertFalse(repository.accounts.containsKey(5L));
    }

    @Test
    void requiresManagePermission() {
        AuthContext.setCurrentUser(FinanceFakes.user(2L, "finance.menu.view"));

        FinanceException exception = assertThrows(FinanceException.class,
                () -> service.create(new AccountRequest("1201", "BOC", 3L, false, null, null)));

        assertEquals("You do not have permission to manage accounts.", exception.getMessage());
    }

    @Test
    void chartShowsBalancesInNormalDirectionAndGroupTotals() {
        repository.netDebits.put(2L, new BigDecimal("1500.00"));
        repository.netDebits.put(5L, new BigDecimal("-400.00"));

        List<AccountNode> chart = service.loadChart(false);

        AccountNode assets = chart.getFirst();
        assertEquals(new BigDecimal("1500.00"), assets.balance());
        AccountNode income = chart.get(1);
        assertEquals(new BigDecimal("400.00"), income.balance());
        assertEquals("4400", income.children().getFirst().account().code());
    }

    @Test
    void suggestsNextCodeFollowingTheGroupsNumbering() {
        Account assets = FinanceFakes.account(1L, "1000", "Assets", AccountType.ASSET, null, true);
        Account bank = FinanceFakes.account(3L, "1200", "Bank Accounts", AccountType.ASSET, 1L, true);
        Account fixed = FinanceFakes.account(6L, "1500", "Fixed Assets", AccountType.ASSET, 1L, true);
        Account income = FinanceFakes.account(4L, "4000", "Income", AccountType.INCOME, null, true);
        Account expenses = FinanceFakes.account(7L, "5000", "Expenses", AccountType.EXPENSE, null, true);
        List<Account> chart = List.of(assets, bank, fixed,
                FinanceFakes.account(2L, "1100", "Cash", AccountType.ASSET, 1L, false),
                FinanceFakes.account(10L, "1510", "Land", AccountType.ASSET, 6L, false),
                FinanceFakes.account(11L, "1520", "Vehicles", AccountType.ASSET, 6L, false),
                income,
                FinanceFakes.account(12L, "4100", "Tithes", AccountType.INCOME, 4L, false),
                FinanceFakes.account(13L, "4900", "Other Income", AccountType.INCOME, 4L, false),
                expenses,
                FinanceFakes.account(14L, "5850", "Bank Charges", AccountType.EXPENSE, 7L, false),
                FinanceFakes.account(15L, "5990", "Misc", AccountType.EXPENSE, 7L, false));

        assertEquals("1600", AccountService.suggestCode(assets, false, chart));
        assertEquals("1201", AccountService.suggestCode(bank, false, chart));
        assertEquals("1530", AccountService.suggestCode(fixed, false, chart));
        assertEquals("4910", AccountService.suggestCode(income, false, chart));
        assertEquals("5991", AccountService.suggestCode(expenses, false, chart));
        assertEquals("1600", AccountService.suggestCode(assets, true, chart));
        assertEquals("4200", AccountService.suggestCode(income, true, chart));
        assertEquals(null, AccountService.suggestCode(bank, true, chart));
    }

    @Test
    void suggestedCodeSkipsCodesAlreadyUsedAnywhere() {
        Account bank = FinanceFakes.account(3L, "1200", "Bank Accounts", AccountType.ASSET, 1L, true);
        List<Account> chart = List.of(bank,
                FinanceFakes.account(20L, "1201", "BOC", AccountType.ASSET, 3L, false),
                FinanceFakes.account(21L, "1202", "Old", AccountType.ASSET, 99L, false));

        assertEquals("1203", AccountService.suggestCode(bank, false, chart));
    }

    @Test
    void serviceSuggestsFromTheStoredChart() {
        assertEquals("1201", service.suggestNextCode(3L, false));
        assertEquals("", service.suggestNextCode(2L, false));
    }

    @Test
    void postingAccountsExcludeGroupsAndInactive() {
        repository.updateStatus(5L, Account.Status.INACTIVE, null);

        assertEquals(List.of("1100"), service.postingAccounts().stream().map(Account::code).toList());
    }
}
