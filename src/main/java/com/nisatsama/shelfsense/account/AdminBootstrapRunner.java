package com.nisatsama.shelfsense.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapService bootstrapService;
    private String username;
    private String password;

    public AdminBootstrapRunner(AdminBootstrapService bootstrapService,
                                @org.springframework.beans.factory.annotation.Value("${SHELF_SENSE_BOOTSTRAP_ADMIN_USERNAME:}") String username,
                                @org.springframework.beans.factory.annotation.Value("${SHELF_SENSE_BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.bootstrapService = bootstrapService;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean hasUsername = StringUtils.hasText(username);
        boolean hasPassword = StringUtils.hasText(password);
        if (!hasUsername && !hasPassword) {
            log.info("Initial administrator bootstrap is not configured; skipped.");
            return;
        }
        if (!hasUsername || !hasPassword) {
            username = null;
            password = null;
            throw new IllegalStateException("Both initial administrator bootstrap variables must be supplied together");
        }

        AdminBootstrapService.BootstrapResult result;
        try {
            result = bootstrapService.provisionInitialAdmin(username, password);
        } finally {
            username = null;
            password = null;
        }
        if (result == AdminBootstrapService.BootstrapResult.CREATED) {
            log.info("Initial administrator bootstrap completed.");
        } else {
            log.info("Initial administrator bootstrap is already complete; no account was changed.");
        }
    }
}
