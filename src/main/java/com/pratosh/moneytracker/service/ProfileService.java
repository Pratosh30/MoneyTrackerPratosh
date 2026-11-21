package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.dto.Dtos.*;
import com.pratosh.moneytracker.repo.*;
import com.pratosh.moneytracker.util.ApiException;
import com.pratosh.moneytracker.util.Currencies;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private static final List<String> THEMES = List.of("system", "light", "dark");

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Credentials credentials;
    private final LoginLockoutService lockout;
    private final TxnRepository txns;
    private final CategoryService categoryService;
    private final AccountService accountService;
    private final GoalService goalService;
    private final BudgetRepository budgets;
    private final RecurringService recurringService;

    public static ProfileDto toDto(AppUser u) {
        return new ProfileDto(u.getId(), u.getUserId(), u.getDisplayName(), u.getCurrency(), u.getTheme(),
                u.isDemoLoaded(), u.isOnboarded());
    }

    /**
     * Changing currency only changes how new transactions are recorded and how totals are shown.
     * Existing transactions keep the currency they were recorded in and are NOT converted.
     */
    @Transactional
    public ProfileDto update(AppUser u, ProfileUpdateReq r) {
        AppUser fresh = users.findById(u.getId()).orElseThrow();
        if (r.displayName() != null && !r.displayName().isBlank()) fresh.setDisplayName(r.displayName().trim());
        if (r.currency() != null) {
            if (!Currencies.isValid(r.currency())) throw ApiException.bad("Please choose a supported currency.");
            fresh.setCurrency(r.currency());
        }
        if (r.theme() != null) {
            if (!THEMES.contains(r.theme())) throw ApiException.bad("Theme must be system, light or dark.");
            fresh.setTheme(r.theme());
        }
        return toDto(users.save(fresh));
    }

    @Transactional
    public void changePin(AppUser u, PinChangeReq r) {
        AuthService.validatePin(r.newPin());
        LoginLockoutService.Status st = lockout.check(u.getUserKey());
        if (st.locked()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many incorrect attempts. Please try again in " + LoginLockoutService.describe(st.remainingSeconds()) + ".");
        if (!encoder.matches(credentials.of(u.getUserKey(), r.currentPin()), u.getPinHash())) {
            lockout.recordFailure(u.getUserKey());
            throw ApiException.bad("Your current PIN is incorrect.");
        }
        AppUser fresh = users.findById(u.getId()).orElseThrow();
        fresh.setPinHash(encoder.encode(credentials.of(u.getUserKey(), r.newPin())));
        users.save(fresh);
        lockout.clear(u.getUserKey());
    }

    /** Everything the user owns, as one JSON document (the PIN hash is never included). */
    @Transactional(readOnly = true)
    public Map<String, Object> exportAll(AppUser u) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now().toString());
        out.put("profile", toDto(u));
        out.put("categories", categoryService.list(u));
        out.put("accounts", accountService.list(u));
        out.put("transactions", txns.findByOwnerIdOrderByOccurredAtDescIdDesc(u.getId()).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("type", t.getType());
            m.put("amount", t.getAmount());
            m.put("currency", t.getCurrency());
            m.put("categoryId", t.getCategoryId());
            m.put("accountId", t.getAccountId());
            m.put("description", t.getDescription());
            m.put("notes", t.getNotes());
            m.put("paymentMethod", t.getPaymentMethod());
            m.put("occurredAt", t.getOccurredAt());
            m.put("demo", t.isDemo());
            return m;
        }).toList());
        out.put("budgets", budgets.findByOwnerId(u.getId()).stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("categoryId", b.getCategoryId());
            m.put("period", b.getPeriod());
            m.put("periodStart", b.getMonthStart());
            m.put("amount", b.getAmount());
            return m;
        }).toList());
        out.put("goals", goalService.list(u));
        out.put("recurring", recurringService.list(u));
        return out;
    }
}
