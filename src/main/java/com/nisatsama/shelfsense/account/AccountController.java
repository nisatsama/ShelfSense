package com.nisatsama.shelfsense.account;

import com.nisatsama.shelfsense.account.dto.AccountCreateRequest;
import com.nisatsama.shelfsense.account.dto.AccountResponse;
import com.nisatsama.shelfsense.account.dto.AccountRoleUpdateRequest;
import com.nisatsama.shelfsense.account.dto.AccountStatusUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> findAll() {
        return accountService.findAll().stream()
                .map(AccountResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public AccountResponse findById(@PathVariable Long id) {
        return AccountResponse.from(accountService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody AccountCreateRequest request) {
        Account created = accountService.createAccount(
                request.loginIdentifier(),
                request.password(),
                request.role()
        );
        return AccountResponse.from(created);
    }

    @PatchMapping("/{id}/status")
    public AccountResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AccountStatusUpdateRequest request
    ) {
        return AccountResponse.from(accountService.updateEnabled(id, request.enabled()));
    }

    @PatchMapping("/{id}/role")
    public AccountResponse updateRole(
            @PathVariable Long id,
            @Valid @RequestBody AccountRoleUpdateRequest request
    ) {
        return AccountResponse.from(accountService.updateRole(id, request.role()));
    }
}
