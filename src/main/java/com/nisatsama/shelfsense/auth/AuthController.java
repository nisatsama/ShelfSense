package com.nisatsama.shelfsense.auth;

import com.nisatsama.shelfsense.auth.dto.LoginRequest;
import com.nisatsama.shelfsense.auth.dto.LoginResponse;
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
    public AuthController(AuthenticationManager authenticationManager, JwtTokenService tokens) {
        this.authenticationManager = authenticationManager; this.tokens = tokens;
    }
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.loginIdentifier(), request.password()));
        var issued = tokens.issue(authentication);
        return new LoginResponse(issued.value(), "Bearer", issued.expiresAt());
    }
}
