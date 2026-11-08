package com.example.logistics.analytics;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ReportExportRunner {
    private final ReportExportService exports;

    public ReportExportRunner(ReportExportService exports) {
        this.exports = exports;
    }

    @Async("reportExecutor")
    public void generate(UUID id, UUID organizationId, UUID actorId, AnalyticsService.Filters filters, String format) {
        exports.generate(id, organizationId, actorId, filters, format);
    }
}
