package com.nisatsama.shelfsense.security;

import com.nisatsama.shelfsense.account.AccountRepository;
import tools.jackson.databind.ObjectMapper;
import com.nisatsama.shelfsense.common.ApiError;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean SecretKey jwtSecretKey(@Value("${shelfsense.security.jwt.secret}") String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank())
            throw new IllegalStateException("SHELF_SENSE_JWT_SECRET must be configured with a stable Base64 key");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encodedSecret); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("JWT secret must be Base64 encoded", ex); }
        if (bytes.length < 32) throw new IllegalStateException("JWT secret must contain at least 256 bits");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean JwtDecoder jwtDecoder(SecretKey key, @Value("${shelfsense.security.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaults));
        return decoder;
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder,
                                                   AccountRepository accounts, ObjectMapper mapper) throws Exception {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(authorities);
        http.csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/v1/auth/login").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .authenticationEntryPoint((request, response, exception) -> writeError(mapper, response, 401, "UNAUTHORIZED", "Authentication is required or the supplied token is invalid.")))
            .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, exception) -> writeError(mapper, response, 401, "UNAUTHORIZED", "Authentication is required or the supplied token is invalid."))
                    .accessDeniedHandler((request, response, exception) -> writeError(mapper, response, 403, "FORBIDDEN", "You are not allowed to access this resource.")))
            .addFilterAfter(new EnabledAccountFilter(accounts, mapper), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(ObjectMapper mapper, HttpServletResponse response, int status,
                                   String error, String message) throws IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError(status, error, message));
    }

    static class EnabledAccountFilter extends OncePerRequestFilter {
        private final AccountRepository accounts; private final ObjectMapper mapper;
        EnabledAccountFilter(AccountRepository accounts, ObjectMapper mapper) { this.accounts = accounts; this.mapper = mapper; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof Jwt jwt) {
                boolean enabled = accounts.findByLoginIdentifier(jwt.getSubject()).map(a -> a.isEnabled()).orElse(false);
                if (!enabled) {
                    SecurityContextHolder.clearContext();
                    writeError(mapper, response, HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", "The account is disabled or no longer exists.");
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }
}
