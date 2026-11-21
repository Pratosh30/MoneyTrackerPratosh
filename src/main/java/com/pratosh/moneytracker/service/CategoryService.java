package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.domain.Category;
import com.pratosh.moneytracker.dto.Dtos.CategoryDto;
import com.pratosh.moneytracker.dto.Dtos.CategoryReq;
import com.pratosh.moneytracker.repo.BudgetRepository;
import com.pratosh.moneytracker.repo.CategoryRepository;
import com.pratosh.moneytracker.repo.RecurringRepository;
import com.pratosh.moneytracker.repo.TxnRepository;
import com.pratosh.moneytracker.util.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categories;
    private final TxnRepository txns;
    private final BudgetRepository budgets;
    private final RecurringRepository recurring;

    public static CategoryDto toDto(Category c) {
        return new CategoryDto(c.getId(), c.getName(), c.getKind(), c.getColor());
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> list(AppUser u) {
        return categories.findByOwnerIdOrderByKindAscNameAsc(u.getId()).stream().map(CategoryService::toDto).toList();
    }

    @Transactional
    public CategoryDto create(AppUser u, CategoryReq r) {
        Category c = new Category();
        c.setOwnerId(u.getId());
        apply(u, c, r, true);
        return toDto(categories.save(c));
    }

    @Transactional
    public CategoryDto update(AppUser u, Long id, CategoryReq r) {
        Category c = categories.findByIdAndOwnerId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Category"));
        apply(u, c, r, !c.getName().equalsIgnoreCase(r.name().trim()) || !c.getKind().equals(r.kind()));
        return toDto(categories.save(c));
    }

    /** Deleting a category keeps the transactions (they become "Uncategorised") and removes its budgets. */
    @Transactional
    public void delete(AppUser u, Long id) {
        Category c = categories.findByIdAndOwnerId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Category"));
        txns.clearCategory(u.getId(), id);
        recurring.clearCategory(u.getId(), id);
        budgets.deleteForCategory(u.getId(), id);
        categories.delete(c);
    }

    private void apply(AppUser u, Category c, CategoryReq r, boolean checkDuplicate) {
        if (!"income".equals(r.kind()) && !"expense".equals(r.kind())) throw ApiException.bad("Kind must be income or expense.");
        String name = r.name().trim();
        if (name.isEmpty()) throw ApiException.bad("Please enter a category name.");
        if (checkDuplicate && categories.existsByOwnerIdAndKindAndNameIgnoreCase(u.getId(), r.kind(), name))
            throw ApiException.conflict("You already have a " + r.kind() + " category with that name.");
        c.setName(name);
        c.setKind(r.kind());
        if (r.color() != null && !r.color().isBlank()) c.setColor(r.color());
    }
}
