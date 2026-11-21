package com.pratosh.moneytracker.web;

import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.dto.Dtos.*;
import com.pratosh.moneytracker.repo.AppUserRepository;
import com.pratosh.moneytracker.service.AuthService;
import com.pratosh.moneytracker.service.ProfileService;
import com.pratosh.moneytracker.service.RecurringService;
import com.pratosh.moneytracker.util.Currencies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final AppUserRepository users;
    private final RecurringService recurring;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    @GetMapping("/currencies")
    public List<Currencies.Meta> currencies() {
        return Currencies.ALL;
    }

    @GetMapping("/available")
    public Map<String, Boolean> available(@RequestParam String userId) {
        return Map.of("available", auth.available(userId));
    }

    @PostMapping("/register")
    public ProfileDto register(@Valid @RequestBody RegisterReq r, HttpServletRequest req, HttpServletResponse res) {
        AppUser u = auth.register(r);
        establish(auth.authenticate(r.userId(), r.pin()), req, res);
        return ProfileService.toDto(u);
    }

    @PostMapping("/login")
    public ProfileDto login(@Valid @RequestBody LoginReq r, HttpServletRequest req, HttpServletResponse res) {
        Authentication a = auth.authenticate(r);
        establish(a, req, res);
        AppUser u = users.findByUserKey(a.getName()).orElseThrow();
        recurring.runDue(u);
        return ProfileService.toDto(u);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /** Lets the web page ask "am I signed in?" without causing a 401. */
    @GetMapping("/session")
    public Map<String, Object> session() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> out = new LinkedHashMap<>();
        boolean ok = a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
        AppUser u = ok ? users.findByUserKey(a.getName()).orElse(null) : null;
        out.put("authenticated", u != null);
        if (u != null) {
            recurring.runDue(u);
            out.put("profile", ProfileService.toDto(u));
        }
        return out;
    }

    private void establish(Authentication a, HttpServletRequest req, HttpServletResponse res) {
        if (req.getSession(false) != null) req.changeSessionId(); // prevent session fixation
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(a);
        SecurityContextHolder.setContext(ctx);
        contextRepository.saveContext(ctx, req, res);
    }
}
