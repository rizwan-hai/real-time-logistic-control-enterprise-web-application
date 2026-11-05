package com.example.logistics;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired MinioClient minioClient;
    @Autowired RedisConnectionFactory redisConnectionFactory;

    @Test void applicationContextLoadsWithExternalClientConfigurations() {
        assertThat(minioClient).isNotNull();
        assertThat(redisConnectionFactory).isNotNull();
    }

    @Test void versionedStatusUsesSuccessEnvelope() throws Exception {
        mvc.perform(get("/api/v1/system/status").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.apiVersion").value("v1"));
    }

    @Test void validationErrorsUseProblemDetailsAndCorrelationId() throws Exception {
        mvc.perform(post("/api/v1/test/validation").with(jwt()).with(csrf()).contentType("application/json").content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test void invalidArgumentsUseProblemDetails() throws Exception {
        mvc.perform(get("/api/v1/test/invalid").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void suppliedSafeCorrelationIdIsReturned() throws Exception {
        mvc.perform(get("/api/v1/system/status").with(jwt()).header("X-Correlation-ID", "trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "trace-123"));
    }

    @Test void csrfProtectsCookieAuthenticatedUnsafeRequests() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").cookie(new jakarta.servlet.http.Cookie("LOGISTICS_REFRESH", "opaque")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new jakarta.servlet.http.Cookie("LOGISTICS_REFRESH", "opaque")))
                .andExpect(status().isUnauthorized());
    }
}
