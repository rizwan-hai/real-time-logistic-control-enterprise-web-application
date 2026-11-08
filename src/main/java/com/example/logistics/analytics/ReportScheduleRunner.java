package com.example.logistics.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ReportScheduleRunner {
    private static final Logger log = LoggerFactory.getLogger(ReportScheduleRunner.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ReportExportService exports;
    private final ReportExportRunner runner;

    public ReportScheduleRunner(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                ReportExportService exports, ReportExportRunner runner) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.exports = exports;
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${reports.schedule-poll-millis:60000}")
    public void runDueSchedules() {
        var due = jdbc.queryForList("""
                SELECT id,organization_id,requested_by,export_format,frequency,filters_json,next_run_at
                FROM report_schedule WHERE enabled=TRUE AND next_run_at<=? ORDER BY next_run_at LIMIT 50
                """, Timestamp.from(Instant.now()));
        for (var schedule : due) {
            UUID id = UUID.fromString((String) schedule.get("id"));
            Timestamp scheduledAt = (Timestamp) schedule.get("next_run_at");
            LocalDateTime next = nextRun(scheduledAt.toLocalDateTime(), (String) schedule.get("frequency"));
            int claimed = jdbc.update("""
                    UPDATE report_schedule SET next_run_at=?
                    WHERE id=? AND enabled=TRUE AND next_run_at=?
                    """, Timestamp.valueOf(next), id.toString(), scheduledAt);
            if (claimed == 0) continue;
            try {
                var saved = objectMapper.readValue((String) schedule.get("filters_json"), AnalyticsService.Filters.class);
                var dates = dateRange((String) schedule.get("frequency"), scheduledAt.toLocalDateTime().toLocalDate());
                var filters = new AnalyticsService.Filters(dates[0], dates[1], saved.customerId(), saved.warehouseId(),
                        saved.vehicleId(), saved.driverId());
                UUID organization = UUID.fromString((String) schedule.get("organization_id"));
                UUID actor = UUID.fromString((String) schedule.get("requested_by"));
                String format = (String) schedule.get("export_format");
                UUID exportId = exports.create(organization, actor, filters, format);
                runner.generate(exportId, organization, actor, filters, format);
            } catch (Exception exception) {
                log.error("Failed to process scheduled report {}", id, exception);
            }
        }
    }

    @Scheduled(fixedDelayString = "${reports.cleanup-poll-millis:86400000}")
    public void cleanupExpiredExports() {
        exports.cleanupExpired();
    }

    private static LocalDateTime nextRun(LocalDateTime current, String frequency) {
        return switch (frequency) {
            case "DAILY" -> current.plusDays(1);
            case "WEEKLY" -> current.plusWeeks(1);
            case "MONTHLY" -> current.plusMonths(1);
            default -> throw new IllegalArgumentException("Invalid report schedule frequency: " + frequency);
        };
    }

    private static LocalDate[] dateRange(String frequency, LocalDate scheduledDate) {
        return switch (frequency) {
            case "DAILY" -> new LocalDate[]{scheduledDate.minusDays(1), scheduledDate.minusDays(1)};
            case "WEEKLY" -> new LocalDate[]{scheduledDate.minusWeeks(1), scheduledDate.minusDays(1)};
            case "MONTHLY" -> {
                LocalDate previous = scheduledDate.withDayOfMonth(1).minusDays(1);
                yield new LocalDate[]{previous.withDayOfMonth(1), previous};
            }
            default -> throw new IllegalArgumentException("Invalid report schedule frequency: " + frequency);
        };
    }
}
