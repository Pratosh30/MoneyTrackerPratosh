package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.Account;
import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.domain.Category;
import com.pratosh.moneytracker.domain.Txn;
import com.pratosh.moneytracker.dto.Dtos.*;
import com.pratosh.moneytracker.repo.TxnRepository;
import com.pratosh.moneytracker.util.ApiException;
import com.pratosh.moneytracker.util.Periods;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Deterministic, data-derived insights. Comparisons are only produced when there is something to compare. */
@Service
@RequiredArgsConstructor
public class InsightsService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MON = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

    private final TxnRepository txns;
    private final TxnService txnService;
    private final BudgetService budgetService;
    private final Lookups lookups;

    @Transactional(readOnly = true)
    public InsightsDto insights(AppUser u, String view, String month, Integer year, Long categoryId,
                                LocalDate from, LocalDate to) {
        List<Txn> all = txns.findByOwnerIdAndCurrency(u.getId(), u.getCurrency()).stream()
                .filter(t -> categoryId == null || categoryId.equals(t.getCategoryId())).toList();
        Map<Long, Category> cats = lookups.categories(u.getId());
        Map<Long, Account> accs = lookups.accounts(u.getId());

        boolean custom = from != null && to != null;
        boolean yearView = !custom && "year".equals(view);
        YearMonth ym = null;
        int yr = 0;
        LocalDate start, end, pStart, pEnd;
        String label, prevLabel;
        if (custom) {
            if (to.isBefore(from)) throw ApiException.bad("The end date must be on or after the start date.");
            start = from;
            end = to.plusDays(1);
            long days = ChronoUnit.DAYS.between(start, end);
            pStart = start.minusDays(days);
            pEnd = start;
            label = from + " to " + to;
            prevLabel = "the previous " + days + " days";
        } else if (yearView) {
            yr = Periods.year(year);
            start = LocalDate.of(yr, 1, 1);
            end = start.plusYears(1);
            pStart = start.minusYears(1);
            pEnd = start;
            label = String.valueOf(yr);
            prevLabel = String.valueOf(yr - 1);
        } else {
            ym = Periods.month(month);
            start = ym.atDay(1);
            end = ym.plusMonths(1).atDay(1);
            pStart = start.minusMonths(1);
            pEnd = start;
            label = Periods.shortLabel(ym);
            prevLabel = Periods.shortLabel(ym.minusMonths(1));
        }

        List<Txn> cur = Analytics.between(all, start, end);
        List<Txn> prev = Analytics.between(all, pStart, pEnd);
        Summary s = Analytics.summarize(cur);
        Summary ps = Analytics.summarize(prev);
        Double expenseChange = Analytics.pctChange(s.expense(), ps.expense());
        Double incomeChange = Analytics.pctChange(s.income(), ps.income());

        // year-over-year (monthly view only)
        Double yoy = null;
        String yoyLabel = null;
        if (ym != null) {
            YearMonth ly = ym.minusYears(1);
            Summary lys = Analytics.summarize(Analytics.between(all, ly.atDay(1), ly.plusMonths(1).atDay(1)));
            yoy = Analytics.pctChange(s.expense(), lys.expense());
            if (yoy != null) yoyLabel = Periods.shortLabel(ly);
        }

        // trend buckets
        List<TrendPoint> trend = new ArrayList<>();
        boolean monthlyBuckets = true;
        if (custom) {
            long days = ChronoUnit.DAYS.between(start, end);
            if (days <= 31) {
                monthlyBuckets = false;
                for (LocalDate d = start; d.isBefore(end); d = d.plusDays(1)) trend.add(point(all, d.format(DAY), d, d.plusDays(1)));
            } else {
                for (YearMonth m = YearMonth.from(start); !m.isAfter(YearMonth.from(end.minusDays(1))); m = m.plusMonths(1)) {
                    LocalDate a = m.atDay(1).isBefore(start) ? start : m.atDay(1);
                    LocalDate b = m.plusMonths(1).atDay(1).isAfter(end) ? end : m.plusMonths(1).atDay(1);
                    trend.add(point(all, m.atDay(1).format(MON) + " " + m.getYear() % 100, a, b));
                }
            }
        } else if (yearView) {
            for (int m = 1; m <= 12; m++) {
                LocalDate a = LocalDate.of(yr, m, 1);
                trend.add(point(all, a.format(MON), a, a.plusMonths(1)));
            }
        } else {
            for (int i = 11; i >= 0; i--) {
                YearMonth m = ym.minusMonths(i);
                trend.add(point(all, Periods.shortLabel(m), m.atDay(1), m.plusMonths(1).atDay(1)));
            }
        }

        BigDecimal avgMonthly = null;
        if (monthlyBuckets) {
            BigDecimal sum = BigDecimal.ZERO;
            int n = 0;
            for (TrendPoint p : trend) {
                if (p.expense().signum() > 0) {
                    sum = sum.add(p.expense());
                    n++;
                }
            }
            if (n > 0) avgMonthly = sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        }
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        long elapsed = Math.max(1, ChronoUnit.DAYS.between(start, end.isAfter(tomorrow) ? tomorrow : end));
        BigDecimal avgDaily = s.expense().divide(BigDecimal.valueOf(elapsed), 2, RoundingMode.HALF_UP);

        List<CategoryTotal> catTotals = Analytics.categoryTotals(cur, cats, "expense");
        List<CategoryTotal> prevCats = Analytics.categoryTotals(prev, cats, "expense");
        List<TxnDto> largest = cur.stream().filter(t -> "expense".equals(t.getType()))
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount())).limit(5)
                .map(t -> txnService.toDto(t, cats, accs)).toList();

        // budget adherence (not available for a custom range)
        Integer budgetCount = null;
        Integer within = null;
        Double usedPct = null;
        if (!custom) {
            BudgetsView bv = budgetService.view(u, yearView ? "year" : "month", yearView ? String.valueOf(yr) : ym.toString());
            List<BudgetProgress> items = bv.items().stream()
                    .filter(b -> categoryId == null || categoryId.equals(b.categoryId())).toList();
            if (!items.isEmpty()) {
                budgetCount = items.size();
                within = (int) items.stream().filter(b -> !"exceeded".equals(b.status())).count();
                BigDecimal spent = items.stream().map(BudgetProgress::spent).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal total = items.stream().map(BudgetProgress::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                usedPct = Analytics.percent(spent, total);
            }
        }

        List<String> notes = new ArrayList<>();
        if (s.count() == 0) {
            notes.add("There are no transactions in this period yet. Add some to see observations here.");
        } else {
            if (expenseChange != null)
                notes.add("Your expenses are " + Math.round(Math.abs(expenseChange)) + "% " + (expenseChange > 0 ? "higher" : "lower")
                        + " than " + prevLabel + ".");
            int shown = 0;
            for (int i = 0; i < Math.min(5, catTotals.size()) && shown < 3; i++) {
                CategoryTotal c = catTotals.get(i);
                BigDecimal before = prevCats.stream().filter(p -> Objects.equals(p.categoryId(), c.categoryId()))
                        .map(CategoryTotal::total).findFirst().orElse(null);
                Double delta = before == null ? null : Analytics.pctChange(c.total(), before);
                if (delta != null && (i == 0 || Math.abs(delta) >= 25)) {
                    notes.add(c.name() + (i == 0 ? " is your largest spending category; its total is " : " spending is ")
                            + Math.round(Math.abs(delta)) + "% " + (delta > 0 ? "higher" : "lower") + " than " + prevLabel + ".");
                    shown++;
                }
            }
            if (s.savingsRate() != null)
                notes.add("You kept " + s.savingsRate() + "% of recorded income in " + label + ".");
            else if (s.expense().signum() > 0)
                notes.add("No income was recorded in " + label + ", so a savings rate can't be calculated.");
            if (usedPct != null)
                notes.add("Your expenses in budgeted categories used " + Math.round(usedPct) + "% of their budgets ("
                        + within + " of " + budgetCount + " within limit).");
            if (yoy != null)
                notes.add("Expenses are " + Math.round(Math.abs(yoy)) + "% " + (yoy > 0 ? "higher" : "lower") + " than " + yoyLabel + ".");
        }

        return new InsightsDto(label, prevLabel, s, ps, expenseChange, incomeChange, yoy, yoyLabel, trend, catTotals,
                largest, avgDaily, avgMonthly, budgetCount, within, usedPct, notes);
    }

    private static TrendPoint point(List<Txn> all, String label, LocalDate a, LocalDate b) {
        Summary s = Analytics.summarize(Analytics.between(all, a, b));
        return new TrendPoint(label, s.income(), s.expense(), s.savingsRate());
    }
}
