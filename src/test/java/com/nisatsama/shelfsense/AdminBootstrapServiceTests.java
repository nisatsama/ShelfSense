package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.account.Account;
import com.nisatsama.shelfsense.account.AccountRepository;
import com.nisatsama.shelfsense.account.AccountRole;
import com.nisatsama.shelfsense.account.AdminBootstrapRunner;
import com.nisatsama.shelfsense.account.AdminBootstrapService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTests {
    @Mock AccountRepository accounts;
    @Mock JdbcTemplate jdbcTemplate;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void createsOneNormalizedEnabledAdminAndDoesNotRepeatAfterCompletion() {
        String username = "  Bootstrap-" + UUID.randomUUID() + "@Example.COM  ";
        String password = UUID.randomUUID().toString() + UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false, true);
        when(accounts.existsByRole(AccountRole.ADMIN)).thenReturn(false);
        when(accounts.findByLoginIdentifier(Account.normalize(username))).thenReturn(Optional.empty());
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AdminBootstrapService service = new AdminBootstrapService(accounts, encoder, jdbcTemplate);

        assertThat(service.provisionInitialAdmin(username, password))
                .isEqualTo(AdminBootstrapService.BootstrapResult.CREATED);
        assertThat(service.provisionInitialAdmin(username, password))
                .isEqualTo(AdminBootstrapService.BootstrapResult.ALREADY_COMPLETED);

        ArgumentCaptor<Account> created = ArgumentCaptor.forClass(Account.class);
        verify(accounts, times(1)).saveAndFlush(created.capture());
        assertThat(created.getValue().getLoginIdentifier()).isEqualTo(Account.normalize(username));
        assertThat(created.getValue().getRole()).isEqualTo(AccountRole.ADMIN);
        assertThat(created.getValue().isEnabled()).isTrue();
        assertThat(created.getValue().getPasswordHash()).startsWith("$2");
        assertThat(encoder.matches(password, created.getValue().getPasswordHash())).isTrue();
        verify(jdbcTemplate, times(1)).update(contains("SET completed = TRUE"));
    }

    @Test
    void existingAdministratorCompletesBootstrapWithoutChangingAccounts() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
        when(accounts.existsByRole(AccountRole.ADMIN)).thenReturn(true);
        AdminBootstrapService service = new AdminBootstrapService(accounts, encoder, jdbcTemplate);

        assertThat(service.provisionInitialAdmin("unused-" + UUID.randomUUID(), UUID.randomUUID().toString()))
                .isEqualTo(AdminBootstrapService.BootstrapResult.ALREADY_COMPLETED);

        verify(accounts, never()).saveAndFlush(any(Account.class));
        verify(accounts, never()).findByLoginIdentifier(anyString());
        verify(jdbcTemplate).update(contains("SET completed = TRUE"));
    }

    @Test
    void occupiedNonAdminLoginIsNotModified() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
        when(accounts.existsByRole(AccountRole.ADMIN)).thenReturn(false);
        String username = "occupied-" + UUID.randomUUID();
        when(accounts.findByLoginIdentifier(username)).thenReturn(Optional.of(new Account()));
        AdminBootstrapService service = new AdminBootstrapService(accounts, encoder, jdbcTemplate);

        assertThatThrownBy(() -> service.provisionInitialAdmin(username, UUID.randomUUID().toString()))
                .isInstanceOf(IllegalStateException.class);
        verify(accounts, never()).saveAndFlush(any(Account.class));
    }

    @Test
    void missingBootstrapConfigurationIsSkippedAndPartialConfigurationFailsClosed() throws Exception {
        AdminBootstrapService service = mock(AdminBootstrapService.class);
        AdminBootstrapRunner missing = new AdminBootstrapRunner(service, "", "");
        missing.run(new DefaultApplicationArguments(new String[0]));
        verifyNoInteractions(service);

        AdminBootstrapRunner partial = new AdminBootstrapRunner(service,
                "partial-" + UUID.randomUUID(), "");
        assertThatThrownBy(() -> partial.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(service);
    }
}
