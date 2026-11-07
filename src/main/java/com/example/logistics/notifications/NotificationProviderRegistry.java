package com.example.logistics.notifications;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationProviderRegistry {
    private final Map<String, NotificationChannelProvider> providers;

    public NotificationProviderRegistry(java.util.List<NotificationChannelProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                NotificationChannelProvider::channel, Function.identity()));
    }

    public NotificationChannelProvider provider(String channel) {
        NotificationChannelProvider provider = providers.get(channel);
        if (provider == null) throw new IllegalArgumentException("No notification provider configured for " + channel);
        return provider;
    }
}
