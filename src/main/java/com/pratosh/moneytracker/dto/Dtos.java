package com.pratosh.moneytracker.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Request and response shapes for the REST API. */
public final class Dtos {
    private Dtos() {
    }

    // ---------- auth / profile ----------
    public record RegisterReq(@NotBlank String userId, @NotBlank String pin,
                              @Size(max = 60) String displayName, String currency) {
    }

    public record LoginReq(@NotBlank String userId, @NotBlank String pin) {
    }

    public record ProfileDto(Long id, String userId, String displayName, String currency, String theme,
                             boolean demoLoaded, boolean onboarded) {
    }

    public record ProfileUpdateReq(@Size(max = 60) String displayName, String currency, String theme) {
    }

    public record PinChangeReq(@NotBlank String currentPin, @NotBlank String newPin) {
    }

    // ---------- categories / accounts ----------
    public record CategoryReq(@NotBlank @Size(max = 40) String name, @NotBlank String kind,
                              @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Colour must look like #22aa88") String color) {
    }

    public record CategoryDto(Long id, String name, String kind, String color) {
    }

    public record AccountReq(@NotBlank @Size(max = 40) String name, @NotBlank String type,
                             @Digits(integer = 12, fraction = 2) BigDecimal openingBalance, Boolean archived) {
    }

    public record AccountDto(Long id, String name, String type, BigDecimal openingBalance, BigDecimal balance,
                             boolean archived) {
    }

    // ---------- transactions ----------
    public record TxnReq(@NotBlank String type,
                         @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
                         @Digits(integer = 12, fraction = 2, message = "Amount allows up to 2 decimals") BigDecimal amount,
                         Long categoryId, Long accountId,
                         @Size(max = 200) String description, @Size(max = 1000) String notes,
                         String paymentMethod, @NotNull LocalDateTime occurredAt) {
    }

    public record TxnDto(Long id, String type, BigDecimal amount, String currency, Long categoryId,
                         String categoryName, String categoryColor, Long accountId, String accountName,
                         String description, String notes, String paymentMethod, LocalDateTime occurredAt,
                         boolean demo) {
    }

    public record PageDto<T>(List<T> items, long total, int page, int pages, BigDecimal income, BigDecimal expense) {
    }

    public record ImportResult(int imported, int skipped, List<String> errors) {
    }

    // ---------- analytics ----------
    public record Summary(BigDecimal income, BigDecimal expense, BigDecimal net, Double savingsRate, int count) {
    }

    public record CategoryTotal(Long categoryId, String name, String color, BigDecimal total, double share, int count) {
    }

    public record BudgetProgress(Long id, Long categoryId, String categoryName, String categoryColor, String period,
                                 String periodKey, BigDecimal amount, BigDecimal spent, BigDecimal remaining,
                                 double percent, String status, String statusLabel, BigDecimal previousSpent) {
    }

    public record BudgetsView(String period, String periodKey, String label, BigDecimal totalBudget,
                              BigDecimal totalSpent, BigDecimal totalRemaining, double percent, String status,
                              String statusLabel, List<BudgetProgress> items) {
    }

    public record MonthPerf(String month, String label, BigDecimal budgeted, BigDecimal spent, double percent) {
    }

    public record YearPerf(int year, BigDecimal budgeted, BigDecimal spent, double percent) {
    }

    public record BudgetPerformance(List<MonthPerf> months, List<YearPerf> years) {
    }

    public record BudgetReq(@NotNull Long categoryId, @NotBlank String period, @NotBlank String periodKey,
                            @NotNull @DecimalMin(value = "0.01", message = "Budget must be greater than zero")
                            @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    public record BudgetUsage(BigDecimal total, BigDecimal spent, List<BudgetProgress> items) {
    }

    public record OverviewDto(String month, String monthLabel, List<String> months, Summary summary,
                              BudgetUsage budget, List<CategoryTotal> categories, List<TxnDto> recent,
                              List<RecurringDto> upcoming, List<AccountDto> accounts, boolean otherCurrency) {
    }

    public record TrendPoint(String label, BigDecimal income, BigDecimal expense, Double savingsRate) {
    }

    public record InsightsDto(String periodLabel, String previousLabel, Summary summary, Summary previous,
                              Double expenseChange, Double incomeChange, Double yoyExpenseChange, String yoyLabel,
                              List<TrendPoint> trend, List<CategoryTotal> categories, List<TxnDto> largest,
                              BigDecimal avgDailySpend, BigDecimal avgMonthlySpend, Integer budgetCount,
                              Integer budgetsWithinLimit, Double budgetUsedPercent, List<String> observations) {
    }

    // ---------- goals / recurring ----------
    public record GoalReq(@NotBlank @Size(max = 80) String name,
                          @NotNull @DecimalMin(value = "0.01", message = "Target must be greater than zero")
                          @Digits(integer = 12, fraction = 2) BigDecimal targetAmount,
                          @DecimalMin(value = "0", message = "Saved amount cannot be negative")
                          @Digits(integer = 12, fraction = 2) BigDecimal savedAmount,
                          LocalDate targetDate) {
    }

    public record GoalDto(Long id, String name, BigDecimal targetAmount, BigDecimal savedAmount,
                          LocalDate targetDate, double percent) {
    }

    public record RecurringReq(@NotBlank String type,
                               @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
                               @Digits(integer = 12, fraction = 2) BigDecimal amount,
                               Long categoryId, Long accountId, @Size(max = 200) String description,
                               String paymentMethod, @NotBlank String frequency, @NotNull LocalDate nextRun,
                               Boolean active) {
    }

    public record RecurringDto(Long id, String type, BigDecimal amount, Long categoryId, String categoryName,
                               Long accountId, String accountName, String description, String paymentMethod,
                               String frequency, LocalDate nextRun, boolean active) {
    }
}
