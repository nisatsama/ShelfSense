package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.account.Account;
import com.nisatsama.shelfsense.account.AccountRepository;
import com.nisatsama.shelfsense.account.AccountRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Value("${shelfsense.security.jwt.secret}") String signingSecret;
    private Account account;
    private String loginIdentifier;
    private String password;

    @BeforeEach
    void createAccount() {
        loginIdentifier = "milestone-" + java.util.UUID.randomUUID() + "@example.com";
        password = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID();
        account = new Account();
        account.setLoginIdentifier("  " + loginIdentifier.toUpperCase(java.util.Locale.ROOT) + " ");
        account.setPasswordHash(encoder.encode(password));
        account.setRole(AccountRole.USER);
        account = accounts.saveAndFlush(account);
    }

    @Test
    void validLoginReturnsUsableJwtWithoutPasswordData() throws Exception {
        assertThat(account.getLoginIdentifier()).isEqualTo(loginIdentifier);
        assertThat(account.getPasswordHash()).startsWith("$2");
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginIdentifier\":\"" + loginIdentifier.toUpperCase(java.util.Locale.ROOT)
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty()).andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(password, account.getPasswordHash());
        String token = com.jayway.jsonpath.JsonPath.read(response, "$.accessToken");
        mvc.perform(get("/api/v1/inventory/products/1/locations/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void invalidCredentialsReturn401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginIdentifier\":\"" + loginIdentifier + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginIsPublicAndProtectedRoutesRejectMissingOrMalformedTokens() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginIdentifier\":\"missing@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/inventory/products/1/locations/1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/v1/inventory/products/1/locations/1").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTamperedWrongSignatureAndWrongIssuerTokensReturn401() throws Exception {
        String expired = token(loginIdentifier, "shelfsense", true, signingSecret);
        String valid = token(loginIdentifier, "shelfsense", false, signingSecret);
        String wrongIssuer = token(loginIdentifier, "other-issuer", false, signingSecret);
        String wrongSignature = token("milestone.user@example.com", "shelfsense", false, "different");
        String tampered = valid.substring(0, valid.length() - 2) + "aa";
        for (String value : new String[]{expired, tampered, wrongSignature, wrongIssuer}) {
            mvc.perform(get("/api/v1/inventory/products/1/locations/1").header("Authorization", "Bearer " + value))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void disabledAccountCannotUsePreviouslyIssuedJwt() throws Exception {
        String token = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginIdentifier\":\"" + loginIdentifier + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String jwt = com.jayway.jsonpath.JsonPath.read(token, "$.accessToken");
        account.setEnabled(false);
        accounts.saveAndFlush(account);
        mvc.perform(get("/api/v1/inventory/products/1/locations/1").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    private String token(String subject, String issuer, boolean expired, String secret) throws Exception {
        String payload = "{\"iss\":\"" + issuer + "\",\"sub\":\"" + subject + "\",\"iat\":"
                + (expired ? 1 : java.time.Instant.now().getEpochSecond()) + ",\"exp\":"
                + (expired ? 2 : java.time.Instant.now().plusSeconds(600).getEpochSecond()) + "}";
        java.util.Base64.Encoder enc = java.util.Base64.getUrlEncoder().withoutPadding();
        String head = enc.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String body = enc.encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] key = "different".equals(secret)
                ? java.util.Arrays.copyOf("different".getBytes(java.nio.charset.StandardCharsets.UTF_8), 32)
                : java.util.Base64.getDecoder().decode(secret);
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        return head + "." + body + "." + enc.encodeToString(mac.doFinal((head + "." + body).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
