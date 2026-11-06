CREATE TABLE sales_order (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, order_number VARCHAR(40) NOT NULL,
 customer_id CHAR(36) NOT NULL, status VARCHAR(30) NOT NULL, fulfillment_type VARCHAR(30) NOT NULL,
 ship_to_json TEXT NOT NULL, currency CHAR(3) NOT NULL, requested_delivery_at TIMESTAMP(6) NULL,
 created_by CHAR(36) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), version BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_order_number (organization_id, order_number), INDEX ix_order_status (organization_id,status,created_at)
);
CREATE TABLE order_line (
 id CHAR(36) NOT NULL PRIMARY KEY, order_id CHAR(36) NOT NULL, product_id CHAR(36) NOT NULL,
 description VARCHAR(255) NOT NULL, quantity DECIMAL(18,4) NOT NULL, unit VARCHAR(30) NOT NULL,
 unit_price DECIMAL(18,4) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_line_order FOREIGN KEY(order_id) REFERENCES sales_order(id), CHECK(quantity > 0), INDEX ix_line_order(order_id)
);
CREATE TABLE order_status_history (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, order_id CHAR(36) NOT NULL,
 from_status VARCHAR(30) NULL, to_status VARCHAR(30) NOT NULL, actor_id CHAR(36) NULL,
 reason VARCHAR(1000) NOT NULL, occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_order_history FOREIGN KEY(order_id) REFERENCES sales_order(id), INDEX ix_order_history(order_id,occurred_at)
);
CREATE TABLE order_comment (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, order_id CHAR(36) NOT NULL,
 actor_id CHAR(36) NULL, body TEXT NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_order_comment FOREIGN KEY(order_id) REFERENCES sales_order(id), INDEX ix_order_comment(order_id,created_at)
);
CREATE TABLE shipment (
 id CHAR(36) NOT NULL PRIMARY KEY, organization_id CHAR(36) NOT NULL, shipment_number VARCHAR(40) NOT NULL,
 status VARCHAR(30) NOT NULL, created_by CHAR(36) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), version BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_shipment_number(organization_id,shipment_number), INDEX ix_shipment_status(organization_id,status)
);
CREATE TABLE shipment_order (
 shipment_id CHAR(36) NOT NULL, order_id CHAR(36) NOT NULL, PRIMARY KEY(shipment_id,order_id),
 UNIQUE KEY uk_order_shipment(order_id), FOREIGN KEY(shipment_id) REFERENCES shipment(id), FOREIGN KEY(order_id) REFERENCES sales_order(id)
);
CREATE TABLE shipment_stop (
 id CHAR(36) NOT NULL PRIMARY KEY, shipment_id CHAR(36) NOT NULL, sequence_no INT NOT NULL,
 stop_type VARCHAR(20) NOT NULL, address_json TEXT NOT NULL, scheduled_at TIMESTAMP(6) NULL, status VARCHAR(20) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uk_stop_sequence(shipment_id,sequence_no),
 FOREIGN KEY(shipment_id) REFERENCES shipment(id)
);
CREATE TABLE shipment_stop_history (
 id CHAR(36) NOT NULL PRIMARY KEY, stop_id CHAR(36) NOT NULL, from_status VARCHAR(20) NULL, to_status VARCHAR(20) NOT NULL,
 actor_id CHAR(36) NULL, reason VARCHAR(1000) NOT NULL, occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(stop_id) REFERENCES shipment_stop(id), INDEX ix_stop_history(stop_id,occurred_at)
);
