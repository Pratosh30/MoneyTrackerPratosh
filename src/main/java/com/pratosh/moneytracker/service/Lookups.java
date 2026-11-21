package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.Account;
import com.pratosh.moneytracker.domain.Category;
import com.pratosh.moneytracker.repo.AccountRepository;
import com.pratosh.moneytracker.repo.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class Lookups {
    private final CategoryRepository categories;
    private final AccountRepository accounts;

    public Map<Long, Category> categories(Long ownerId) {
        Map<Long, Category> m = new LinkedHashMap<>();
        for (Category c : categories.findByOwnerIdOrderByKindAscNameAsc(ownerId)) m.put(c.getId(), c);
        return m;
    }

    public Map<Long, Account> accounts(Long ownerId) {
        Map<Long, Account> m = new LinkedHashMap<>();
        for (Account a : accounts.findByOwnerIdOrderByIdAsc(ownerId)) m.put(a.getId(), a);
        return m;
    }
}
