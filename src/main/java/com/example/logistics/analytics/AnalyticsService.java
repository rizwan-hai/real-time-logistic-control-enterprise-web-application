package com.example.logistics.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {
    private final JdbcTemplate jdbc;

    public AnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UUID organizationId, UUID customerScope, Filters filters) {
        return dashboard(organizationId, customerScope, filters, List.of("delivery", "fleet", "warehouse", "financial"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UUID organizationId, UUID customerScope, Filters filters, List<String> categories) {
        return dashboard(organizationId, customerScope, filters, categories, List.of());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UUID organizationId, UUID customerScope, Filters filters,
                                         List<String> categories, List<UUID> warehouseScopes) {
        var result = new LinkedHashMap<String, Object>();
        result.put("filters", filters);
        if (categories.contains("delivery")) result.put("delivery", delivery(organizationId, customerScope, filters));
        if (categories.contains("fleet")) result.put("fleet", fleet(organizationId, filters));
        if (categories.contains("warehouse")) result.put("warehouse", warehouse(organizationId, filters, warehouseScopes));
        if (categories.contains("financial")) result.put("financial", customerScope == null ? financial(organizationId, filters)
                : emptyCustomerFinancials());
        var unavailable = new ArrayList<String>();
        if (categories.contains("fleet")) unavailable.add("fleet.availability: vehicle availability calendars are not persisted");
        if (categories.contains("warehouse")) {
            unavailable.add("warehouse.inventoryTurnover: inventory valuation is not persisted");
            unavailable.add("warehouse.lowStockTrends: product reorder thresholds are not persisted");
        }
        if (categories.contains("financial")) unavailable.addAll(List.of("financial.revenue", "financial.profitMargin",
                "financial.customerProfitability", "financial.routeProfitability", "financial.outstandingReceivables", "financial.payables"));
        result.put("unavailableMetrics", unavailable);
        return result;
    }

    private static Map<String, Object> emptyCustomerFinancials() {
        var result = new LinkedHashMap<String, Object>();
        result.put("revenue", null);
        result.put("operatingCostByCurrency", List.of());
        result.put("profitMarginPercent", null);
        result.put("customerProfitability", null);
        result.put("routeProfitability", null);
        result.put("outstandingReceivables", null);
        result.put("payables", null);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> shipmentRows(UUID organizationId, UUID customerScope, Filters filters, int page, int size) {
        Query query = shipmentQuery(organizationId, customerScope, filters);
        var args = new ArrayList<>(query.args());
        args.add(size);
        args.add((long) page * size);
        return jdbc.query(query.sql() + " LIMIT ? OFFSET ?", args.toArray(), (rs, row) -> {
            var item = new LinkedHashMap<String, Object>();
            item.put("shipmentNumber", rs.getString("shipment_number"));
            item.put("shipmentStatus", rs.getString("shipment_status"));
            item.put("orderNumber", rs.getString("order_number"));
            item.put("customerId", rs.getString("customer_id"));
            item.put("stopSequence", rs.getInt("sequence_no"));
            item.put("stopStatus", rs.getString("stop_status"));
            item.put("scheduledAt", rs.getTimestamp("scheduled_at"));
            item.put("completedAt", rs.getTimestamp("completed_at"));
            item.put("failedAttempts", rs.getInt("failed_attempts"));
            return item;
        });
    }

    public long streamShipmentRows(UUID organizationId, UUID customerScope, Filters filters,
                                   java.util.function.Consumer<ShipmentRow> consumer) {
        Query query = shipmentQuery(organizationId, customerScope, filters);
        Long count = jdbc.query(statement(query), rs -> {
            long rows = 0;
            while (rs.next()) {
                consumer.accept(new ShipmentRow(rs.getString("shipment_number"), rs.getString("shipment_status"),
                        rs.getString("order_number"), rs.getString("customer_id"), rs.getInt("sequence_no"),
                        rs.getString("stop_status"), rs.getTimestamp("scheduled_at"), rs.getTimestamp("completed_at"),
                        rs.getInt("failed_attempts")));
                rows++;
            }
            return rows;
        });
        return count == null ? 0 : count;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> deliveryTrends(UUID organizationId, UUID customerScope, Filters filters) {
        var args = new ArrayList<Object>();
        StringBuilder where = new StringBuilder("s.organization_id=? AND s.created_at >= ? AND s.created_at < ?");
        args.add(organizationId.toString());
        args.add(filters.from().atStartOfDay());
        args.add(filters.to().plusDays(1).atStartOfDay());
        UUID customer = customerScope != null ? customerScope : filters.customerId();
        if (customer != null) {
            where.append(" AND o.customer_id=?");
            args.add(customer.toString());
        }
        if (filters.vehicleId() != null || filters.driverId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM trip_shipment ts JOIN trip t ON t.id=ts.trip_id WHERE ts.shipment_id=s.id");
            if (filters.vehicleId() != null) {
                where.append(" AND t.vehicle_id=?");
                args.add(filters.vehicleId().toString());
            }
            if (filters.driverId() != null) {
                where.append(" AND t.driver_id=?");
                args.add(filters.driverId().toString());
            }
            where.append(")");
        }
        return jdbc.queryForList("""
                SELECT DATE(s.created_at) report_date,s.status,COUNT(DISTINCT s.id) shipment_count
                FROM shipment s JOIN shipment_order so ON so.shipment_id=s.id
                JOIN sales_order o ON o.id=so.order_id AND o.organization_id=s.organization_id AND o.organization_id=s.organization_id
                WHERE %s GROUP BY DATE(s.created_at),s.status ORDER BY report_date
                """.formatted(where), args.toArray());
    }

    private Map<String, Object> delivery(UUID org, UUID customerScope, Filters filters) {
        Query query = shipmentQuery(org, customerScope, filters);
        DeliveryTotals totals = jdbc.query(statement(query), rs -> {
            var value = new DeliveryTotals();
            while (rs.next()) {
                value.stops++;
                String status = rs.getString("stop_status");
                var scheduled = rs.getTimestamp("scheduled_at");
                var completed = rs.getTimestamp("completed_at");
                var created = rs.getTimestamp("shipment_created_at");
                int failedAttempts = rs.getInt("failed_attempts");
                if ("COMPLETED".equals(status)) {
                    value.completed++;
                    if (failedAttempts == 0) value.firstAttempt++;
                    if (scheduled != null && completed != null) {
                        value.scheduledCompleted++;
                        if (completed.after(scheduled)) value.delayed++;
                        else value.onTime++;
                    }
                    if (completed != null && created != null) {
                        value.leadTimeSeconds += Duration.between(created.toInstant(), completed.toInstant()).getSeconds();
                        value.leadTimeSamples++;
                    }
                } else if ("FAILED".equals(status)) {
                    value.failed++;
                }
            }
            return value;
        });
        var result = new LinkedHashMap<String, Object>();
        result.put("deliveryStops", totals.stops);
        result.put("completedDeliveries", totals.completed);
        result.put("onTimeDeliveryPercent", percent(totals.onTime, totals.scheduledCompleted));
        result.put("averageLeadTimeHours", totals.leadTimeSamples == 0 ? null
                : BigDecimal.valueOf(totals.leadTimeSeconds).divide(BigDecimal.valueOf(totals.leadTimeSamples * 3600d), 2, RoundingMode.HALF_UP));
        result.put("delayRatePercent", percent(totals.delayed, totals.scheduledCompleted));
        result.put("firstAttemptSuccessRatePercent", percent(totals.firstAttempt, totals.completed));
        result.put("failedDeliveryRatePercent", percent(totals.failed, totals.stops));
        return result;
    }

    private Map<String, Object> fleet(UUID org, Filters filters) {
        var args = new ArrayList<Object>();
        StringBuilder where = datePredicate("t.created_at", filters, args);
        where.append(" AND t.organization_id=?");
        args.add(org.toString());
        if (filters.vehicleId() != null) {
            where.append(" AND t.vehicle_id=?");
            args.add(filters.vehicleId().toString());
        }
        if (filters.driverId() != null) {
            where.append(" AND t.driver_id=?");
            args.add(filters.driverId().toString());
        }
        var tripStats = jdbc.queryForMap("""
                SELECT COUNT(*) trip_count,
                       SUM(CASE WHEN t.status='COMPLETED' THEN 1 ELSE 0 END) completed_trips,
                       SUM(CASE WHEN t.status IN ('DISPATCHED','STARTED') THEN 1 ELSE 0 END) active_trips,
                       COUNT(DISTINCT CASE WHEN t.status IN ('DISPATCHED','STARTED') THEN t.vehicle_id END) active_vehicles,
                       SUM(CASE WHEN t.odometer_end IS NOT NULL AND t.odometer_start IS NOT NULL AND t.odometer_end>t.odometer_start
                           THEN t.odometer_end-t.odometer_start ELSE 0 END) distance_km
                FROM trip t WHERE %s
                """.formatted(where), args.toArray());
        long vehicleCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM master_data_record
                WHERE organization_id=? AND resource_type='vehicles' AND status='ACTIVE'
                """ + (filters.vehicleId() == null ? "" : " AND id=?"),
                Long.class, filters.vehicleId() == null ? new Object[]{org.toString()} : new Object[]{org.toString(), filters.vehicleId().toString()});
        List<Map<String, Object>> fuel = jdbc.queryForList("""
                SELECT trip_fuel.unit,SUM(trip_fuel.quantity) quantity,SUM(trip_fuel.distance) distance
                FROM (
                    SELECT f.unit,t.id trip_id,SUM(f.quantity) quantity,
                           MAX(CASE WHEN t.odometer_end>t.odometer_start THEN t.odometer_end-t.odometer_start ELSE 0 END) distance
                    FROM fuel_log f JOIN trip t ON t.id=f.trip_id
                    WHERE %s GROUP BY f.unit,t.id
                ) trip_fuel GROUP BY trip_fuel.unit
                """.formatted(where), args.toArray());
        var expenseArgs = new ArrayList<Object>();
        StringBuilder expenseWhere = new StringBuilder("t.organization_id=? AND e.incurred_at >= ? AND e.incurred_at < ?");
        expenseArgs.add(org.toString());
        expenseArgs.add(filters.from().atStartOfDay());
        expenseArgs.add(filters.to().plusDays(1).atStartOfDay());
        if (filters.vehicleId() != null) {
            expenseWhere.append(" AND t.vehicle_id=?");
            expenseArgs.add(filters.vehicleId().toString());
        }
        if (filters.driverId() != null) {
            expenseWhere.append(" AND t.driver_id=?");
            expenseArgs.add(filters.driverId().toString());
        }
        List<Map<String, Object>> tripCosts = jdbc.queryForList("""
                SELECT e.currency,SUM(e.amount) operating_cost
                FROM trip_expense e JOIN trip t ON t.id=e.trip_id WHERE %s GROUP BY e.currency
                """.formatted(expenseWhere), expenseArgs.toArray());

        var result = new LinkedHashMap<String, Object>();
        long completed = number(tripStats.get("completed_trips"));
        long tripCount = number(tripStats.get("trip_count"));
        long active = number(tripStats.get("active_vehicles"));
        BigDecimal distance = decimal(tripStats.get("distance_km"));
        result.put("completedTrips", completed);
        result.put("distanceKm", distance);
        result.put("driverPerformancePercent", percent(completed, tripCount));
        result.put("vehicleUtilizationPercent", percent(active, vehicleCount));
        result.put("costPerKilometer", distance.signum() > 0 ? tripCosts.stream().map(cost -> Map.of(
                "currency", cost.get("currency"),
                "amountPerKm", decimal(cost.get("operating_cost")).divide(distance, 4, RoundingMode.HALF_UP))).toList() : List.of());
        result.put("fuelEfficiency", fuel.size() == 1 && decimal(fuel.get(0).get("distance")).signum() > 0
                ? Map.of("unit", fuel.get(0).get("unit"), "quantityPerKm",
                    decimal(fuel.get(0).get("quantity")).divide(decimal(fuel.get(0).get("distance")), 4, RoundingMode.HALF_UP))
                : null);
        result.put("fleetAvailabilityPercent", null);
        return result;
    }

    private Map<String, Object> warehouse(UUID org, Filters filters, List<UUID> warehouseScopes) {
        var args = new ArrayList<Object>();
        StringBuilder scope = new StringBuilder("ii.organization_id=?");
        args.add(org.toString());
        appendWarehouseScope(scope, args, "ii.location_id", warehouseScopes, filters.warehouseId());
        var stock = jdbc.queryForMap("""
                SELECT COUNT(*) item_count,
                       SUM(CASE WHEN expires_at IS NOT NULL AND expires_at < CURRENT_DATE THEN 1 ELSE 0 END) expired_count,
                       SUM(CASE WHEN expires_at IS NOT NULL AND expires_at BETWEEN ? AND ? THEN 1 ELSE 0 END) expiring_count
                FROM inventory_item ii WHERE %s
                """.formatted(scope), append(args, LocalDate.now(), LocalDate.now().plusDays(30)).toArray());
        var countArgs = new ArrayList<Object>();
        StringBuilder countScope = new StringBuilder("c.organization_id=? AND c.status IN ('COMPLETED','APPROVED') AND c.created_at >= ? AND c.created_at < ?");
        countArgs.add(org.toString());
        countArgs.add(filters.from().atStartOfDay());
        countArgs.add(filters.to().plusDays(1).atStartOfDay());
        appendWarehouseScope(countScope, countArgs, "l.inventory_item_id", warehouseScopes, filters.warehouseId());
        var counts = jdbc.queryForObject("""
                SELECT COUNT(l.counted_qty) line_count,
                       SUM(CASE WHEN l.counted_qty IS NOT NULL AND l.counted_qty=l.expected_qty THEN 1 ELSE 0 END) accurate_count
                FROM stock_count_line l JOIN stock_count c ON c.id=l.count_id
                JOIN inventory_item i ON i.id=l.inventory_item_id
                WHERE %s
                """.formatted(countScope.toString().replace("l.inventory_item_id", "i.location_id")),
                (rs, row) -> new long[]{rs.getLong("line_count"), rs.getLong("accurate_count")}, countArgs.toArray());
        var pickArgs = new ArrayList<Object>();
        StringBuilder pickScope = new StringBuilder("p.organization_id=? AND p.created_at >= ? AND p.created_at < ?");
        pickArgs.add(org.toString());
        pickArgs.add(filters.from().atStartOfDay());
        pickArgs.add(filters.to().plusDays(1).atStartOfDay());
        appendWarehouseScope(pickScope, pickArgs, "i.location_id", warehouseScopes, filters.warehouseId());
        var pick = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.requested_qty),0) requested, COALESCE(SUM(l.picked_qty),0) picked
                FROM pick_list_line l JOIN pick_list p ON p.id=l.pick_list_id
                JOIN inventory_item i ON i.id=l.inventory_item_id
                WHERE %s
                """.formatted(pickScope), (rs, row) -> new BigDecimal[]{rs.getBigDecimal("requested"), rs.getBigDecimal("picked")},
                pickArgs.toArray());
        var receiptArgs = new ArrayList<Object>();
        StringBuilder receiptScope = new StringBuilder("r.organization_id=? AND r.received_at >= ? AND r.received_at < ? AND p.expected_at IS NOT NULL");
        receiptArgs.add(org.toString());
        receiptArgs.add(filters.from().atStartOfDay());
        receiptArgs.add(filters.to().plusDays(1).atStartOfDay());
        if (!warehouseScopes.isEmpty() || filters.warehouseId() != null) {
            receiptScope.append(" AND EXISTS (SELECT 1 FROM goods_receipt_line rl WHERE rl.receipt_id=r.id AND rl.suggested_location_id IN (")
                    .append("SELECT wl.id FROM warehouse_location wl WHERE 1=1");
            if (!warehouseScopes.isEmpty()) {
                receiptScope.append(" AND (wl.id IN (")
                        .append(String.join(",", java.util.Collections.nCopies(warehouseScopes.size(), "?")))
                        .append(") OR wl.warehouse_id IN (")
                        .append(String.join(",", java.util.Collections.nCopies(warehouseScopes.size(), "?"))).append("))");
                warehouseScopes.forEach(id -> receiptArgs.add(id.toString()));
                warehouseScopes.forEach(id -> receiptArgs.add(id.toString()));
            }
            if (filters.warehouseId() != null) {
                receiptScope.append(" AND (wl.id=? OR wl.warehouse_id=?)");
                receiptArgs.add(filters.warehouseId().toString());
                receiptArgs.add(filters.warehouseId().toString());
            }
            receiptScope.append("))");
        }
        var receiving = jdbc.queryForObject("""
                SELECT COUNT(*) expected_receipts,
                       SUM(CASE WHEN r.received_at<=p.expected_at THEN 1 ELSE 0 END) on_time_receipts
                FROM goods_receipt r JOIN purchase_order p ON p.id=r.purchase_order_id
                WHERE %s
                """.formatted(receiptScope), (rs, row) -> new long[]{rs.getLong("expected_receipts"), rs.getLong("on_time_receipts")},
                receiptArgs.toArray());
        var result = new LinkedHashMap<String, Object>();
        result.put("inventoryItems", number(stock.get("item_count")));
        result.put("expiredStockItems", number(stock.get("expired_count")));
        result.put("expiringWithin30DaysItems", number(stock.get("expiring_count")));
        result.put("stockAccuracyPercent", percent(counts[1], counts[0]));
        result.put("pickingPerformancePercent", percent(pick[1], pick[0]));
        result.put("inventoryTurnover", null);
        result.put("lowStockCount", null);
        result.put("receivingPerformancePercent", percent(receiving[1], receiving[0]));
        return result;
    }

    private static void appendWarehouseScope(StringBuilder predicate, List<Object> args, String locationColumn,
                                             List<UUID> warehouseScopes, UUID selectedWarehouse) {
        if (!warehouseScopes.isEmpty()) {
            predicate.append(" AND EXISTS (SELECT 1 FROM warehouse_location wl WHERE wl.id=").append(locationColumn)
                    .append(" AND (wl.id IN (").append(String.join(",", java.util.Collections.nCopies(warehouseScopes.size(), "?")))
                    .append(") OR wl.warehouse_id IN (").append(String.join(",", java.util.Collections.nCopies(warehouseScopes.size(), "?"))).append(")))");
            warehouseScopes.forEach(id -> args.add(id.toString()));
            warehouseScopes.forEach(id -> args.add(id.toString()));
        }
        if (selectedWarehouse != null) {
            predicate.append(" AND ").append(locationColumn)
                    .append(" IN (SELECT id FROM warehouse_location WHERE warehouse_id=? OR id=?)");
            args.add(selectedWarehouse.toString());
            args.add(selectedWarehouse.toString());
        }
    }

    private Map<String, Object> financial(UUID org, Filters filters) {
        List<Map<String, Object>> costs = jdbc.queryForList("""
                SELECT currency, SUM(amount) operating_cost
                FROM trip_expense e JOIN trip t ON t.id=e.trip_id
                WHERE t.organization_id=? AND e.incurred_at >= ? AND e.incurred_at < ?
                GROUP BY currency ORDER BY currency
                """, org.toString(), filters.from().atStartOfDay(), filters.to().plusDays(1).atStartOfDay());
        var result = new LinkedHashMap<String, Object>();
        result.put("revenue", null);
        result.put("operatingCostByCurrency", costs);
        result.put("profitMarginPercent", null);
        result.put("customerProfitability", null);
        result.put("routeProfitability", null);
        result.put("outstandingReceivables", null);
        result.put("payables", null);
        return result;
    }

    private Query shipmentQuery(UUID org, UUID customerScope, Filters filters) {
        var args = new ArrayList<Object>();
        StringBuilder where = new StringBuilder("s.organization_id=?");
        args.add(org.toString());
        where.append(" AND s.created_at >= ? AND s.created_at < ?");
        args.add(filters.from().atStartOfDay());
        args.add(filters.to().plusDays(1).atStartOfDay());
        UUID customer = customerScope != null ? customerScope : filters.customerId();
        if (customer != null) {
            where.append(" AND o.customer_id=?");
            args.add(customer.toString());
        }
        if (filters.vehicleId() != null || filters.driverId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM trip_shipment ts JOIN trip t ON t.id=ts.trip_id WHERE ts.shipment_id=s.id");
            if (filters.vehicleId() != null) {
                where.append(" AND t.vehicle_id=?");
                args.add(filters.vehicleId().toString());
            }
            if (filters.driverId() != null) {
                where.append(" AND t.driver_id=?");
                args.add(filters.driverId().toString());
            }
            where.append(")");
        }
        String sql = """
                SELECT s.shipment_number, s.status shipment_status, o.order_number, o.customer_id,
                       st.sequence_no, st.status stop_status, st.scheduled_at,
                       s.created_at shipment_created_at,
                       (SELECT MAX(h.occurred_at) FROM shipment_stop_history h
                        WHERE h.stop_id=st.id AND h.to_status='COMPLETED') completed_at,
                       (SELECT COUNT(*) FROM shipment_stop_history h
                        WHERE h.stop_id=st.id AND h.to_status='FAILED') failed_attempts
                FROM shipment s
                JOIN shipment_order so ON so.shipment_id=s.id
                JOIN sales_order o ON o.id=so.order_id AND o.organization_id=s.organization_id
                JOIN shipment_stop st ON st.shipment_id=s.id
                WHERE %s
                ORDER BY s.created_at DESC,s.id,st.sequence_no
                """.formatted(where);
        return new Query(sql, args);
    }

    private PreparedStatementCreator statement(Query query) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement(query.sql(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            for (int i = 0; i < query.args().size(); i++) statement.setObject(i + 1, query.args().get(i));
            statement.setFetchSize(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")
                    ? Integer.MIN_VALUE : 500);
            return statement;
        };
    }

    private static List<Object> append(List<Object> values, Object... extra) {
        var result = new ArrayList<>(values);
        java.util.Collections.addAll(result, extra);
        return result;
    }

    private static StringBuilder datePredicate(String column, Filters filters, List<Object> args) {
        args.add(filters.from().atStartOfDay());
        args.add(filters.to().plusDays(1).atStartOfDay());
        return new StringBuilder(column).append(" >= ? AND ").append(column).append(" < ?");
    }

    private static Long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : value instanceof BigDecimal amount ? amount : new BigDecimal(value.toString());
    }

    private static BigDecimal percent(long numerator, long denominator) {
        return denominator == 0 ? null : BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        return denominator.signum() == 0 ? null : numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private record Query(String sql, List<Object> args) {}
    public record Filters(LocalDate from, LocalDate to, UUID customerId, UUID warehouseId, UUID vehicleId, UUID driverId) {}
    public record ShipmentRow(String shipmentNumber, String shipmentStatus, String orderNumber, String customerId,
                              int stopSequence, String stopStatus, java.sql.Timestamp scheduledAt,
                              java.sql.Timestamp completedAt, int failedAttempts) {}

    private static final class DeliveryTotals {
        private long stops;
        private long completed;
        private long delayed;
        private long failed;
        private long firstAttempt;
        private long scheduledCompleted;
        private long onTime;
        private long leadTimeSeconds;
        private long leadTimeSamples;
    }
}
