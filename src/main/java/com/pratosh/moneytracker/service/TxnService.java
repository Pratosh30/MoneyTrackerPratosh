package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.Account;
import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.domain.Category;
import com.pratosh.moneytracker.domain.Txn;
import com.pratosh.moneytracker.dto.Dtos.PageDto;
import com.pratosh.moneytracker.dto.Dtos.TxnDto;
import com.pratosh.moneytracker.dto.Dtos.TxnReq;
import com.pratosh.moneytracker.repo.TxnRepository;
import com.pratosh.moneytracker.util.ApiException;
import com.pratosh.moneytracker.util.Periods;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TxnService {
    public static final List<String> METHODS =
            List.of("cash", "upi", "debit card", "credit card", "bank transfer", "wallet", "cheque", "other");

    private final TxnRepository txns;
    private final Lookups lookups;

    public record Filter(String q, String type, Long categoryId, Long accountId, String paymentMethod,
                         String month, Integer year, LocalDate from, LocalDate to,
                         String sort, String dir, int page, int size) {
    }

    // ---------------- reading ----------------

    @Transactional(readOnly = true)
    public PageDto<TxnDto> search(AppUser u, Filter f) {
        Specification<Txn> spec = spec(u, f);
        String field = "amount".equals(f.sort()) ? "amount" : "occurredAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(f.dir()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(dir, field).and(Sort.by(Sort.Direction.DESC, "id"));
        int size = Math.min(Math.max(f.size(), 1), 100);
        int page = Math.max(f.page(), 0);
        Page<Txn> result = txns.findAll(spec, PageRequest.of(page, size, sort));

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (Txn t : txns.findAll(spec)) {
            if (!t.getCurrency().equals(u.getCurrency())) continue; // never mix currencies in a total
            if ("income".equals(t.getType())) income = income.add(t.getAmount());
            else expense = expense.add(t.getAmount());
        }
        Map<Long, Category> cm = lookups.categories(u.getId());
        Map<Long, Account> am = lookups.accounts(u.getId());
        List<TxnDto> items = result.getContent().stream().map(t -> toDto(t, cm, am)).toList();
        return new PageDto<>(items, result.getTotalElements(), page, result.getTotalPages(), income, expense);
    }

    private Specification<Txn> spec(AppUser u, Filter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("ownerId"), u.getId()));
            if (notBlank(f.type())) p.add(cb.equal(root.get("type"), f.type()));
            if (f.categoryId() != null) p.add(cb.equal(root.get("categoryId"), f.categoryId()));
            if (f.accountId() != null) p.add(cb.equal(root.get("accountId"), f.accountId()));
            if (notBlank(f.paymentMethod())) p.add(cb.equal(root.get("paymentMethod"), f.paymentMethod()));
            if (notBlank(f.q())) {
                String like = "%" + f.q().trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("notes"), "")), like, '\\')));
            }
            LocalDate start = null;
            LocalDate end = null; // exclusive
            if (notBlank(f.month())) {
                YearMonth ym = Periods.month(f.month());
                start = ym.atDay(1);
                end = ym.plusMonths(1).atDay(1);
            } else if (f.year() != null) {
                int y = Periods.year(f.year());
                start = LocalDate.of(y, 1, 1);
                end = LocalDate.of(y + 1, 1, 1);
            } else {
                if (f.from() != null) start = f.from();
                if (f.to() != null) end = f.to().plusDays(1);
            }
            if (start != null)
                p.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("occurredAt"), start.atStartOfDay()));
            if (end != null)
                p.add(cb.lessThan(root.<LocalDateTime>get("occurredAt"), end.atStartOfDay()));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public TxnDto toDto(Txn t, Map<Long, Category> cm, Map<Long, Account> am) {
        Category c = t.getCategoryId() == null ? null : cm.get(t.getCategoryId());
        Account a = t.getAccountId() == null ? null : am.get(t.getAccountId());
        return new TxnDto(t.getId(), t.getType(), t.getAmount(), t.getCurrency(), t.getCategoryId(),
                c == null ? null : c.getName(), c == null ? null : c.getColor(), t.getAccountId(),
                a == null ? null : a.getName(), t.getDescription(), t.getNotes(), t.getPaymentMethod(),
                t.getOccurredAt(), t.isDemo());
    }

    // ---------------- writing ----------------

    @Transactional
    public TxnDto create(AppUser u, TxnReq r) {
        Txn t = new Txn();
        t.setOwnerId(u.getId());
        t.setCurrency(u.getCurrency());
        apply(u, t, r);
        txns.save(t);
        return toDto(t, lookups.categories(u.getId()), lookups.accounts(u.getId()));
    }

    @Transactional
    public TxnDto update(AppUser u, Long id, TxnReq r) {
        Txn t = txns.findByIdAndOwnerId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Transaction"));
        apply(u, t, r); // currency stays as originally recorded
        txns.save(t);
        return toDto(t, lookups.categories(u.getId()), lookups.accounts(u.getId()));
    }

    @Transactional
    public void delete(AppUser u, Long id) {
        Txn t = txns.findByIdAndOwnerId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Transaction"));
        txns.delete(t);
    }

    private void apply(AppUser u, Txn t, TxnReq r) {
        String type = r.type();
        if (!"income".equals(type) && !"expense".equals(type)) throw ApiException.bad("Type must be income or expense.");
        String method = notBlank(r.paymentMethod()) ? r.paymentMethod() : "cash";
        if (!METHODS.contains(method)) throw ApiException.bad("Unknown payment method.");
        int year = r.occurredAt().getYear();
        if (year < 1970 || year > 2100) throw ApiException.bad("Please enter a valid date.");

        if (r.categoryId() != null) {
            Category c = lookups.categories(u.getId()).get(r.categoryId());
            if (c == null) throw ApiException.bad("Choose one of your own categories.");
            if (!c.getKind().equals(type)) throw ApiException.bad("That category is for " + c.getKind() + " transactions.");
        }
        if (r.accountId() != null && !lookups.accounts(u.getId()).containsKey(r.accountId()))
            throw ApiException.bad("Choose one of your own accounts.");

        t.setType(type);
        t.setAmount(r.amount().setScale(2, java.math.RoundingMode.HALF_UP));
        t.setCategoryId(r.categoryId());
        t.setAccountId(r.accountId());
        t.setDescription(notBlank(r.description()) ? r.description().trim() : null);
        t.setNotes(notBlank(r.notes()) ? r.notes().trim() : null);
        t.setPaymentMethod(method);
        t.setOccurredAt(r.occurredAt().withSecond(0).withNano(0));
    }
}
