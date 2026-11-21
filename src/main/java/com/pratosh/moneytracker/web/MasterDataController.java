package com.pratosh.moneytracker.web;

import com.pratosh.moneytracker.dto.Dtos.*;
import com.pratosh.moneytracker.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Categories, accounts, goals and recurring rules. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MasterDataController {
    private final CurrentUser current;
    private final CategoryService categories;
    private final AccountService accounts;
    private final GoalService goals;
    private final RecurringService recurring;

    // ----- categories -----
    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return categories.list(current.get());
    }

    @PostMapping("/categories")
    public CategoryDto createCategory(@Valid @RequestBody CategoryReq r) {
        return categories.create(current.get(), r);
    }

    @PutMapping("/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryReq r) {
        return categories.update(current.get(), id, r);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categories.delete(current.get(), id);
        return ResponseEntity.noContent().build();
    }

    // ----- accounts -----
    @GetMapping("/accounts")
    public List<AccountDto> accounts() {
        return accounts.list(current.get());
    }

    @PostMapping("/accounts")
    public AccountDto createAccount(@Valid @RequestBody AccountReq r) {
        return accounts.create(current.get(), r);
    }

    @PutMapping("/accounts/{id}")
    public AccountDto updateAccount(@PathVariable Long id, @Valid @RequestBody AccountReq r) {
        return accounts.update(current.get(), id, r);
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        accounts.delete(current.get(), id);
        return ResponseEntity.noContent().build();
    }

    // ----- goals -----
    @GetMapping("/goals")
    public List<GoalDto> goals() {
        return goals.list(current.get());
    }

    @PostMapping("/goals")
    public GoalDto createGoal(@Valid @RequestBody GoalReq r) {
        return goals.create(current.get(), r);
    }

    @PutMapping("/goals/{id}")
    public GoalDto updateGoal(@PathVariable Long id, @Valid @RequestBody GoalReq r) {
        return goals.update(current.get(), id, r);
    }

    @DeleteMapping("/goals/{id}")
    public ResponseEntity<Void> deleteGoal(@PathVariable Long id) {
        goals.delete(current.get(), id);
        return ResponseEntity.noContent().build();
    }

    // ----- recurring -----
    @GetMapping("/recurring")
    public List<RecurringDto> recurring() {
        return recurring.list(current.get());
    }

    @PostMapping("/recurring")
    public RecurringDto createRecurring(@Valid @RequestBody RecurringReq r) {
        return recurring.create(current.get(), r);
    }

    @PutMapping("/recurring/{id}")
    public RecurringDto updateRecurring(@PathVariable Long id, @Valid @RequestBody RecurringReq r) {
        return recurring.update(current.get(), id, r);
    }

    @DeleteMapping("/recurring/{id}")
    public ResponseEntity<Void> deleteRecurring(@PathVariable Long id) {
        recurring.delete(current.get(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recurring/run")
    public Map<String, Integer> runRecurring() {
        return Map.of("created", recurring.runDue(current.get()));
    }
}
