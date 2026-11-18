package com.nisatsama.shelfsense;

import com.jayway.jsonpath.JsonPath;
import com.nisatsama.shelfsense.account.Account;
import com.nisatsama.shelfsense.account.AccountRepository;
import com.nisatsama.shelfsense.account.AccountRole;
import com.nisatsama.shelfsense.inventory.Inventory;
import com.nisatsama.shelfsense.inventory.InventoryService;
import com.nisatsama.shelfsense.location.Location;
import com.nisatsama.shelfsense.location.LocationRepository;
import com.nisatsama.shelfsense.location.LocationType;
import com.nisatsama.shelfsense.product.Product;
import com.nisatsama.shelfsense.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthorizationAndOwnershipIntegrationTests {

    @Autowired MockMvc mvc;
    @Autowired AccountRepository accountRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ProductRepository productRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryService inventoryService;

    private String adminToken;
    private String userAToken;
    private String userBToken;
    private Account userA;
    private Inventory inventory;

    @BeforeEach
    void setUp() throws Exception {
        Account admin = createAccount(AccountRole.ADMIN, "AdminPass12345!");
        userA = createAccount(AccountRole.USER, "UserAPass12345!");
        Account userB = createAccount(AccountRole.USER, "UserBPass12345!");

        adminToken = login(admin.getLoginIdentifier(), "AdminPass12345!");
        userAToken = login(userA.getLoginIdentifier(), "UserAPass12345!");
        userBToken = login(userB.getLoginIdentifier(), "UserBPass12345!");

        String unique = UUID.randomUUID().toString();
        Product product = new Product();
        product.setSku("AUTH-SKU-" + unique);
        product.setName("Auth Test Product");
        product.setCategory("BEVERAGES");
        product.setUnitPrice(new BigDecimal("12.50"));
        product = productRepository.saveAndFlush(product);

        Location location = new Location();
        location.setCode("AUTH-LOC-" + unique);
        location.setName("Auth Test Store");
        location.setType(LocationType.STORE);
        location = locationRepository.saveAndFlush(location);

        inventory = inventoryService.create(product.getId(), location.getId(), 50);
    }

    @Test
    void ordinaryUserCannotCreateProductsOrAccessAdminAccountsEndpoint() throws Exception {
        String sku = "RBAC-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Milk\",\"category\":\"DAIRY\",\"unitPrice\":3.99}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"name\":\"Milk\",\"category\":\"DAIRY\",\"unitPrice\":3.99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(sku));
    }

    @Test
    void usersCannotReadReleaseOrConfirmAnotherUsersReservationButAdminCan() throws Exception {
        String createBody = String.format(
                "{\"productId\":%d,\"locationId\":%d,\"quantity\":5}",
                inventory.getProduct().getId(), inventory.getLocation().getId()
        );
        String createdJson = mvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(userA.getId()))
                .andReturn().getResponse().getContentAsString();

        Number reservationId = JsonPath.read(createdJson, "$.id");

        // Owner can view
        mvc.perform(get("/api/v1/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId.longValue()));

        // Another USER gets 403 on read, release, and confirm
        mvc.perform(get("/api/v1/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/reservations/" + reservationId + "/release")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/reservations/" + reservationId + "/confirm")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden());

        // User B listing does not include User A's reservation
        mvc.perform(get("/api/v1/reservations")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // Admin can view and confirm
        mvc.perform(put("/api/v1/reservations/" + reservationId + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void selfRegistrationCreatesStandardUserAndReturnsProfileViaMeEndpoint() throws Exception {
        String email = "reg-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginIdentifier\":\"" + email + "\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginIdentifier").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true));

        String token = login(email, "StrongPass123!");
        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginIdentifier").value(email))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void adminCanManageAccountsAndDisableUser() throws Exception {
        mvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mvc.perform(patch("/api/v1/accounts/" + userA.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isUnauthorized());
    }

    private Account createAccount(AccountRole role, String rawPassword) {
        Account account = new Account();
        account.setLoginIdentifier(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setRole(role);
        account.setEnabled(true);
        return accountRepository.saveAndFlush(account);
    }

    private String login(String identifier, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginIdentifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
