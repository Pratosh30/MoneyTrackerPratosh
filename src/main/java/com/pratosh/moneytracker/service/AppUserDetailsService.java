package com.pratosh.moneytracker.service;

import com.pratosh.moneytracker.repo.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByUserKey(username)
                .map(u -> new User(u.getUserKey(), u.getPinHash(), List.of()))
                .orElseThrow(() -> new UsernameNotFoundException("unknown"));
    }
}
