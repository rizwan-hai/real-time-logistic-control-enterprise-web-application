CREATE TABLE warehouse_location (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, warehouse_id CHAR(36) NULL,
 parent_id CHAR(36) NULL, code VARCHAR(100) NOT NULL, name VARCHAR(200) NOT NULL, location_type VARCHAR(30) NOT NULL,
 zone VARCHAR(100) NULL, capacity DECIMAL(18,4) NULL, occupied DECIMAL(18,4) NOT NULL DEFAULT 0,
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_location_code(organization_id,code), INDEX ix_location_parent(organization_id,parent_id),
 CONSTRAINT fk_location_parent FOREIGN KEY(parent_id) REFERENCES warehouse_location(id), CHECK(capacity IS NULL OR capacity >= 0), CHECK(occupied >= 0)
);
CREATE TABLE purchase_order (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, po_number VARCHAR(40) NOT NULL,
 supplier_id CHAR(36) NULL, status VARCHAR(30) NOT NULL, expected_at TIMESTAMP(6) NULL,
 created_by CHAR(36) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_po_number(organization_id,po_number), INDEX ix_po_status(organization_id,status)
);
CREATE TABLE purchase_order_line (
 id CHAR(36) NOT NULL PRIMARY KEY, purchase_order_id CHAR(36) NOT NULL, product_id CHAR(36) NOT NULL,
 description VARCHAR(255) NOT NULL, ordered_qty DECIMAL(18,4) NOT NULL, received_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
 unit VARCHAR(30) NOT NULL DEFAULT 'EA', CHECK(ordered_qty > 0), CHECK(received_qty >= 0),
 FOREIGN KEY(purchase_order_id) REFERENCES purchase_order(id)
);
CREATE TABLE advance_shipping_notice (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, asn_number VARCHAR(40) NOT NULL,
 purchase_order_id CHAR(36) NULL, status VARCHAR(30) NOT NULL, expected_at TIMESTAMP(6) NULL,
 UNIQUE KEY uk_asn_number(organization_id,asn_number), FOREIGN KEY(purchase_order_id) REFERENCES purchase_order(id)
);
CREATE TABLE goods_receipt (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, receipt_number VARCHAR(40) NOT NULL,
 purchase_order_id CHAR(36) NULL, asn_id CHAR(36) NULL, status VARCHAR(30) NOT NULL, received_by CHAR(36) NULL,
 received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uk_receipt_number(organization_id,receipt_number),
 FOREIGN KEY(purchase_order_id) REFERENCES purchase_order(id), FOREIGN KEY(asn_id) REFERENCES advance_shipping_notice(id)
);
CREATE TABLE goods_receipt_line (
 id CHAR(36) NOT NULL PRIMARY KEY, receipt_id CHAR(36) NOT NULL, product_id CHAR(36) NOT NULL, quantity DECIMAL(18,4) NOT NULL,
 quality_status VARCHAR(20) NOT NULL, lot_code VARCHAR(100) NULL, serial_number VARCHAR(160) NULL, expires_at DATE NULL,
 suggested_location_id CHAR(36) NULL, FOREIGN KEY(receipt_id) REFERENCES goods_receipt(id),
 FOREIGN KEY(suggested_location_id) REFERENCES warehouse_location(id), CHECK(quantity > 0)
);
CREATE TABLE inventory_item (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, product_id CHAR(36) NOT NULL,
 location_id CHAR(36) NOT NULL, lot_code VARCHAR(100) NULL, serial_number VARCHAR(160) NULL, expires_at DATE NULL,
 qty_on_hand DECIMAL(18,4) NOT NULL DEFAULT 0, qty_reserved DECIMAL(18,4) NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_stock_identity(organization_id,product_id,location_id,lot_code,serial_number),
 INDEX ix_stock_allocate(organization_id,product_id,expires_at,created_at), INDEX ix_stock_location(organization_id,location_id),
 FOREIGN KEY(location_id) REFERENCES warehouse_location(id), CHECK(qty_on_hand >= 0), CHECK(qty_reserved >= 0), CHECK(qty_reserved <= qty_on_hand)
);
CREATE TABLE stock_movement (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, inventory_item_id CHAR(36) NOT NULL,
 movement_type VARCHAR(30) NOT NULL, quantity DECIMAL(18,4) NOT NULL, quantity_delta DECIMAL(18,4) NOT NULL DEFAULT 0, from_location_id CHAR(36) NULL,
 to_location_id CHAR(36) NULL, reference_type VARCHAR(40) NULL, reference_id CHAR(36) NULL, reverses_movement_id CHAR(36) NULL,
 actor_id CHAR(36) NULL, occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX ix_movement_item(inventory_item_id,occurred_at), INDEX ix_movement_org(organization_id,occurred_at),
 FOREIGN KEY(inventory_item_id) REFERENCES inventory_item(id), FOREIGN KEY(reverses_movement_id) REFERENCES stock_movement(id), CHECK(quantity > 0)
);
CREATE TABLE inventory_reservation (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, order_id CHAR(36) NOT NULL,
 order_line_id CHAR(36) NOT NULL, inventory_item_id CHAR(36) NOT NULL, quantity DECIMAL(18,4) NOT NULL,
 status VARCHAR(20) NOT NULL, expires_at TIMESTAMP(6) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX ix_reservation_order(organization_id,order_id,status), FOREIGN KEY(inventory_item_id) REFERENCES inventory_item(id), CHECK(quantity > 0)
);
CREATE TABLE putaway_task (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, receipt_line_id CHAR(36) NOT NULL,
 inventory_item_id CHAR(36) NULL, source_location_id CHAR(36) NULL, target_location_id CHAR(36) NOT NULL,
 quantity DECIMAL(18,4) NOT NULL, status VARCHAR(20) NOT NULL, assigned_to CHAR(36) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), completed_at TIMESTAMP(6) NULL,
 FOREIGN KEY(receipt_line_id) REFERENCES goods_receipt_line(id), FOREIGN KEY(inventory_item_id) REFERENCES inventory_item(id),
 FOREIGN KEY(target_location_id) REFERENCES warehouse_location(id), CHECK(quantity > 0)
);
CREATE TABLE pick_wave (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, wave_number VARCHAR(40) NOT NULL,
 status VARCHAR(20) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_wave_number(organization_id,wave_number)
);
CREATE TABLE pick_list (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, wave_id CHAR(36) NULL, order_id CHAR(36) NOT NULL,
 status VARCHAR(20) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(wave_id) REFERENCES pick_wave(id), INDEX ix_pick_order(organization_id,order_id,status)
);
CREATE TABLE pick_list_line (
 id CHAR(36) NOT NULL PRIMARY KEY, pick_list_id CHAR(36) NOT NULL, order_line_id CHAR(36) NOT NULL,
 inventory_item_id CHAR(36) NOT NULL, requested_qty DECIMAL(18,4) NOT NULL, picked_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
 status VARCHAR(20) NOT NULL, FOREIGN KEY(pick_list_id) REFERENCES pick_list(id), FOREIGN KEY(inventory_item_id) REFERENCES inventory_item(id),
 CHECK(requested_qty > 0), CHECK(picked_qty >= 0)
);
CREATE TABLE package (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, order_id CHAR(36) NOT NULL,
 package_code VARCHAR(80) NOT NULL, weight DECIMAL(18,4) NULL, status VARCHAR(20) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uk_package_code(organization_id,package_code), CHECK(weight IS NULL OR weight >= 0)
);
CREATE TABLE stock_count (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, count_number VARCHAR(40) NOT NULL,
 count_type VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL, blind BOOLEAN NOT NULL DEFAULT TRUE,
 created_by CHAR(36) NULL, approved_by CHAR(36) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_count_number(organization_id,count_number)
);
CREATE TABLE stock_count_line (
 id CHAR(36) NOT NULL PRIMARY KEY, count_id CHAR(36) NOT NULL, inventory_item_id CHAR(36) NOT NULL,
 expected_qty DECIMAL(18,4) NOT NULL, counted_qty DECIMAL(18,4) NULL, counted_at TIMESTAMP(6) NULL,
 FOREIGN KEY(count_id) REFERENCES stock_count(id), FOREIGN KEY(inventory_item_id) REFERENCES inventory_item(id)
);
