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
    void postingAccountsExcludeGroupsAndInactive() {
        repository.updateStatus(5L, Account.Status.INACTIVE, null);

        assertEquals(List.of("1100"), service.postingAccounts().stream().map(Account::code).toList());
    }
}
