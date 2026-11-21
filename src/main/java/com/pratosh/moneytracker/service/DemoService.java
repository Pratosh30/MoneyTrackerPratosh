package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.*;
import com.pratosh.moneytracker.repo.*;
import com.pratosh.moneytracker.util.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Optional, clearly-labelled sample data. Every row is flagged demo=true so it can be removed again. */
@Service
@RequiredArgsConstructor
public class DemoService {
    private record Seed(String category, int min, int max, int times, String method) {
    }

    private static final List<Seed> EXPENSES = List.of(
            new Seed("Food & Dining", 180, 900, 7, "upi"),
            new Seed("Groceries", 600, 2600, 3, "debit card"),
            new Seed("Rent", 12000, 12000, 1, "bank transfer"),
            new Seed("Transport", 60, 450, 6, "cash"),
            new Seed("Utilities", 400, 1800, 2, "upi"),
            new Seed("Shopping", 500, 4200, 2, "credit card"),
            new Seed("Entertainment", 200, 1200, 2, "credit card"),
            new Seed("Health", 300, 2200, 1, "upi"));

    private static final Map<String, Integer> BUDGETS = new LinkedHashMap<>();

    static {
        BUDGETS.put("Food & Dining", 6000);
        BUDGETS.put("Groceries", 7000);
        BUDGETS.put("Rent", 12000);
        BUDGETS.put("Transport", 2500);
        BUDGETS.put("Utilities", 3000);
        BUDGETS.put("Shopping", 5000);
        BUDGETS.put("Entertainment", 2000);
    }

    private final TxnRepository txns;
    private final BudgetRepository budgets;
    private final GoalRepository goals;
    private final RecurringRepository recurring;
    private final AppUserRepository users;
    private final Lookups lookups;

    @Transactional
    public void load(AppUser u) {
        if (u.isDemoLoaded()) throw ApiException.conflict("Demo data is already loaded. Clear it first to load it again.");
        Map<Long, Category> cats = lookups.categories(u.getId());
        Map<Long, Account> accs = lookups.accounts(u.getId());
        Random rnd = new Random();
        YearMonth now = YearMonth.now();
        List<Txn> rows = new ArrayList<>();

        for (int back = 0; back < 6; back++) {
            YearMonth ym = now.minusMonths(back);
            int lastDay = back == 0 ? LocalDate.now().getDayOfMonth() : ym.lengthOfMonth();

            rows.add(txn(u, "income", money(rnd, 48000, 56000), catId(cats, "Salary", "income"), accId(accs, "bank"),
                    "Monthly salary (demo)", "Demo data", "bank transfer", ym.atDay(Math.min(2, lastDay)).atTime(10, 0)));
            if (rnd.nextInt(100) < 60) {
                rows.add(txn(u, "income", money(rnd, 1500, 9000), catId(cats, "Business", "income"), accId(accs, "bank"),
                        "Freelance project (demo)", "Demo data", "upi", ym.atDay(Math.max(1, Math.min(18, lastDay))).atTime(16, 30)));
            }
            for (Seed s : EXPENSES) {
                for (int i = 0; i < s.times(); i++) {
                    int day = "Rent".equals(s.category()) ? Math.min(5, lastDay) : 1 + rnd.nextInt(lastDay);
                    String acc = "cash".equals(s.method()) ? "cash" : "bank";
                    rows.add(txn(u, "expense", money(rnd, s.min(), s.max()), catId(cats, s.category(), "expense"), accId(accs, acc),
                            s.category() + " (demo)", "Demo data", s.method(),
                            ym.atDay(day).atTime(9 + rnd.nextInt(12), rnd.nextInt(60))));
                }
            }
        }
        txns.saveAll(rows);

        List<Budget> bs = new ArrayList<>();
        for (int back = 0; back < 4; back++) {
            YearMonth ym = now.minusMonths(back);
            for (Map.Entry<String, Integer> e : BUDGETS.entrySet()) {
                Long cid = catId(cats, e.getKey(), "expense");
                if (cid == null) continue;
                Budget b = new Budget();
                b.setOwnerId(u.getId());
                b.setDemo(true);
                b.setCategoryId(cid);
                b.setPeriod("month");
                b.setMonthStart(ym.atDay(1));
                b.setAmount(BigDecimal.valueOf(e.getValue()));
                bs.add(b);
            }
        }
        // do not clash with budgets the user already created
        Set<String> existing = new HashSet<>();
        for (Budget b : budgets.findByOwnerId(u.getId())) existing.add(b.getCategoryId() + "|" + b.getPeriod() + "|" + b.getMonthStart());
        bs.removeIf(b -> existing.contains(b.getCategoryId() + "|" + b.getPeriod() + "|" + b.getMonthStart()));
        budgets.saveAll(bs);

        goals.save(goal(u, "Emergency fund (demo)", 150000, 42000, now.plusMonths(10).atDay(1)));
        goals.save(goal(u, "Trip to Japan (demo)", 200000, 35000, now.plusMonths(14).atDay(1)));

        RecurringRule r = new RecurringRule();
        r.setOwnerId(u.getId());
        r.setDemo(true);
        r.setType("expense");
        r.setAmount(new BigDecimal("649.00"));
        r.setCategoryId(catId(cats, "Entertainment", "expense"));
        r.setAccountId(accId(accs, "bank"));
        r.setDescription("Streaming subscription (demo)");
        r.setPaymentMethod("credit card");
        r.setFrequency("monthly");
        r.setNextRun(now.plusMonths(1).atDay(1));
        recurring.save(r);

        AppUser fresh = users.findById(u.getId()).orElseThrow();
        fresh.setDemoLoaded(true);
        users.save(fresh);
    }

    @Transactional
    public void clear(AppUser u) {
        txns.deleteDemo(u.getId());
        budgets.deleteDemo(u.getId());
        goals.deleteDemo(u.getId());
        recurring.deleteDemo(u.getId());
        AppUser fresh = users.findById(u.getId()).orElseThrow();
        fresh.setDemoLoaded(false);
        users.save(fresh);
    }

    private static BigDecimal money(Random rnd, int min, int max) {
        double v = min + rnd.nextDouble() * (max - min);
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static Long catId(Map<Long, Category> cats, String name, String kind) {
        for (Category c : cats.values()) if (c.getKind().equals(kind) && c.getName().equalsIgnoreCase(name)) return c.getId();
        return null;
    }

    private static Long accId(Map<Long, Account> accs, String type) {
        for (Account a : accs.values()) if (a.getType().equals(type)) return a.getId();
        return accs.isEmpty() ? null : accs.values().iterator().next().getId();
    }

    private static Txn txn(AppUser u, String type, BigDecimal amount, Long cat, Long acc, String desc, String notes,
                           String method, java.time.LocalDateTime when) {
        Txn t = new Txn();
        t.setOwnerId(u.getId());
        t.setDemo(true);
        t.setType(type);
        t.setAmount(amount);
        t.setCurrency(u.getCurrency());
        t.setCategoryId(cat);
        t.setAccountId(acc);
        t.setDescription(desc);
        t.setNotes(notes);
        t.setPaymentMethod(method);
        t.setOccurredAt(when);
        return t;
    }

    private static Goal goal(AppUser u, String name, int target, int saved, LocalDate date) {
        Goal g = new Goal();
        g.setOwnerId(u.getId());
        g.setDemo(true);
        g.setName(name);
        g.setTargetAmount(BigDecimal.valueOf(target));
        g.setSavedAmount(BigDecimal.valueOf(saved));
        g.setTargetDate(date);
        return g;
    }
}
