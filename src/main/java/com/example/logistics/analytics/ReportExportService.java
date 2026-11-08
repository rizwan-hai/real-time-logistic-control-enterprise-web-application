package com.example.logistics.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReportExportService {
    private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);
    private static final List<String> HEADERS = List.of("Shipment", "Status", "Order", "Customer", "Stop",
            "Stop status", "Scheduled at", "Completed at", "Failed attempts");
    private final JdbcTemplate jdbc;
    private final AnalyticsService analytics;
    private final ObjectMapper json;
    private final Path directory = Paths.get(System.getProperty("java.io.tmpdir"), "logistics-report-exports")
            .toAbsolutePath().normalize();

    public ReportExportService(JdbcTemplate jdbc, AnalyticsService analytics, ObjectMapper json) {
        this.jdbc = jdbc;
        this.analytics = analytics;
        this.json = json;
    }

    @Transactional
    public UUID create(UUID organizationId, UUID actorId, AnalyticsService.Filters filters, String format) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO report_export_job(id,organization_id,requested_by,report_type,export_format,status,filters_json)
                    VALUES(?,?,?,'DELIVERY',?,'QUEUED',?)
                    """, id.toString(), organizationId.toString(), actorId.toString(), format, json.writeValueAsString(filters));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist report export job", exception);
        }
        return id;
    }

    public void generate(UUID id, UUID organizationId, UUID actorId, AnalyticsService.Filters filters, String format) {
        Path file = directory.resolve(id + "." + format.toLowerCase(java.util.Locale.ROOT)).normalize();
        try {
            Files.createDirectories(directory);
            jdbc.update("UPDATE report_export_job SET status='RUNNING',started_at=? WHERE id=? AND status='QUEUED'",
                    Timestamp.from(Instant.now()), id.toString());
            long rows = switch (format) {
                case "CSV" -> writeCsv(file, organizationId, filters);
                case "XLSX" -> writeXlsx(file, organizationId, filters);
                case "PDF" -> writePdf(file, organizationId, filters);
                default -> throw new IllegalArgumentException("Unsupported export format");
            };
            jdbc.update("""
                    UPDATE report_export_job SET status='COMPLETED',storage_key=?,row_count=?,completed_at=?,
                        expires_at=? WHERE id=? AND organization_id=?
                    """, file.getFileName().toString(), rows, Timestamp.from(Instant.now()),
                    Timestamp.from(Instant.now().plusSeconds(7 * 24 * 60 * 60)), id.toString(), organizationId.toString());
        } catch (Exception exception) {
            log.error("Report export {} failed", id, exception);
            try {
                Files.deleteIfExists(file);
            } catch (java.io.IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            jdbc.update("UPDATE report_export_job SET status='FAILED',error_message=?,completed_at=? WHERE id=? AND organization_id=?",
                    message.substring(0, Math.min(500, message.length())), Timestamp.from(Instant.now()), id.toString(), organizationId.toString());
        }
    }

    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> jobs(UUID organizationId, UUID actorId) {
        return jdbc.queryForList("""
                SELECT id,report_type reportType,export_format exportFormat,status,row_count rowCount,
                       error_message errorMessage,created_at createdAt,started_at startedAt,completed_at completedAt,
                       expires_at expiresAt,requested_by requestedBy
                FROM report_export_job WHERE organization_id=? AND requested_by=? ORDER BY created_at DESC
                """, organizationId.toString(), actorId.toString());
    }

    @Transactional(readOnly = true)
    public Path downloadable(UUID organizationId, UUID actorId, UUID jobId, boolean downloadAny) {
        var jobs = jdbc.queryForList("""
                SELECT storage_key,status,expires_at,requested_by FROM report_export_job
                WHERE id=? AND organization_id=?
                """, jobId.toString(), organizationId.toString());
        if (jobs.isEmpty() || (!downloadAny && !actorId.toString().equals(jobs.get(0).get("requested_by")))) {
            throw new com.example.logistics.shared.api.ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "REPORT_EXPORT_NOT_FOUND", "Report export not found");
        }
        if ("EXPIRED".equals(jobs.get(0).get("status"))
                || jobs.get(0).get("expires_at") instanceof Timestamp expiry && expiry.toInstant().isBefore(Instant.now())) {
            throw new com.example.logistics.shared.api.ApiException(
                    org.springframework.http.HttpStatus.GONE, "REPORT_EXPORT_EXPIRED", "Report export is no longer available");
        }
        if (!"COMPLETED".equals(jobs.get(0).get("status"))) {
            throw new com.example.logistics.shared.api.ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "REPORT_EXPORT_NOT_FOUND", "Report export not found");
        }
        if (jobs.get(0).get("expires_at") == null) {
            throw new com.example.logistics.shared.api.ApiException(
                    org.springframework.http.HttpStatus.GONE, "REPORT_EXPORT_EXPIRED", "Report export is no longer available");
        }
        String key = (String) jobs.get(0).get("storage_key");
        Path file = directory.resolve(key).normalize();
        if (!file.getParent().equals(directory) || !Files.isRegularFile(file)) {
            throw new com.example.logistics.shared.api.ApiException(
                    org.springframework.http.HttpStatus.GONE, "REPORT_EXPORT_EXPIRED", "Report export is no longer available");
        }
        return file;
    }

    @Transactional
    public void cleanupExpired() {
        var expired = jdbc.queryForList("""
                SELECT id,storage_key FROM report_export_job
                WHERE expires_at<=? AND storage_key IS NOT NULL
                """, Timestamp.from(Instant.now()));
        for (var row : expired) {
            String key = (String) row.get("storage_key");
            Path file = directory.resolve(key).normalize();
            if (!file.getParent().equals(directory)) {
                log.error("Refusing to remove invalid report export path for job {}", row.get("id"));
                continue;
            }
            try {
                Files.deleteIfExists(file);
                jdbc.update("UPDATE report_export_job SET status='EXPIRED',storage_key=NULL WHERE id=?", row.get("id"));
            } catch (java.io.IOException exception) {
                log.error("Unable to remove expired report export for job {}", row.get("id"), exception);
            }
        }
    }

    private long writeCsv(Path file, UUID organizationId, AnalyticsService.Filters filters) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write('\ufeff');
            writeCsvRow(writer, HEADERS);
            return analytics.streamShipmentRows(organizationId, filters.customerId(), filters, row -> {
                try {
                    writeCsvRow(writer, List.of(row.shipmentNumber(), row.shipmentStatus(), row.orderNumber(),
                            row.customerId(), Integer.toString(row.stopSequence()), row.stopStatus(),
                            value(row.scheduledAt()), value(row.completedAt()), Integer.toString(row.failedAttempts())));
                } catch (Exception exception) {
                    throw new java.io.UncheckedIOException(new java.io.IOException("Unable to write CSV row", exception));
                }
            });
        }
    }

    private long writeXlsx(Path file, UUID organizationId, AnalyticsService.Filters filters) throws Exception {
        var workbook = new SXSSFWorkbook(100);
        try (OutputStream output = Files.newOutputStream(file)) {
            var sheet = workbook.createSheet("Deliveries");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) header.createCell(i).setCellValue(HEADERS.get(i));
            int[] rowNumber = {1};
            long count = analytics.streamShipmentRows(organizationId, filters.customerId(), filters, item -> {
                Row row = sheet.createRow(rowNumber[0]++);
                List<String> values = List.of(item.shipmentNumber(), item.shipmentStatus(), item.orderNumber(),
                        item.customerId(), Integer.toString(item.stopSequence()), item.stopStatus(),
                        value(item.scheduledAt()), value(item.completedAt()), Integer.toString(item.failedAttempts()));
                for (int i = 0; i < values.size(); i++) row.createCell(i).setCellValue(values.get(i));
            });
            workbook.write(output);
            return count;
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private long writePdf(Path file, UUID organizationId, AnalyticsService.Filters filters) throws Exception {
        Document document = new Document();
        try (OutputStream output = Files.newOutputStream(file)) {
        PdfWriter.getInstance(document, output);
        document.open();
        document.add(new Paragraph("Delivery report"));
        int[] rowNumber = {0};
        PdfPTable[] table = {pdfTable()};
        try {
            long count = analytics.streamShipmentRows(organizationId, filters.customerId(), filters, item -> {
                for (String value : List.of(item.shipmentNumber(), item.shipmentStatus(), item.orderNumber(),
                        item.customerId(), Integer.toString(item.stopSequence()), item.stopStatus(),
                        value(item.scheduledAt()), value(item.completedAt()), Integer.toString(item.failedAttempts()))) {
                    table[0].addCell(value);
                }
                if (++rowNumber[0] % 100 == 0) {
                    document.add(table[0]);
                    table[0] = pdfTable();
                }
            });
            if (rowNumber[0] % 100 != 0) document.add(table[0]);
            return count;
        } finally {
            document.close();
        }
        }
    }

    private static PdfPTable pdfTable() {
        PdfPTable table = new PdfPTable(HEADERS.size());
        for (String header : HEADERS) table.addCell(header);
        table.setHeaderRows(1);
        return table;
    }

    private static void writeCsvRow(BufferedWriter writer, List<String> values) throws java.io.IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(',');
            writer.write('"');
            String value = values.get(i);
            if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) writer.write('\'');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }

    private static String value(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toInstant().toString();
    }
}
