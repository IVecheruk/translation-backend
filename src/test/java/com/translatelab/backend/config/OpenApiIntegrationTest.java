package com.translatelab.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "OPENAPI_DOCS_ENABLED=true",
        "OPENAPI_PUBLIC_ACCESS=true"
})
@AutoConfigureMockMvc
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeOpenApiDocumentWithJwtSecurityContract()
            throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.info.title").value(
                        "Translation API"
                ))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath(
                        "$.components.securitySchemes.bearerAuth.type"
                ).value("http"))
                .andExpect(jsonPath(
                        "$.components.securitySchemes.bearerAuth.scheme"
                ).value("bearer"))
                .andExpect(jsonPath(
                        "$.components.securitySchemes"
                                + ".bearerAuth.bearerFormat"
                ).value("JWT"))
                .andExpect(jsonPath(
                        "$.paths['/api/documents/upload']"
                                + ".post.security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/profile'].get"
                                + ".security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/profile/avatar'].get"
                                + ".security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/login'].post.security"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/register'].post.security"
                ).doesNotExist());
    }

    @Test
    void shouldExposeSwaggerUiWithoutAuthentication()
            throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_HTML
                ));
    }
}
