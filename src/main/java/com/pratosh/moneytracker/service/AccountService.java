package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.Account;
import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.domain.Txn;
import com.pratosh.moneytracker.dto.Dtos.AccountDto;
import com.pratosh.moneytracker.dto.Dtos.AccountReq;
import com.pratosh.moneytracker.repo.AccountRepository;
import com.pratosh.moneytracker.repo.RecurringRepository;
import com.pratosh.moneytracker.repo.TxnRepository;
import com.pratosh.moneytracker.util.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountService {
    private static final List<String> TYPES = List.of("cash", "bank", "card", "upi", "wallet", "other");

    private final AccountRepository accounts;
    private final TxnRepository txns;
    private final RecurringRepository recurring;

    @Transactional(readOnly = true)
    public List<AccountDto> list(AppUser u) {
        Map<Long, BigDecimal> delta = new HashMap<>();
        for (Txn t : txns.findByOwnerIdOrderByOccurredAtDescIdDesc(u.getId())) {
            if (t.getAccountId() == null) continue;
            BigDecimal signed = "income".equals(t.getType()) ? t.getAmount() : t.getAmount().negate();
            delta.merge(t.getAccountId(), signed, BigDecimal::add);
        }
        return accounts.findByOwnerIdOrderByIdAsc(u.getId()).stream().map(a -> new AccountDto(
                a.getId(), a.getName(), a.getType(), a.getOpeningBalance(),
                a.getOpeningBalance().add(delta.getOrDefault(a.getId(), BigDecimal.ZERO)), a.isArchived())).toList();
    }

    @Transactional
    public AccountDto create(AppUser u, AccountReq r) {
        Account a = new Account();
        a.setOwnerId(u.getId());
        apply(a, r);
        accounts.save(a);
        return list(u).stream().filter(x -> x.id().equals(a.getId())).findFirst().orElseThrow();
    }

    @Transactional
    public AccountDto update(AppUser u, Long id, AccountReq r) {
        Account a = accounts.findByIdAndOwnerId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Account"));
        apply(a, r);
        accounts.save(a);
        return list(u).stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    /** Transactions stay; they simply lose the account link. */
    @Transactional
    public void delete(AppUser u, Long id) {
        Account a = accounts.findByIdAndOwnerId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Account"));
        txns.clearAccount(u.getId(), id);
        recurring.clearAccount(u.getId(), id);
        accounts.delete(a);
    }

    private void apply(Account a, AccountReq r) {
        if (!TYPES.contains(r.type())) throw ApiException.bad("Unknown account type.");
        if (r.name().isBlank()) throw ApiException.bad("Please enter an account name.");
        a.setName(r.name().trim());
        a.setType(r.type());
        a.setOpeningBalance(r.openingBalance() == null ? BigDecimal.ZERO : r.openingBalance());
        a.setArchived(Boolean.TRUE.equals(r.archived()));
    }
}
