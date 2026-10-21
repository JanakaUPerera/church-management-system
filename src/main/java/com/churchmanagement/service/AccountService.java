package com.churchmanagement.service;

import com.churchmanagement.dto.finance.AccountNode;
import com.churchmanagement.dto.finance.AccountRequest;
import com.churchmanagement.entity.Account;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.AccountRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.util.MoneyFormat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Chart of accounts maintenance. */
public class AccountService {
    private static final int MAX_NAME = 120;

    private final AccountRepository accountRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public AccountService() {
        this(new AccountRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public AccountService(AccountRepository accountRepository, ActivityLogService activityLogService, Clock clock) {
        this.accountRepository = accountRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public List<AccountNode> loadChart(boolean includeInactive) {
        require("finance.menu.view", "You do not have permission to view accounts.");
        try {
            List<Account> accounts = accountRepository.findAll().stream()
                    .filter(account -> includeInactive || account.isActive())
                    .toList();
            Map<Long, BigDecimal> netDebits = accountRepository.postedNetDebits();
            return accounts.stream()
                    .filter(Account::isTopGroup)
                    .map(top -> node(top, accounts, netDebits))
                    .toList();
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load the chart of accounts right now.", exception);
        }
    }

    public List<Account> postingAccounts() {
        try {
            return accountRepository.findAll().stream()
                    .filter(account -> !account.group() && account.isActive())
                    .toList();
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load accounts right now.", exception);
        }
    }

    public List<Account> groups() {
        try {
            return accountRepository.findAll().stream()
                    .filter(account -> account.group() && account.isActive())
                    .toList();
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load account groups right now.", exception);
        }
    }

    public Account create(AccountRequest request) {
        AuthenticatedUser user = requireManage();
        try {
            Account parent = requireParent(request.parentId());
            String code = normalizeCode(request.code());
            String name = normalizeName(request.name());
            validatePlacement(parent, code, request.group());
            if (accountRepository.findByCode(code).isPresent()) {
                throw new FinanceException("Account code " + code + " is already used.");
            }
            requireUniqueName(parent, name, null);
            Account account = new Account(null, code, name, parent.type(), parent.id(), request.group(),
                    request.cashFlowClass() == null ? parent.cashFlowClass() : request.cashFlowClass(), null,
                    blankToNull(request.description()), Account.Status.ACTIVE);
            Account saved = accountRepository.insert(account, LocalDateTime.now(clock));
            activityLogService.logFinance(user.getUserId(), ActivityLogService.ACCOUNT_CREATED,
                    String.valueOf(saved.id()), saved.displayText());
            return saved;
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to save the account right now.", exception);
        }
    }

    public Account update(long id, AccountRequest request) {
        AuthenticatedUser user = requireManage();
        try {
            Account existing = requireAccount(id);
            String code = normalizeCode(request.code());
            String name = normalizeName(request.name());
            Long parentId = existing.isTopGroup() ? null : request.parentId();
            boolean placementChanged = !code.equals(existing.code())
                    || (parentId != null && !parentId.equals(existing.parentId()));
            if (placementChanged) {
                if (existing.isTopGroup() || existing.isSystem() || accountRepository.isUsed(id)
                        || (existing.group() && accountRepository.hasChildren(id))) {
                    throw new FinanceException("Code and group cannot change after the account has been used.");
                }
                Account parent = requireParent(parentId);
                validatePlacement(parent, code, existing.group());
                if (accountRepository.findByCode(code).filter(other -> !other.id().equals(id)).isPresent()) {
                    throw new FinanceException("Account code " + code + " is already used.");
                }
            }
            if (!existing.isTopGroup()) {
                requireUniqueName(requireAccount(parentId == null ? existing.parentId() : parentId), name, id);
            }
            Account updated = new Account(id, code, name, existing.type(),
                    parentId == null ? existing.parentId() : parentId, existing.group(),
                    request.cashFlowClass() == null ? existing.cashFlowClass() : request.cashFlowClass(),
                    existing.systemKey(), blankToNull(request.description()), existing.status());
            accountRepository.update(updated, LocalDateTime.now(clock));
            activityLogService.logFinance(user.getUserId(), ActivityLogService.ACCOUNT_UPDATED, String.valueOf(id),
                    updated.displayText());
            return updated;
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to update the account right now.", exception);
        }
    }

    public void activate(long id) {
        AuthenticatedUser user = requireManage();
        try {
            Account account = requireAccount(id);
            if (!account.isTopGroup()) {
                Account parent = requireAccount(account.parentId());
                if (!parent.isActive()) {
                    throw new FinanceException("Activate " + parent.displayText() + " first.");
                }
            }
            accountRepository.updateStatus(id, Account.Status.ACTIVE, LocalDateTime.now(clock));
            activityLogService.logFinance(user.getUserId(), ActivityLogService.ACCOUNT_ACTIVATED, String.valueOf(id),
                    account.displayText());
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to update the account right now.", exception);
        }
    }

    public void deactivate(long id) {
        AuthenticatedUser user = requireManage();
        try {
            Account account = requireAccount(id);
            if (account.isTopGroup() || account.isSystem()) {
                throw new FinanceException(account.displayText() + " is a system account and cannot be deactivated.");
            }
            if (account.group() && accountRepository.hasActiveChildren(id)) {
                throw new FinanceException("Deactivate the accounts under " + account.displayText() + " first.");
            }
            if (!account.group()) {
                requireDeactivatable(account);
            }
            accountRepository.updateStatus(id, Account.Status.INACTIVE, LocalDateTime.now(clock));
            activityLogService.logFinance(user.getUserId(), ActivityLogService.ACCOUNT_DEACTIVATED,
                    String.valueOf(id), account.displayText());
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to update the account right now.", exception);
        }
    }

    public void delete(long id) {
        AuthenticatedUser user = requireManage();
        try {
            Account account = requireAccount(id);
            if (account.isTopGroup() || account.isSystem()) {
                throw new FinanceException(account.displayText() + " is a system account and cannot be deleted.");
            }
            if (accountRepository.isUsed(id)) {
                throw new FinanceException(account.displayText()
                        + " has entries and cannot be deleted; deactivate it instead.");
            }
            if (account.group() && accountRepository.hasChildren(id)) {
                throw new FinanceException("Move or delete the accounts under " + account.displayText() + " first.");
            }
            accountRepository.delete(id);
            activityLogService.logFinance(user.getUserId(), ActivityLogService.ACCOUNT_DELETED, String.valueOf(id),
                    account.displayText());
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to delete the account right now.", exception);
        }
    }

    /** The next free code under a group, following its numbering; "" when none fits. */
    public String suggestNextCode(long parentId, boolean group) {
        try {
            List<Account> chart = accountRepository.findAll();
            String code = chart.stream()
                    .filter(account -> account.id() == parentId && account.group())
                    .findFirst()
                    .map(parent -> suggestCode(parent, group, chart))
                    .orElse(null);
            return code == null ? "" : code;
        } catch (DatabaseException exception) {
            return "";
        }
    }

    /**
     * Next free code under {@code parent}. The group's spacing is the step most of its accounts use
     * (hundreds, tens or units). The first unused slot at that spacing, from the group's first slot
     * up to its highest code, fills a gap; otherwise the next slot after the highest code is used,
     * then finer steps after it, then any free code in the range. A new group takes the first free
     * hundred under a main group. Codes used anywhere in the chart are skipped. Null when nothing fits.
     */
    static String suggestCode(Account parent, boolean group, List<Account> chart) {
        Set<Integer> used = chart.stream().map(account -> Integer.parseInt(account.code())).collect(Collectors.toSet());
        List<Integer> children = chart.stream()
                .filter(account -> parent.id().equals(account.parentId()))
                .map(account -> Integer.parseInt(account.code()))
                .toList();
        int base = Integer.parseInt(parent.code());
        int high = parent.isTopGroup() ? base + 999 : base + 99;
        if (group) {
            if (!parent.isTopGroup()) {
                return null;
            }
            return format(firstFree(used, base + 100, high, 100));
        }
        if (children.isEmpty()) {
            return format(firstFree(used, base + 1, high, 1));
        }
        int max = children.stream().max(Integer::compare).orElse(base);
        int spacing = spacing(children, parent.isTopGroup());
        Integer gap = firstFree(used, base + spacing, max, spacing);
        if (gap != null) {
            return format(gap);
        }
        for (int step = spacing; step >= 1; step /= 10) {
            Integer next = firstFree(used, (max / step) * step + step, high, step);
            if (next != null) {
                return format(next);
            }
        }
        return format(firstFree(used, base + 1, high, 1));
    }

    /** The coarsest step (100 for a main group, else 10) that at least half of the codes are a multiple of. */
    private static int spacing(List<Integer> codes, boolean topGroup) {
        for (int step : topGroup ? new int[]{100, 10} : new int[]{10}) {
            long multiples = codes.stream().filter(code -> code % step == 0).count();
            if (multiples * 2 >= codes.size()) {
                return step;
            }
        }
        return 1;
    }

    private static Integer firstFree(Set<Integer> used, int from, int to, int step) {
        for (int code = from; code <= to; code += step) {
            if (!used.contains(code)) {
                return code;
            }
        }
        return null;
    }

    private static String format(Integer code) {
        return code == null ? null : String.format("%04d", code);
    }

    /** A posting account must be empty, off every draft and unused in open years before it is deactivated. */
    private void requireDeactivatable(Account account) {
        BigDecimal netDebit = accountRepository.postedNetDebit(account.id());
        if (netDebit.signum() != 0) {
            throw new FinanceException(account.displayText() + " has a balance of "
                    + MoneyFormat.format(netDebit.abs())
                    + ". Move it to another account with a journal entry before deactivating.");
        }
        if (accountRepository.hasDraftLines(account.id())) {
            throw new FinanceException(account.displayText()
                    + " is used on draft entries. Post or delete them before deactivating.");
        }
        accountRepository.openYearWithPostedLines(account.id()).ifPresent(year -> {
            throw new FinanceException(account.displayText() + " has entries in the open " + year
                    + " accounting year. It can be deactivated after that year is closed.");
        });
    }

    /** "4001–4999" for a top group, "1201–1299" for a sub-group. */
    public static String rangeText(Account group) {
        String code = group.code();
        if (group.isTopGroup()) {
            return code.charAt(0) + "001–" + code.charAt(0) + "999";
        }
        return code.substring(0, 2) + "01–" + code.substring(0, 2) + "99";
    }

    private AccountNode node(Account account, List<Account> accounts, Map<Long, BigDecimal> netDebits) {
        List<AccountNode> children = accounts.stream()
                .filter(child -> account.id().equals(child.parentId()))
                .sorted(Comparator.comparing(Account::code))
                .map(child -> node(child, accounts, netDebits))
                .toList();
        BigDecimal balance;
        if (account.group()) {
            balance = children.stream().map(AccountNode::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            BigDecimal net = netDebits.getOrDefault(account.id(), BigDecimal.ZERO);
            balance = account.type().isDebitNormal() ? net : net.negate();
        }
        return new AccountNode(account, balance, new ArrayList<>(children));
    }

    private void validatePlacement(Account parent, String code, boolean group) {
        if (!parent.group()) {
            throw new FinanceException(parent.displayText() + " is not a group.");
        }
        if (!parent.isActive()) {
            throw new FinanceException(parent.displayText() + " is inactive.");
        }
        boolean inRange = parent.isTopGroup()
                ? code.charAt(0) == parent.code().charAt(0) && !code.equals(parent.code())
                : code.startsWith(parent.code().substring(0, 2)) && !code.equals(parent.code());
        if (!inRange) {
            throw new FinanceException("Code " + code + " is outside " + parent.displayText() + " ("
                    + rangeText(parent) + ").");
        }
        if (group && (!parent.isTopGroup() || !code.endsWith("00"))) {
            throw new FinanceException("A group must sit directly under a main group and have a code ending in 00.");
        }
    }

    private void requireUniqueName(Account parent, String name, Long exceptId) {
        boolean taken = accountRepository.findAll().stream()
                .anyMatch(account -> parent.id().equals(account.parentId())
                        && !account.id().equals(exceptId)
                        && account.name().equalsIgnoreCase(name));
        if (taken) {
            throw new FinanceException(parent.displayText() + " already has an account named " + name + ".");
        }
    }

    private Account requireParent(Long parentId) {
        if (parentId == null) {
            throw new FinanceException("Choose the group this account belongs to.");
        }
        return requireAccount(parentId);
    }

    private Account requireAccount(long id) {
        return accountRepository.findById(id).orElseThrow(() -> new FinanceException("The account could not be found."));
    }

    private static String normalizeCode(String code) {
        String normalized = code == null ? "" : code.strip();
        if (!normalized.matches("\\d{4}")) {
            throw new FinanceException("Account code must be 4 digits.");
        }
        return normalized;
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isEmpty()) {
            throw new FinanceException("Enter the account name.");
        }
        if (normalized.length() > MAX_NAME) {
            throw new FinanceException("The account name cannot exceed 120 characters.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private AuthenticatedUser requireManage() {
        return require("finance.accounts.manage", "You do not have permission to manage accounts.");
    }

    private static AuthenticatedUser require(String permission, String message) {
        AuthenticatedUser user = AuthContext.getCurrentUser().orElseThrow(() -> new FinanceException(message));
        if (!new PermissionGuard(user).can(permission)) {
            throw new FinanceException(message);
        }
        return user;
    }
}
