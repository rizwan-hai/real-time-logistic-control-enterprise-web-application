package com.example.logistics.notifications;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class NotificationTemplateRenderer {
    private final JdbcTemplate db;

    public NotificationTemplateRenderer(JdbcTemplate db) { this.db = db; }

    public Rendered render(String eventType, String requestedLocale, Map<String, String> values) {
        String locale = "fa".equalsIgnoreCase(requestedLocale) ? "fa" : "en";
        var templates = db.query("SELECT title_template,body_template FROM notification_template WHERE event_type=? AND locale=?",
                (rs, row) -> new Rendered(rs.getString(1), rs.getString(2)), eventType, locale);
        if (templates.isEmpty() && !"en".equals(locale)) {
            templates = db.query("SELECT title_template,body_template FROM notification_template WHERE event_type=? AND locale='en'",
                    (rs, row) -> new Rendered(rs.getString(1), rs.getString(2)), eventType);
        }
        Rendered template = templates.isEmpty()
                ? new Rendered(locale.equals("fa") ? "اعلان جدید" : "New notification",
                    locale.equals("fa") ? "رویداد جدیدی ثبت شده است." : "A new business event has been recorded.")
                : templates.get(0);
        return new Rendered(apply(template.title(), values), apply(template.body(), values));
    }

    private String apply(String template, Map<String, String> values) {
        String rendered = template;
        for (String key : new String[]{"status", "reference"}) {
            String value = values.get(key);
            if (value != null) {
                String safeValue = value.replaceAll("[\\p{Cntrl}]", "");
                rendered = rendered.replace("{" + key + "}", safeValue.substring(0, Math.min(safeValue.length(), 80)));
            }
        }
        return rendered.replaceAll("\\{[a-zA-Z0-9_]+}", "");
    }

    public record Rendered(String title, String body) {}
}
