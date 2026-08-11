package com.translatelab.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "OPENAPI_DOCS_ENABLED=true",
        "OPENAPI_PUBLIC_ACCESS=false"
})
@AutoConfigureMockMvc
class OpenApiProtectedIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void shouldProtectEnabledDocumentationByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v3/api-docs").with(jwt()))
                .andExpect(status().isOk());
    }
}
