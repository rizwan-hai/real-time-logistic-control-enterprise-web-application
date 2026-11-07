package com.example.logistics.notifications;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationSubscriptionAuthorizationTest {
    @Test void onlyAllowsTheAuthenticatedUsersPrivateDestination() {
        String owner = "278f55c5-ec9e-4e31-b550-7d8091f3a5a1";
        assertThat(NotificationSubscriptionAuthorization.allows(owner, "/topic/notifications/" + owner)).isTrue();
        assertThat(NotificationSubscriptionAuthorization.allows(owner,
                "/topic/notifications/7fa70942-c7f8-4e49-9352-cbe4e87a1b50")).isFalse();
        assertThat(NotificationSubscriptionAuthorization.allows(owner, "/topic/notifications/")).isFalse();
        assertThat(NotificationSubscriptionAuthorization.allows(null, "/topic/notifications/" + owner)).isFalse();
    }
}
