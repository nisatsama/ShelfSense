package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.common.GlobalExceptionHandler;
import com.nisatsama.shelfsense.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class OptimisticLockConflictHandlerTests {

    @Test
    void mapsObjectOptimisticLockingFailureToConflictApiError() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConflictController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        MvcResult result = mockMvc.perform(get("/test/optimistic-lock"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentType()).contains("application/json");

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody)
                .contains("\"status\":409")
                .contains("\"error\":\"CONFLICT\"")
                .contains("\"message\":\"The resource was modified by another request. Please retry.\"")
                .doesNotContain("ObjectOptimisticLockingFailureException", "SQLException", "stackTrace");
    }

    @RestController
    static class ConflictController {

        @GetMapping("/test/optimistic-lock")
        void optimisticLockConflict() {
            throw new ObjectOptimisticLockingFailureException(Inventory.class, 42L);
        }
    }
}
