package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.domain.AppUser;
import com.pratosh.moneytracker.repo.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.pratosh.moneytracker.util.ApiException;

@Component
@RequiredArgsConstructor
public class CurrentUser {
    private final AppUserRepository users;

    public AppUser get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        return users.findByUserKey(a.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in to continue."));
    }
}
