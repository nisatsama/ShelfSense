package com.nisatsama.shelfsense.account;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_identifier", nullable = false, unique = true, length = 254)
    private String loginIdentifier;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountRole role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void createTimestamps() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate
    void updateTimestamp() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getLoginIdentifier() { return loginIdentifier; }
    public void setLoginIdentifier(String value) { loginIdentifier = normalize(value); }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { passwordHash = value; }
    public AccountRole getRole() { return role; }
    public void setRole(AccountRole value) { role = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static String normalize(String value) {
        if (value == null) return null;
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
