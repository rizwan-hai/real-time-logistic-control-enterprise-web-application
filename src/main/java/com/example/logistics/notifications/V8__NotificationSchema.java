package com.example.logistics.notifications;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.Statement;
import java.util.List;

public class V8__NotificationSchema extends BaseJavaMigration {
    private static final List<String> EVENTS = List.of(
            "OrderStatusChanged", "ShipmentAssigned", "TripDispatched", "EtaChanged",
            "DeliveryDelay", "GpsAlert", "VehicleIdle", "GeofenceBreach", "DocumentExpiry",
            "LowInventory", "PodCompleted", "InvoiceIssued", "InvoiceOverdue", "PaymentReceived");

    @Override public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("CREATE TABLE notification (id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, user_id CHAR(36) NOT NULL, source_outbox_id CHAR(36) NOT NULL, event_fingerprint VARCHAR(64) NOT NULL, event_type VARCHAR(100) NOT NULL, title VARCHAR(200) NOT NULL, body VARCHAR(1000) NOT NULL, locale VARCHAR(5) NOT NULL, resource_type VARCHAR(50) NOT NULL, resource_id CHAR(36) NOT NULL, deep_link VARCHAR(300) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), read_at TIMESTAMP(6) NULL, CONSTRAINT uk_notification_event_user UNIQUE (source_outbox_id,user_id), INDEX ix_notification_user_time (organization_id,user_id,created_at), INDEX ix_notification_user_unread (organization_id,user_id,read_at,created_at), INDEX ix_notification_fingerprint (event_fingerprint))");
            statement.execute("CREATE TABLE notification_event_receipt (id CHAR(36) NOT NULL PRIMARY KEY, event_fingerprint CHAR(64) NOT NULL, outbox_event_id CHAR(36) NOT NULL, processed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), CONSTRAINT uk_notification_event_fingerprint UNIQUE (event_fingerprint), CONSTRAINT uk_notification_outbox UNIQUE (outbox_event_id))");
            statement.execute("CREATE TABLE notification_preference (id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, user_id CHAR(36) NOT NULL, in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE, email_enabled BOOLEAN NOT NULL DEFAULT FALSE, sms_enabled BOOLEAN NOT NULL DEFAULT FALSE, web_push_enabled BOOLEAN NOT NULL DEFAULT FALSE, locale VARCHAR(5) NOT NULL DEFAULT 'en', updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), CONSTRAINT uk_notification_preference_user UNIQUE (organization_id,user_id))");
            statement.execute("CREATE TABLE notification_delivery_log (id CHAR(36) NOT NULL PRIMARY KEY, notification_id CHAR(36) NOT NULL, channel VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL, attempts INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP(6) NULL, delivered_at TIMESTAMP(6) NULL, last_error VARCHAR(1000) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), CONSTRAINT uk_notification_delivery_channel UNIQUE (notification_id,channel), INDEX ix_notification_delivery_retry (status,next_attempt_at))");
            statement.execute("CREATE TABLE notification_template (id CHAR(36) NOT NULL PRIMARY KEY, event_type VARCHAR(100) NOT NULL, locale VARCHAR(5) NOT NULL, title_template VARCHAR(200) NOT NULL, body_template VARCHAR(1000) NOT NULL, updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), CONSTRAINT uk_notification_template_event_locale UNIQUE (event_type,locale))");
            statement.execute("CREATE TABLE notification_eta_snapshot (trip_id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, estimated_arrival TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6))");
            for (String event : EVENTS) {
                String[] english = template(event, false);
                String[] persian = template(event, true);
                insertTemplate(statement, event, "en", english[0], english[1]);
                insertTemplate(statement, event, "fa", persian[0], persian[1]);
            }
        }
    }

    private static void insertTemplate(Statement statement, String event, String locale, String title, String body) throws Exception {
        statement.executeUpdate("INSERT INTO notification_template(id,event_type,locale,title_template,body_template) VALUES ('"
                + java.util.UUID.randomUUID() + "','" + event + "','" + locale + "','" + title.replace("'", "''")
                + "','" + body.replace("'", "''") + "')");
    }

    private static String[] template(String event, boolean persian) {
        String title = switch (event) {
            case "OrderStatusChanged" -> persian ? "به‌روزرسانی وضعیت سفارش" : "Order status updated";
            case "ShipmentAssigned" -> persian ? "محموله تخصیص یافت" : "Shipment assigned";
            case "TripDispatched" -> persian ? "سفر اعزام شد" : "Trip dispatched";
            case "EtaChanged" -> persian ? "زمان تخمینی به‌روز شد" : "Estimated arrival updated";
            case "DeliveryDelay" -> persian ? "احتمال تأخیر در تحویل" : "Delivery delay alert";
            case "GpsAlert" -> persian ? "هشدار ردیابی" : "Tracking alert";
            case "VehicleIdle" -> persian ? "خودرو متوقف است" : "Vehicle idle alert";
            case "GeofenceBreach" -> persian ? "هشدار محدوده جغرافیایی" : "Geofence alert";
            case "DocumentExpiry" -> persian ? "مدرک در آستانه انقضا است" : "Document expiry reminder";
            case "LowInventory" -> persian ? "موجودی کالا کم است" : "Low inventory alert";
            case "PodCompleted" -> persian ? "مدرک تحویل ثبت شد" : "Proof of delivery completed";
            case "InvoiceIssued" -> persian ? "صورتحساب صادر شد" : "Invoice issued";
            case "InvoiceOverdue" -> persian ? "سررسید صورتحساب گذشته است" : "Invoice overdue";
            case "PaymentReceived" -> persian ? "پرداخت دریافت شد" : "Payment received";
            default -> persian ? "اعلان جدید" : "New notification";
        };
        String subject = switch (event) {
            case "OrderStatusChanged" -> persian ? "وضعیت یک سفارش تغییر کرده است." : "An order status has changed.";
            case "ShipmentAssigned" -> persian ? "یک محموله به تیم شما تخصیص یافته است." : "A shipment has been assigned to your team.";
            case "TripDispatched" -> persian ? "یک سفر برای اجرا اعزام شده است." : "A trip has been dispatched.";
            case "EtaChanged" -> persian ? "زمان تخمینی رسیدن به‌روزرسانی شده است." : "An estimated arrival time has changed.";
            case "DeliveryDelay" -> persian ? "تحویل یک محموله ممکن است با تأخیر انجام شود." : "A shipment delivery may be delayed.";
            case "GpsAlert" -> persian ? "یک هشدار ردیابی نیازمند بررسی است." : "A tracking alert needs attention.";
            case "VehicleIdle" -> persian ? "یک خودرو بیش از حد معمول متوقف مانده است." : "A vehicle has been idle beyond its threshold.";
            case "GeofenceBreach" -> persian ? "یک خودرو وارد محدوده جغرافیایی شده یا از آن خارج شده است." : "A vehicle crossed a geofence boundary.";
            case "DocumentExpiry" -> persian ? "یک مدرک سازمانی به‌زودی منقضی می‌شود." : "An organizational document is nearing expiry.";
            case "LowInventory" -> persian ? "موجودی یک کالا به آستانه هشدار رسیده است." : "An inventory item has reached its alert threshold.";
            case "PodCompleted" -> persian ? "مدرک تحویل یک محموله تکمیل شده است." : "Proof of delivery has been completed.";
            case "InvoiceIssued" -> persian ? "یک صورتحساب جدید صادر شده است." : "A new invoice has been issued.";
            case "InvoiceOverdue" -> persian ? "یک صورتحساب پرداخت‌نشده از موعد گذشته است." : "An unpaid invoice is past due.";
            case "PaymentReceived" -> persian ? "پرداخت یک صورتحساب ثبت شده است." : "A payment has been recorded.";
            default -> persian ? "رویداد جدیدی ثبت شده است." : "A new business event has been recorded.";
        };
        return new String[]{title, subject};
    }
}
