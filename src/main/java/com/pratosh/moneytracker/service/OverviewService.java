package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.Account;
import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.domain.Category;
import com.pratosh.moneytracker.domain.Txn;
import com.pratosh.moneytracker.dto.Dtos.*;
import com.pratosh.moneytracker.repo.TxnRepository;
import com.pratosh.moneytracker.util.Periods;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OverviewService {
    private final TxnRepository txns;
    private final TxnService txnService;
    private final BudgetService budgetService;
    private final AccountService accountService;
    private final RecurringService recurringService;
    private final Lookups lookups;

    @Transactional(readOnly = true)
    public OverviewDto overview(AppUser u, String monthKey) {
        YearMonth ym = Periods.month(monthKey);
        Map<Long, Category> cats = lookups.categories(u.getId());
        Map<Long, Account> accs = lookups.accounts(u.getId());

        List<Txn> month = new ArrayList<>(Analytics.between(
                txns.findByOwnerIdAndCurrency(u.getId(), u.getCurrency()), ym.atDay(1), ym.plusMonths(1).atDay(1)));
        month.sort(Comparator.comparing(Txn::getOccurredAt).thenComparing(Txn::getId).reversed());

        BudgetsView bv = budgetService.view(u, "month", ym.toString());
        List<TxnDto> recent = month.stream().limit(10).map(t -> txnService.toDto(t, cats, accs)).toList();
        List<RecurringDto> upcoming = recurringService.list(u).stream().filter(RecurringDto::active).limit(4).toList();

        return new OverviewDto(ym.toString(), Periods.longLabel(ym), monthOptions(u, ym), Analytics.summarize(month),
                new BudgetUsage(bv.totalBudget(), bv.totalSpent(), bv.items()),
                Analytics.categoryTotals(month, cats, "expense"), recent, upcoming, accountService.list(u),
                txns.existsByOwnerIdAndCurrencyNot(u.getId(), u.getCurrency()));
    }

    /** Last 12 months, plus every earlier month back to the first transaction; newest first. */
    private List<String> monthOptions(AppUser u, YearMonth selected) {
        YearMonth now = YearMonth.now();
        YearMonth first = now.minusMonths(11);
        LocalDateTime earliest = txns.earliest(u.getId());
        if (earliest != null && YearMonth.from(earliest).isBefore(first)) first = YearMonth.from(earliest);
        if (selected.isBefore(first)) first = selected;
        YearMonth last = selected.isAfter(now) ? selected : now;
        List<String> out = new ArrayList<>();
        for (YearMonth m = last; !m.isBefore(first); m = m.minusMonths(1)) out.add(m.toString());
        return out;
    }
}
