package com.nisatsama.shelfsense.auth;

import com.nisatsama.shelfsense.account.Account;
import com.nisatsama.shelfsense.account.AccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {
    private final AccountRepository accounts;
    public AccountUserDetailsService(AccountRepository accounts) { this.accounts = accounts; }

    @Override public UserDetails loadUserByUsername(String username) {
        Account account = accounts.findByLoginIdentifier(Account.normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return User.withUsername(account.getLoginIdentifier())
                .password(account.getPasswordHash()).roles(account.getRole().name())
                .disabled(!account.isEnabled()).build();
    }
}
