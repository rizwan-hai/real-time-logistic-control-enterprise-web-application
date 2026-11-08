package com.example.logistics.orders.application;

import com.example.logistics.shared.api.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class OrderService {
    private final JdbcTemplate db; private final ObjectMapper json;
    public OrderService(JdbcTemplate db, ObjectMapper json) { this.db=db; this.json=json; }

    @Transactional
    public Map<String,Object> create(UUID org, UUID actor, CreateOrder body) {
        if(body.lines()==null || body.lines().isEmpty()) throw invalid("At least one order line is required");
        validateMaster(org,"customers",body.customerId());
        UUID id=UUID.randomUUID(); String number=nextNumber(org,"ORD");
        try {
            db.update("INSERT INTO sales_order(id,organization_id,order_number,customer_id,status,fulfillment_type,ship_to_json,currency,requested_delivery_at,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    id.toString(),org.toString(),number,body.customerId().toString(),"DRAFT",norm(body.fulfillmentType(),"WAREHOUSE"),write(body.shipTo()),norm(body.currency(),"USD"),body.requestedDeliveryAt()==null?null:java.sql.Timestamp.from(body.requestedDeliveryAt()),actor==null?null:actor.toString());
            for(Line line:body.lines()) {
                if(line.quantity()==null || line.quantity().signum()<=0) throw invalid("Line quantity must be greater than zero");
                if(line.description()==null||line.description().isBlank()) throw invalid("Product description is required");
                validateMaster(org,"products",line.productId());
                db.update("INSERT INTO order_line(id,order_id,product_id,description,quantity,unit,unit_price) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id.toString(),line.productId().toString(),line.description(),line.quantity(),norm(line.unit(),"EA"),line.unitPrice());
            }
            history(org,id,null,"DRAFT",actor,"Order created"); event(org,"OrderCreated",id,Map.of("orderId",id,"orderNumber",number));
            return get(org,id);
        } catch (RuntimeException e) { throw e; }
    }

    @Transactional
    public Map<String,Object> transition(UUID org, UUID actor, UUID id, String target, String reason) {
        var order=rawOrder(org,id); String from=(String)order.get("status"); target=target.toUpperCase(Locale.ROOT);
        if(!allowed(from,target,(String)order.get("fulfillment_type"))) throw new ApiException(HttpStatus.CONFLICT,"ORDER_STATE_TRANSITION_INVALID","Transition "+from+" to "+target+" is not allowed");
        if(target.equals("CONFIRMED")) validateConfirmation(org,id,order);
        db.update("UPDATE sales_order SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND organization_id=?",target,id.toString(),org.toString());
        history(org,id,from,target,actor,reason==null?"":reason); event(org,"OrderStatusChanged",id,Map.of("orderId",id,"from",from,"to",target,"reason",reason==null?"":reason));
        if(target.equals("CANCELLED") && !"TRANSPORT_ONLY".equals(order.get("fulfillmentType"))) event(org,"InventoryReservationReleaseRequested",id,Map.of("orderId",id,"contractVersion",1));
        return get(org,id);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> get(UUID org, UUID id) {
        var result=rawOrder(org,id);
        result.put("lines",db.queryForList("SELECT id,product_id productId,description,quantity,unit,unit_price unitPrice FROM order_line WHERE order_id=?",id.toString()));
        result.put("history",db.queryForList("SELECT from_status fromStatus,to_status toStatus,actor_id actorId,reason,occurred_at occurredAt FROM order_status_history WHERE order_id=? ORDER BY occurred_at,id",id.toString()));
        result.put("comments",db.queryForList("SELECT id,actor_id actorId,body,created_at createdAt FROM order_comment WHERE order_id=? ORDER BY created_at",id.toString()));
        return result;
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> list(UUID org) { return db.queryForList("SELECT id,order_number orderNumber,customer_id customerId,status,fulfillment_type fulfillmentType,currency,created_at createdAt FROM sales_order WHERE organization_id=? ORDER BY created_at DESC",org.toString()); }
    @Transactional(readOnly=true) public List<Map<String,Object>> listForCustomer(UUID org, UUID customer) {
        return db.queryForList("SELECT id,order_number orderNumber,customer_id customerId,status,fulfillment_type fulfillmentType,currency,created_at createdAt FROM sales_order WHERE organization_id=? AND customer_id=? ORDER BY created_at DESC",
                org.toString(), customer.toString());
    }
    @Transactional(readOnly=true) public Map<String,Object> getForCustomer(UUID org, UUID customer, UUID id) {
        var order = rawOrder(org, id);
        if (!customer.toString().equals(String.valueOf(order.get("customerId")))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found");
        }
        order.put("lines", db.queryForList("""
                SELECT description,quantity,unit,unit_price unitPrice
                FROM order_line WHERE order_id=? ORDER BY id
                """, id.toString()));
        order.put("history", db.queryForList("""
                SELECT to_status toStatus,occurred_at occurredAt
                FROM order_status_history WHERE order_id=? ORDER BY occurred_at,id
                """, id.toString()));
        return order;
    }
    @Transactional public Map<String,Object> createForCustomer(UUID org, UUID customer, UUID actor, CreateOrder body) {
        if (body.customerId() != null && !customer.equals(body.customerId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_SCOPE_FORBIDDEN", "Orders can only be created for the authenticated customer");
        }
        return create(org, actor, new CreateOrder(customer, body.fulfillmentType(), body.shipTo(),
                body.currency(), body.requestedDeliveryAt(), body.lines()));
    }

    @Transactional public void comment(UUID org, UUID actor, UUID id, String body) { rawOrder(org,id); db.update("INSERT INTO order_comment(id,organization_id,order_id,actor_id,body) VALUES(?,?,?,?,?)",UUID.randomUUID().toString(),org.toString(),id.toString(),actor==null?null:actor.toString(),body); }

    private void validateConfirmation(UUID org,UUID id,Map<String,Object> o) {
        if(db.queryForObject("SELECT COUNT(*) FROM order_line WHERE order_id=?",Integer.class,id.toString())==0) throw invalid("Order requires at least one line");
        @SuppressWarnings("unchecked") Map<String,Object> address=read((String)o.get("ship_to_json"));
        if(address==null || address.isEmpty()) throw invalid("Ship-to address is required");
        String attributes=db.queryForObject("SELECT attributes_json FROM master_data_record WHERE organization_id=? AND resource_type='customers' AND id=?",String.class,org.toString(),o.get("customerId").toString());
        Map<String,Object> customer=read(attributes); Object limit=customer.get("creditLimit");
        if(limit!=null) {
            BigDecimal amount=db.queryForObject("SELECT COALESCE(SUM(quantity*COALESCE(unit_price,0)),0) FROM order_line WHERE order_id=?",BigDecimal.class,id.toString());
            if(amount.compareTo(new BigDecimal(limit.toString()))>0) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"CREDIT_LIMIT_EXCEEDED","Order total exceeds the customer credit limit");
        }
        // Reservation is owned by inventory. This event is the integration request; consumers must deduplicate by event ID.
        if(!"TRANSPORT_ONLY".equals(o.get("fulfillmentType"))) event(org,"InventoryReservationRequested",id,Map.of("orderId",id,"contractVersion",1));
    }
    private boolean allowed(String f,String t,String fulfillment) {
        return switch(f) {
            case "DRAFT" -> Set.of("CONFIRMED","CANCELLED").contains(t);
            case "CONFIRMED" -> Set.of("PLANNED","CANCELLED").contains(t);
            case "PLANNED" -> Set.of("PICKED","IN_TRANSIT","CANCELLED").contains(t);
            case "PICKED" -> Set.of("LOADED","CANCELLED").contains(t);
            case "LOADED" -> Set.of("IN_TRANSIT","CANCELLED").contains(t);
            case "IN_TRANSIT" -> Set.of("DELIVERED","FAILED").contains(t);
            case "FAILED" -> Set.of("PLANNED","RETURNED").contains(t);
            default -> false;
        };
    }
    private void validateMaster(UUID org,String type,UUID id) { if(db.queryForObject("SELECT COUNT(*) FROM master_data_record WHERE organization_id=? AND resource_type=? AND id=? AND status='ACTIVE'",Integer.class,org.toString(),type,id.toString())==0) throw invalid("Active "+type+" record is required: "+id); }
    private String nextNumber(UUID org,String key) {
        String scope=org.toString();
        db.update("INSERT INTO number_sequence(id,scope_id,sequence_key,prefix,current_value) VALUES(?,?,?,?,0) ON DUPLICATE KEY UPDATE sequence_key=VALUES(sequence_key)",UUID.randomUUID().toString(),scope,key,key+"-",0);
        db.update("UPDATE number_sequence SET current_value=LAST_INSERT_ID(current_value+1) WHERE scope_id=? AND sequence_key=?",scope,key);
        Long value=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class); return key+"-"+String.format("%08d",value);
    }
    private Map<String,Object> rawOrder(UUID org,UUID id) { return db.query("SELECT id,order_number orderNumber,customer_id customerId,status,fulfillment_type fulfillmentType,ship_to_json,currency,requested_delivery_at requestedDeliveryAt,version FROM sales_order WHERE id=? AND organization_id=?",rs->{ if(!rs.next()) throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found"); var m=new LinkedHashMap<String,Object>(); var md=rs.getMetaData(); for(int i=1;i<=md.getColumnCount();i++)m.put(md.getColumnLabel(i),rs.getObject(i)); m.put("shipTo",read((String)m.remove("ship_to_json"))); return m;},id.toString(),org.toString()); }
    private void history(UUID org,UUID id,String from,String to,UUID actor,String reason) { db.update("INSERT INTO order_status_history(id,organization_id,order_id,from_status,to_status,actor_id,reason) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),org.toString(),id.toString(),from,to,actor==null?null:actor.toString(),reason==null?"":reason); }
    private void event(UUID org,String type,UUID id,Map<String,Object> payload) { db.update("INSERT INTO outbox_event(id,organization_id,event_type,schema_version,aggregate_type,aggregate_id,payload) VALUES(?,?,?,1,'Order',?,?)",UUID.randomUUID().toString(),org.toString(),type,id.toString(),write(payload)); }
    private String write(Object value) { try{return json.writeValueAsString(value==null?Map.of():value);}catch(Exception e){throw invalid("Invalid JSON data");} }
    private Map<String,Object> read(String value) { try{return value==null?Map.of():json.readValue(value,new TypeReference<>(){});}catch(Exception e){return Map.of();} }
    private String norm(String s,String d){return s==null||s.isBlank()?d:s.trim().toUpperCase(Locale.ROOT);}
    private ApiException invalid(String m){return new ApiException(HttpStatus.BAD_REQUEST,"ORDER_INVALID",m);}
    public record CreateOrder(UUID customerId,String fulfillmentType,Map<String,Object> shipTo,String currency,Instant requestedDeliveryAt,List<Line> lines){}
    public record Line(UUID productId,String description,BigDecimal quantity,String unit,BigDecimal unitPrice){}
}
