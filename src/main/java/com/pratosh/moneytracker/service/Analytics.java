package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.Category;
import com.pratosh.moneytracker.domain.Txn;
import com.pratosh.moneytracker.dto.Dtos.CategoryTotal;
import com.pratosh.moneytracker.dto.Dtos.Summary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Pure calculation helpers (no database access). All money maths uses BigDecimal. */
public final class Analytics {
    private Analytics() {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static List<Txn> between(Collection<Txn> all, LocalDate start, LocalDate endExclusive) {
        LocalDateTime a = start.atStartOfDay();
        LocalDateTime b = endExclusive.atStartOfDay();
        List<Txn> out = new ArrayList<>();
        for (Txn t : all) {
            if (!t.getOccurredAt().isBefore(a) && t.getOccurredAt().isBefore(b)) out.add(t);
        }
        return out;
    }

    public static Summary summarize(Collection<Txn> list) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (Txn t : list) {
            if ("income".equals(t.getType())) income = income.add(t.getAmount());
            else expense = expense.add(t.getAmount());
        }
        BigDecimal net = income.subtract(expense);
        Double rate = income.signum() > 0
                ? net.multiply(HUNDRED).divide(income, 1, RoundingMode.HALF_UP).doubleValue() : null;
        return new Summary(income, expense, net, rate, list.size());
    }

    public static List<CategoryTotal> categoryTotals(Collection<Txn> list, Map<Long, Category> cats, String type) {
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        BigDecimal grand = BigDecimal.ZERO;
        for (Txn t : list) {
            if (!type.equals(t.getType())) continue;
            grand = grand.add(t.getAmount());
            totals.merge(t.getCategoryId(), t.getAmount(), BigDecimal::add);
            counts.merge(t.getCategoryId(), 1, Integer::sum);
        }
        List<CategoryTotal> out = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : totals.entrySet()) {
            Category c = e.getKey() == null ? null : cats.get(e.getKey());
            double share = grand.signum() > 0
                    ? e.getValue().multiply(HUNDRED).divide(grand, 1, RoundingMode.HALF_UP).doubleValue() : 0;
            out.add(new CategoryTotal(e.getKey(), c == null ? "Uncategorised" : c.getName(),
                    c == null ? "#64748b" : c.getColor(), e.getValue(), share, counts.get(e.getKey())));
        }
        out.sort((x, y) -> y.total().compareTo(x.total()));
        return out;
    }

    /** Percentage change, or null when the earlier value is zero (nothing meaningful to compare). */
    public static Double pctChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() <= 0) return null;
        return current.subtract(previous).multiply(HUNDRED).divide(previous, 1, RoundingMode.HALF_UP).doubleValue();
    }

    public static double percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) return 0;
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP).doubleValue();
    }

    public static String status(BigDecimal spent, BigDecimal amount) {
        if (amount.signum() <= 0) return "unused";
        int cmp = spent.compareTo(amount);
        if (cmp > 0) return "exceeded";
        if (spent.signum() == 0) return "unused";
        if (spent.multiply(HUNDRED).compareTo(amount.multiply(BigDecimal.valueOf(80))) >= 0) return "approaching";
        return "on-track";
    }

    public static String statusLabel(String status) {
        return switch (status) {
            case "exceeded" -> "Exceeded";
            case "approaching" -> "Approaching limit";
            case "on-track" -> "On track";
            default -> "Not used yet";
        };
    }
}
