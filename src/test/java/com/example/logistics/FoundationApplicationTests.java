package com.example.logistics;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.logistics.notifications.NotificationService;
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
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService notifications;

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

    @Test void consumesAndDeduplicatesBusinessEventsUsingTheUsersPreferredLocale() {
        var fixture = notificationFixture(true, false);
        String eventId = java.util.UUID.randomUUID().toString();
        String aggregateId = java.util.UUID.randomUUID().toString();
        String payload = "{\"to\":\"IN_TRANSIT\",\"reason\":\"private address must not be exposed\"}";
        addNotificationEvent(eventId, fixture.organizationId(), aggregateId, "OrderStatusChanged", "Order", payload);
        addNotificationEvent(java.util.UUID.randomUUID().toString(), fixture.organizationId(), aggregateId,
                "OrderStatusChanged", "Order", payload);

        notifications.consumeOutbox();

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE organization_id=? AND user_id=?",
                Integer.class, fixture.organizationId(), fixture.userId());
        assertThat(count).isEqualTo(1);
        var notification = jdbc.queryForMap("SELECT title,body,locale FROM notification WHERE organization_id=? AND user_id=?",
                fixture.organizationId(), fixture.userId());
        assertThat(notification.get("title")).isEqualTo("به‌روزرسانی وضعیت سفارش");
        assertThat(notification.get("locale")).isEqualTo("fa");
        assertThat(notification.get("body").toString()).doesNotContain("private address");
        assertThat(jdbc.queryForList("SELECT channel FROM notification_delivery_log WHERE notification_id IN "
                        + "(SELECT id FROM notification WHERE organization_id=? AND user_id=?)", String.class,
                fixture.organizationId(), fixture.userId())).containsExactly("EMAIL");
    }

    @Test void retriesFailedProviderDeliveryAndMovesItToDeadLetter() {
        var fixture = notificationFixture(true, true);
        String eventId = java.util.UUID.randomUUID().toString();
        String aggregateId = java.util.UUID.randomUUID().toString();
        addNotificationEvent(eventId, fixture.organizationId(), aggregateId, "InvoiceIssued", "Invoice", "{}");
        notifications.consumeOutbox();

        String notificationId = jdbc.queryForObject("SELECT id FROM notification WHERE organization_id=? AND user_id=?",
                String.class, fixture.organizationId(), fixture.userId());
        notifications.deliverPending();
        assertThat(jdbc.queryForObject("SELECT status FROM notification_delivery_log WHERE notification_id=?",
                String.class, notificationId)).isEqualTo("RETRY");

        for (int attempt = 2; attempt <= 5; attempt++) {
            jdbc.update("UPDATE notification_delivery_log SET next_attempt_at=TIMESTAMPADD(SECOND,-10,CURRENT_TIMESTAMP(6)) WHERE notification_id=?",
                    notificationId);
            notifications.deliverPending();
        }

        var delivery = jdbc.queryForMap("SELECT status,attempts FROM notification_delivery_log WHERE notification_id=?",
                notificationId);
        assertThat(delivery.get("status")).isEqualTo("DEAD_LETTER");
        assertThat(delivery.get("attempts")).isEqualTo(5);
    }

    @Test void notificationEndpointsReturnOnlyTheAuthenticatedUsersScope() throws Exception {
        var organizationId = java.util.UUID.randomUUID().toString();
        var userId = java.util.UUID.randomUUID().toString();
        mvc.perform(get("/api/v1/notifications").with(jwt().jwt(token -> token.subject(userId)
                        .claim("organizationId", organizationId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/notification-preferences").with(jwt().jwt(token -> token.subject(userId)
                        .claim("organizationId", organizationId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inApp").value(true))
                .andExpect(jsonPath("$.data.locale").value("en"));
    }

    private NotificationFixture notificationFixture(boolean email, boolean noEmailAddress) {
        String organizationId = java.util.UUID.randomUUID().toString();
        String userId = java.util.UUID.randomUUID().toString();
        String emailAddress = noEmailAddress ? null : "notification-" + userId + "@example.test";
        String phone = noEmailAddress ? "+1555" + userId.replace("-", "").substring(0, 7) : null;
        jdbc.update("""
                INSERT INTO user_account(id,organization_id,email_normalized,phone_normalized,password_hash,active)
                VALUES(?,?,?,?,?,TRUE)
                """, userId, organizationId, emailAddress, phone, "test-password-hash");
        notifications.updatePreferences(java.util.UUID.fromString(organizationId), java.util.UUID.fromString(userId),
                new NotificationService.PreferenceUpdate(false, email, false, false, "fa"));
        return new NotificationFixture(organizationId, userId);
    }

    private void addNotificationEvent(String id, String organizationId, String aggregateId,
                                      String eventType, String aggregateType, String payload) {
        jdbc.update("""
                INSERT INTO outbox_event(id,organization_id,event_type,schema_version,aggregate_type,aggregate_id,payload)
                VALUES(?,?,?,1,?,?,?)
                """, id, organizationId, eventType, aggregateType, aggregateId, payload);
    }

    private record NotificationFixture(String organizationId, String userId) {}

    @Test void csrfProtectsCookieAuthenticatedUnsafeRequests() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").cookie(new jakarta.servlet.http.Cookie("LOGISTICS_REFRESH", "opaque")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new jakarta.servlet.http.Cookie("LOGISTICS_REFRESH", "opaque")))
                .andExpect(status().isUnauthorized());
    }
}
