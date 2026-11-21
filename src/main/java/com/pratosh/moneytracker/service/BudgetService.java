package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.domain.Budget;
import com.pratosh.moneytracker.domain.Category;
import com.pratosh.moneytracker.domain.Txn;
import com.pratosh.moneytracker.dto.Dtos.*;
import com.pratosh.moneytracker.repo.BudgetRepository;
import com.pratosh.moneytracker.repo.TxnRepository;
import com.pratosh.moneytracker.util.ApiException;
import com.pratosh.moneytracker.util.Periods;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BudgetService {
    private final BudgetRepository budgets;
    private final TxnRepository txns;
    private final Lookups lookups;

    /** A budget period (one month or one calendar year) and the period right before it. */
    record Window(String period, String key, String label, LocalDate monthStart,
                  LocalDate start, LocalDate end, LocalDate prevStart, LocalDate prevEnd) {
    }

    static Window window(String period, String key) {
        if ("year".equals(period)) {
            int y;
            try {
                y = key == null || key.isBlank() ? java.time.Year.now().getValue() : Integer.parseInt(key.trim());
            } catch (NumberFormatException e) {
                throw ApiException.bad("Year must look like 2026.");
            }
            Periods.year(y);
            LocalDate s = LocalDate.of(y, 1, 1);
            return new Window("year", String.valueOf(y), String.valueOf(y), s, s, s.plusYears(1), s.minusYears(1), s);
        }
        if ("month".equals(period)) {
            YearMonth ym = Periods.month(key);
            LocalDate s = ym.atDay(1);
            return new Window("month", ym.toString(), Periods.longLabel(ym), s, s, s.plusMonths(1), s.minusMonths(1), s);
        }
        throw ApiException.bad("Period must be month or year.");
    }

    @Transactional(readOnly = true)
    public BudgetsView view(AppUser u, String period, String key) {
        Window w = window(period, key);
        List<Txn> expenses = txns.findByOwnerIdAndCurrency(u.getId(), u.getCurrency()).stream()
                .filter(t -> "expense".equals(t.getType())).toList();
        List<Txn> cur = Analytics.between(expenses, w.start(), w.end());
        List<Txn> prev = Analytics.between(expenses, w.prevStart(), w.prevEnd());
        Map<Long, Category> cats = lookups.categories(u.getId());

        List<BudgetProgress> items = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;
        for (Budget b : budgets.findByOwnerIdAndPeriodAndMonthStart(u.getId(), w.period(), w.monthStart())) {
            BigDecimal spent = sumFor(cur, b.getCategoryId());
            BigDecimal prevSpent = sumFor(prev, b.getCategoryId());
            String status = Analytics.status(spent, b.getAmount());
            Category c = cats.get(b.getCategoryId());
            items.add(new BudgetProgress(b.getId(), b.getCategoryId(), c == null ? "Category" : c.getName(),
                    c == null ? "#64748b" : c.getColor(), w.period(), w.key(), b.getAmount(), spent,
                    b.getAmount().subtract(spent), Analytics.percent(spent, b.getAmount()), status,
                    Analytics.statusLabel(status), prevSpent));
            totalBudget = totalBudget.add(b.getAmount());
            totalSpent = totalSpent.add(spent);
        }
        items.sort((a, b) -> Double.compare(b.percent(), a.percent()));
        String status = items.isEmpty() ? "unused" : Analytics.status(totalSpent, totalBudget);
        return new BudgetsView(w.period(), w.key(), w.label(), totalBudget, totalSpent, totalBudget.subtract(totalSpent),
                Analytics.percent(totalSpent, totalBudget), status, Analytics.statusLabel(status), items);
    }

    private static BigDecimal sumFor(List<Txn> list, Long categoryId) {
        BigDecimal s = BigDecimal.ZERO;
        for (Txn t : list) if (categoryId.equals(t.getCategoryId())) s = s.add(t.getAmount());
        return s;
    }

    /** Budgeted vs actual (in budgeted categories) for the last {@code n} months, plus totals per year. */
    @Transactional(readOnly = true)
    public BudgetPerformance performance(AppUser u, String endMonth, int n) {
        YearMonth end = Periods.month(endMonth);
        n = Math.min(Math.max(n, 1), 24);
        Map<String, BigDecimal> spent = new HashMap<>();
        for (Txn t : txns.findByOwnerIdAndCurrency(u.getId(), u.getCurrency())) {
            if (!"expense".equals(t.getType()) || t.getCategoryId() == null) continue;
            spent.merge(YearMonth.from(t.getOccurredAt()) + "|" + t.getCategoryId(), t.getAmount(), BigDecimal::add);
        }
        List<Budget> monthly = budgets.findByOwnerId(u.getId()).stream().filter(b -> "month".equals(b.getPeriod())).toList();

        List<MonthPerf> months = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            YearMonth ym = end.minusMonths(i);
            BigDecimal budgeted = BigDecimal.ZERO;
            BigDecimal used = BigDecimal.ZERO;
            for (Budget b : monthly) {
                if (!YearMonth.from(b.getMonthStart()).equals(ym)) continue;
                budgeted = budgeted.add(b.getAmount());
                used = used.add(spent.getOrDefault(ym + "|" + b.getCategoryId(), BigDecimal.ZERO));
            }
            months.add(new MonthPerf(ym.toString(), Periods.shortLabel(ym), budgeted, used, Analytics.percent(used, budgeted)));
        }

        TreeMap<Integer, BigDecimal[]> byYear = new TreeMap<>();
        for (Budget b : monthly) {
            BigDecimal[] acc = byYear.computeIfAbsent(b.getMonthStart().getYear(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(b.getAmount());
            acc[1] = acc[1].add(spent.getOrDefault(YearMonth.from(b.getMonthStart()) + "|" + b.getCategoryId(), BigDecimal.ZERO));
        }
        List<YearPerf> years = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal[]> e : byYear.entrySet())
            years.add(new YearPerf(e.getKey(), e.getValue()[0], e.getValue()[1], Analytics.percent(e.getValue()[1], e.getValue()[0])));
        return new BudgetPerformance(months, years);
    }

    @Transactional
    public void create(AppUser u, BudgetReq r) {
        Window w = window(r.period(), r.periodKey());
        checkCategory(u, r.categoryId());
        if (budgets.existsByOwnerIdAndCategoryIdAndPeriodAndMonthStart(u.getId(), r.categoryId(), w.period(), w.monthStart()))
            throw ApiException.conflict("A budget for that category and period already exists - edit it instead.");
        Budget b = new Budget();
        b.setOwnerId(u.getId());
        b.setCategoryId(r.categoryId());
        b.setPeriod(w.period());
        b.setMonthStart(w.monthStart());
        b.setAmount(r.amount());
        budgets.save(b);
    }

    @Transactional
    public void update(AppUser u, Long id, BudgetReq r) {
        Budget b = budgets.findByIdAndOwnerId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Budget"));
        Window w = window(r.period(), r.periodKey());
        checkCategory(u, r.categoryId());
        boolean moved = !b.getCategoryId().equals(r.categoryId()) || !b.getPeriod().equals(w.period())
                || !b.getMonthStart().equals(w.monthStart());
        if (moved && budgets.existsByOwnerIdAndCategoryIdAndPeriodAndMonthStart(u.getId(), r.categoryId(), w.period(), w.monthStart()))
            throw ApiException.conflict("A budget for that category and period already exists.");
        b.setCategoryId(r.categoryId());
        b.setPeriod(w.period());
        b.setMonthStart(w.monthStart());
        b.setAmount(r.amount());
        budgets.save(b);
    }

    @Transactional
    public void delete(AppUser u, Long id) {
        budgets.delete(budgets.findByIdAndOwnerId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Budget")));
    }

    private void checkCategory(AppUser u, Long categoryId) {
        Category c = lookups.categories(u.getId()).get(categoryId);
        if (c == null) throw ApiException.bad("Choose one of your own categories.");
        if (!"expense".equals(c.getKind())) throw ApiException.bad("Budgets can only be set for expense categories.");
    }
}
