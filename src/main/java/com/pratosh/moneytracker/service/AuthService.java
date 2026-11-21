package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.Account;
import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.domain.Category;
import com.pratosh.moneytracker.dto.Dtos.LoginReq;
import com.pratosh.moneytracker.dto.Dtos.RegisterReq;
import com.pratosh.moneytracker.repo.AccountRepository;
import com.pratosh.moneytracker.repo.AppUserRepository;
import com.pratosh.moneytracker.repo.CategoryRepository;
import com.pratosh.moneytracker.util.ApiException;
import com.pratosh.moneytracker.util.Currencies;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Pattern USER_ID = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern PIN = Pattern.compile("^\\d{4,6}$");

    private final AppUserRepository users;
    private final CategoryRepository categories;
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final Credentials credentials;
    private final LoginLockoutService lockout;
    private final AuthenticationManager authManager;

    public static String key(String userId) {
        return userId == null ? "" : userId.trim().toLowerCase(Locale.ROOT);
    }

    public static void validateUserId(String userId) {
        String v = userId == null ? "" : userId.trim();
        if (v.length() < 3) throw ApiException.bad("User ID must be at least 3 characters.");
        if (v.length() > 24) throw ApiException.bad("User ID must be 24 characters or fewer.");
        if (!USER_ID.matcher(v).matches())
            throw ApiException.bad("Use only letters, numbers, dots, underscores or hyphens in the User ID.");
    }

    public static void validatePin(String pin) {
        if (pin == null || !PIN.matcher(pin).matches()) throw ApiException.bad("PIN must be 4 to 6 digits.");
    }

    public boolean available(String userId) {
        validateUserId(userId);
        return !users.existsByUserKey(key(userId));
    }

    @Transactional
    public AppUser register(RegisterReq r) {
        validateUserId(r.userId());
        validatePin(r.pin());
        String currency = r.currency() == null || r.currency().isBlank() ? "INR" : r.currency();
        if (!Currencies.isValid(currency)) throw ApiException.bad("Please choose a supported currency.");
        String userKey = key(r.userId());
        if (users.existsByUserKey(userKey)) throw ApiException.conflict("That User ID is already taken. Please pick another.");

        AppUser u = new AppUser();
        u.setUserKey(userKey);
        u.setUserId(r.userId().trim());
        u.setDisplayName(r.displayName() == null || r.displayName().isBlank() ? r.userId().trim() : r.displayName().trim());
        u.setPinHash(encoder.encode(credentials.of(userKey, r.pin())));
        u.setCurrency(currency);
        u.setOnboarded(true);
        try {
            u = users.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("That User ID is already taken. Please pick another.");
        }
        seedDefaults(u.getId());
        return u;
    }

    /** Verifies the User ID + PIN, applying the escalating lockout. Returns the authenticated token. */
    public Authentication authenticate(String userId, String pin) {
        String userKey = key(userId);
        if (userKey.isEmpty() || userKey.length() > 24 || pin == null || !PIN.matcher(pin).matches())
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect User ID or PIN.");
        LoginLockoutService.Status st = lockout.check(userKey);
        if (st.locked()) throw lockedOut(st);
        try {
            Authentication a = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(userKey, credentials.of(userKey, pin)));
            lockout.clear(userKey);
            return a;
        } catch (AuthenticationException e) {
            LoginLockoutService.Status after = lockout.recordFailure(userKey);
            if (after.locked()) throw lockedOut(after);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect User ID or PIN. " + after.attemptsRemaining()
                    + " attempt(s) left before a " + after.waitHours() + "-hour lockout.");
        }
    }

    public Authentication authenticate(LoginReq r) {
        return authenticate(r.userId(), r.pin());
    }

    private ApiException lockedOut(LoginLockoutService.Status s) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many incorrect attempts. Please try again in " + LoginLockoutService.describe(s.remainingSeconds()) + ".");
    }

    private void seedDefaults(Long ownerId) {
        for (String[] a : new String[][]{{"Cash", "cash"}, {"Bank Account", "bank"}}) {
            Account acc = new Account();
            acc.setOwnerId(ownerId);
            acc.setName(a[0]);
            acc.setType(a[1]);
            accounts.save(acc);
        }
        String[][] cats = {
                {"Salary", "income", "#16a34a"}, {"Business", "income", "#0ea5e9"},
                {"Interest", "income", "#14b8a6"}, {"Other Income", "income", "#84cc16"},
                {"Food & Dining", "expense", "#f97316"}, {"Groceries", "expense", "#eab308"},
                {"Rent", "expense", "#ef4444"}, {"Transport", "expense", "#3b82f6"},
                {"Utilities", "expense", "#8b5cf6"}, {"Shopping", "expense", "#ec4899"},
                {"Health", "expense", "#06b6d4"}, {"Entertainment", "expense", "#a855f7"},
                {"Education", "expense", "#22c55e"}, {"Other Expense", "expense", "#64748b"}};
        List<Category> list = new java.util.ArrayList<>();
        for (String[] c : cats) {
            Category cat = new Category();
            cat.setOwnerId(ownerId);
            cat.setName(c[0]);
            cat.setKind(c[1]);
            cat.setColor(c[2]);
            list.add(cat);
        }
        categories.saveAll(list);
    }
}
