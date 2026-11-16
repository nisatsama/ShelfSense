package com.nisatsama.shelfsense.auth;

import java.time.Instant;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration lifetime;
    public JwtTokenService(JwtEncoder encoder, @Value("${shelfsense.security.jwt.issuer}") String issuer,
                           @Value("${shelfsense.security.jwt.expiration}") Duration lifetime) {
        this.encoder = encoder; this.issuer = issuer; this.lifetime = lifetime;
    }
    public IssuedToken issue(Authentication authentication) {
        Instant now = Instant.now();
        Instant expires = now.plus(lifetime);
        String role = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).findFirst().orElse("USER");
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(authentication.getName())
                .issuedAt(now).expiresAt(expires).claim("role", role).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expires);
    }
    public record IssuedToken(String value, Instant expiresAt) { }
}
