package com.nisatsama.shelfsense.auth;

import com.nisatsama.shelfsense.account.Account;
import com.nisatsama.shelfsense.account.AccountService;
import com.nisatsama.shelfsense.account.dto.AccountResponse;
import com.nisatsama.shelfsense.auth.dto.LoginRequest;
import com.nisatsama.shelfsense.auth.dto.LoginResponse;
import com.nisatsama.shelfsense.auth.dto.RegisterRequest;
import com.nisatsama.shelfsense.security.CurrentAccountProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokens;
    private final AccountService accountService;
    private final CurrentAccountProvider currentAccountProvider;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenService tokens,
            AccountService accountService,
            CurrentAccountProvider currentAccountProvider
    ) {
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
        this.accountService = accountService;
        this.currentAccountProvider = currentAccountProvider;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.loginIdentifier(), request.password()));
        var issued = tokens.issue(authentication);
        return new LoginResponse(issued.value(), "Bearer", issued.expiresAt());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@Valid @RequestBody RegisterRequest request) {
        Account created = accountService.registerUser(request.loginIdentifier(), request.password());
        return AccountResponse.from(created);
    }

    @GetMapping("/me")
    public AccountResponse me() {
        return AccountResponse.from(currentAccountProvider.requireCurrentAccount());
    }
}
