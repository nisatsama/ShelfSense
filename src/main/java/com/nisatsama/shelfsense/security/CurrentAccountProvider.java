package com.nisatsama.shelfsense.security;

import com.nisatsama.shelfsense.account.Account;
import com.nisatsama.shelfsense.account.AccountRepository;
import com.nisatsama.shelfsense.account.AccountRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentAccountProvider {

    private final AccountRepository accountRepository;

    public CurrentAccountProvider(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Optional<Account> currentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        String loginIdentifier;
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            loginIdentifier = jwt.getSubject();
        } else {
            loginIdentifier = authentication.getName();
        }
        if (loginIdentifier == null || loginIdentifier.isBlank()) {
            return Optional.empty();
        }
        return accountRepository.findByLoginIdentifier(Account.normalize(loginIdentifier));
    }

    public Account requireCurrentAccount() {
        return currentAccount().orElseThrow(() ->
                new AccessDeniedException("Authenticated account context is required."));
    }

    public Optional<Long> currentAccountId() {
        return currentAccount().map(Account::getId);
    }

    public boolean hasAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    public boolean isAdmin(Account account) {
        return account != null && account.getRole() == AccountRole.ADMIN;
    }
}
