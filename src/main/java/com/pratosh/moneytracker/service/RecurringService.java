package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.domain.Account;
import com.pratosh.moneytracker.domain.Category;
import com.pratosh.moneytracker.domain.RecurringRule;
import com.pratosh.moneytracker.domain.Txn;
import com.pratosh.moneytracker.dto.Dtos.RecurringDto;
import com.pratosh.moneytracker.dto.Dtos.RecurringReq;
import com.pratosh.moneytracker.repo.AppUserRepository;
import com.pratosh.moneytracker.repo.RecurringRepository;
import com.pratosh.moneytracker.repo.TxnRepository;
import com.pratosh.moneytracker.util.ApiException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecurringService {
    private static final Logger log = LoggerFactory.getLogger(RecurringService.class);
    public static final List<String> FREQUENCIES = List.of("weekly", "monthly", "yearly");

    private final RecurringRepository rules;
    private final TxnRepository txns;
    private final AppUserRepository users;
    private final Lookups lookups;

    @Transactional(readOnly = true)
    public List<RecurringDto> list(AppUser u) {
        Map<Long, Category> cm = lookups.categories(u.getId());
        Map<Long, Account> am = lookups.accounts(u.getId());
        return rules.findByOwnerIdOrderByNextRunAsc(u.getId()).stream().map(r -> toDto(r, cm, am)).toList();
    }

    public static RecurringDto toDto(RecurringRule r, Map<Long, Category> cm, Map<Long, Account> am) {
        Category c = r.getCategoryId() == null ? null : cm.get(r.getCategoryId());
        Account a = r.getAccountId() == null ? null : am.get(r.getAccountId());
        return new RecurringDto(r.getId(), r.getType(), r.getAmount(), r.getCategoryId(), c == null ? null : c.getName(),
                r.getAccountId(), a == null ? null : a.getName(), r.getDescription(), r.getPaymentMethod(),
                r.getFrequency(), r.getNextRun(), r.isActive());
    }

    @Transactional
    public RecurringDto create(AppUser u, RecurringReq req) {
        RecurringRule r = new RecurringRule();
        r.setOwnerId(u.getId());
        apply(u, r, req);
        rules.save(r);
        return toDto(r, lookups.categories(u.getId()), lookups.accounts(u.getId()));
    }

    @Transactional
    public RecurringDto update(AppUser u, Long id, RecurringReq req) {
        RecurringRule r = rules.findByIdAndOwnerId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Recurring rule"));
        apply(u, r, req);
        rules.save(r);
        return toDto(r, lookups.categories(u.getId()), lookups.accounts(u.getId()));
    }

    @Transactional
    public void delete(AppUser u, Long id) {
        rules.delete(rules.findByIdAndOwnerId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Recurring rule")));
    }

    private void apply(AppUser u, RecurringRule r, RecurringReq req) {
        if (!"income".equals(req.type()) && !"expense".equals(req.type())) throw ApiException.bad("Type must be income or expense.");
        if (!FREQUENCIES.contains(req.frequency())) throw ApiException.bad("Frequency must be weekly, monthly or yearly.");
        String method = req.paymentMethod() == null || req.paymentMethod().isBlank() ? "cash" : req.paymentMethod();
        if (!TxnService.METHODS.contains(method)) throw ApiException.bad("Unknown payment method.");
        if (req.categoryId() != null) {
            Category c = lookups.categories(u.getId()).get(req.categoryId());
            if (c == null) throw ApiException.bad("Choose one of your own categories.");
            if (!c.getKind().equals(req.type())) throw ApiException.bad("That category is for " + c.getKind() + " transactions.");
        }
        if (req.accountId() != null && !lookups.accounts(u.getId()).containsKey(req.accountId()))
            throw ApiException.bad("Choose one of your own accounts.");
        if (req.nextRun().getYear() < 1970 || req.nextRun().getYear() > 2100) throw ApiException.bad("Please enter a valid date.");
        r.setType(req.type());
        r.setAmount(req.amount());
        r.setCategoryId(req.categoryId());
        r.setAccountId(req.accountId());
        r.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        r.setPaymentMethod(method);
        r.setFrequency(req.frequency());
        r.setNextRun(req.nextRun());
        r.setActive(req.active() == null || req.active());
    }

    static LocalDate advance(LocalDate d, String frequency) {
        return switch (frequency) {
            case "weekly" -> d.plusWeeks(1);
            case "yearly" -> d.plusYears(1);
            default -> d.plusMonths(1);
        };
    }

    /** Creates a transaction for every occurrence that is due, then moves next_run forward. Returns how many were created. */
    @Transactional
    public int runDue(AppUser u) {
        return process(rules.findByOwnerIdAndActiveTrueAndNextRunLessThanEqual(u.getId(), LocalDate.now()), u);
    }

    /** Safety net: also run hourly for every user, so rules fire even if nobody opens the app. */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void runAllDue() {
        List<RecurringRule> due = rules.findByActiveTrueAndNextRunLessThanEqual(LocalDate.now());
        Map<Long, List<RecurringRule>> byOwner = new java.util.LinkedHashMap<>();
        for (RecurringRule r : due) byOwner.computeIfAbsent(r.getOwnerId(), k -> new ArrayList<>()).add(r);
        int total = 0;
        for (Map.Entry<Long, List<RecurringRule>> e : byOwner.entrySet()) {
            AppUser u = users.findById(e.getKey()).orElse(null);
            if (u != null) total += process(e.getValue(), u);
        }
        if (total > 0) log.info("Recurring rules created {} transaction(s)", total);
    }

    private int process(List<RecurringRule> due, AppUser u) {
        LocalDate today = LocalDate.now();
        int created = 0;
        for (RecurringRule r : due) {
            LocalDate next = r.getNextRun();
            int guard = 0;
            while (!next.isAfter(today) && guard++ < 400) {
                Txn t = new Txn();
                t.setOwnerId(u.getId());
                t.setType(r.getType());
                t.setAmount(r.getAmount());
                t.setCurrency(u.getCurrency());
                t.setCategoryId(r.getCategoryId());
                t.setAccountId(r.getAccountId());
                t.setDescription(r.getDescription());
                t.setNotes("Added by recurring rule");
                t.setPaymentMethod(r.getPaymentMethod());
                t.setOccurredAt(next.atTime(9, 0));
                t.setDemo(r.isDemo());
                txns.save(t);
                created++;
                next = advance(next, r.getFrequency());
            }
            r.setNextRun(next);
            rules.save(r);
        }
        return created;
    }
}
