package com.github.ksewen.kelios.platform;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatusControllerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StatusController()).build();

    @Test
    void exposesOnlyTechnicalStatusContract() throws Exception {
        mvc.perform(get("/api/v1/status")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.application").value("KELIOS"))
            .andExpect(jsonPath("$.status").value("ready"));
    }

    @Test
    void hasNoBusinessOrAuthenticationEndpoints() throws Exception {
        mvc.perform(get("/api/v1/assets")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/auth/login")).andExpect(status().isNotFound());
    }
}
