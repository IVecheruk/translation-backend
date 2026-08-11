package com.translatelab.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.prometheus.metrics.export.enabled=true"
})
@AutoConfigureMockMvc
class OperationalEndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeSeparateLivenessAndReadinessProbes() throws Exception {
        mockMvc.perform(get("/livez"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UP")));

        mockMvc.perform(get("/readyz"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UP")));
    }

    @Test
    void shouldExportPrometheusMetricsWhenExplicitlyEnabled()
            throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "jvm_memory_used_bytes"
                )));
    }
}
