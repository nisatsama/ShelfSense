package com.nisatsama.shelfsense.account;

import com.nisatsama.shelfsense.common.ConflictException;
import com.nisatsama.shelfsense.common.ResourceNotFoundException;
import com.nisatsama.shelfsense.security.CurrentAccountProvider;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentAccountProvider currentAccountProvider;

    public AccountService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            CurrentAccountProvider currentAccountProvider
    ) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentAccountProvider = currentAccountProvider;
    }

    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return accountRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional(readOnly = true)
    public Account findById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }

    public Account registerUser(String loginIdentifier, String rawPassword) {
        return createAccount(loginIdentifier, rawPassword, AccountRole.USER);
    }

    public Account createAccount(String loginIdentifier, String rawPassword, AccountRole role) {
        String normalized = Account.normalize(loginIdentifier);
        if (normalized == null || normalized.isBlank() || normalized.length() > 254) {
            throw new IllegalArgumentException("Login identifier must be between 1 and 254 characters.");
        }
        if (rawPassword == null || rawPassword.length() < 8
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be between 8 characters and 72 UTF-8 bytes.");
        }
        if (accountRepository.findByLoginIdentifier(normalized).isPresent()) {
            throw new ConflictException("An account with this login identifier already exists.");
        }
        Account account = new Account();
        account.setLoginIdentifier(normalized);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setRole(role != null ? role : AccountRole.USER);
        account.setEnabled(true);
        return accountRepository.save(account);
    }

    public Account updateEnabled(Long id, boolean enabled) {
        Account target = findById(id);
        if (!enabled) {
            currentAccountProvider.currentAccountId().ifPresent(currentId -> {
                if (currentId.equals(target.getId())) {
                    throw new IllegalArgumentException("Administrators cannot disable their own account.");
                }
            });
        }
        target.setEnabled(enabled);
        return accountRepository.save(target);
    }

    public Account updateRole(Long id, AccountRole role) {
        Account target = findById(id);
        if (role != AccountRole.ADMIN) {
            currentAccountProvider.currentAccountId().ifPresent(currentId -> {
                if (currentId.equals(target.getId())) {
                    throw new IllegalArgumentException("Administrators cannot demote their own account.");
                }
            });
        }
        target.setRole(role);
        return accountRepository.save(target);
    }
}
