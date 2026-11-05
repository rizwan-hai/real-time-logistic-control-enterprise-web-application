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
        mvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.apiVersion").value("v1"));
    }

    @Test void validationErrorsUseProblemDetailsAndCorrelationId() throws Exception {
        mvc.perform(post("/api/v1/test/validation").contentType("application/json").content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test void invalidArgumentsUseProblemDetails() throws Exception {
        mvc.perform(get("/api/v1/test/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void suppliedSafeCorrelationIdIsReturned() throws Exception {
        mvc.perform(get("/api/v1/system/status").header("X-Correlation-ID", "trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "trace-123"));
    }
}
