package com.pratosh.moneytracker.web;

import com.pratosh.moneytracker.dto.Dtos.*;
import com.pratosh.moneytracker.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Overview, budgets and insights. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalyticsController {
    private final CurrentUser current;
    private final OverviewService overview;
    private final BudgetService budgets;
    private final InsightsService insights;

    @GetMapping("/overview")
    public OverviewDto overview(@RequestParam(required = false) String month) {
        return overview.overview(current.get(), month);
    }

    @GetMapping("/budgets")
    public BudgetsView budgets(@RequestParam(defaultValue = "month") String period,
                               @RequestParam(required = false) String key) {
        return budgets.view(current.get(), period, key);
    }

    @GetMapping("/budgets/performance")
    public BudgetPerformance performance(@RequestParam(required = false) String endMonth,
                                         @RequestParam(defaultValue = "6") int months) {
        return budgets.performance(current.get(), endMonth, months);
    }

    @PostMapping("/budgets")
    public ResponseEntity<Void> createBudget(@Valid @RequestBody BudgetReq r) {
        budgets.create(current.get(), r);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/budgets/{id}")
    public ResponseEntity<Void> updateBudget(@PathVariable Long id, @Valid @RequestBody BudgetReq r) {
        budgets.update(current.get(), id, r);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/budgets/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable Long id) {
        budgets.delete(current.get(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/insights")
    public InsightsDto insights(@RequestParam(defaultValue = "month") String view,
                                @RequestParam(required = false) String month,
                                @RequestParam(required = false) Integer year,
                                @RequestParam(required = false) Long categoryId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return insights.insights(current.get(), view, month, year, categoryId, from, to);
    }
}
