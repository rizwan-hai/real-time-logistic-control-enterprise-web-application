package com.example.logistics.shipments;

import com.example.logistics.shared.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service public class ShipmentService {
 private final JdbcTemplate db; private final ObjectMapper mapper;
 public ShipmentService(JdbcTemplate db,ObjectMapper mapper){this.db=db;this.mapper=mapper;}
 @Transactional public Map<String,Object> build(UUID org,UUID actor,List<UUID> orderIds){
  if(orderIds==null||orderIds.isEmpty())throw bad("Select at least one order");
  UUID id=UUID.randomUUID(); String no=next(org); db.update("INSERT INTO shipment(id,organization_id,shipment_number,status,created_by) VALUES(?,?,?,'PLANNED',?)",id.toString(),org.toString(),no,actor==null?null:actor.toString());
  int seq=0;
  for(UUID order:orderIds){var row=order(org,order);String status=(String)row.get("status"); if(!Set.of("CONFIRMED","PLANNED").contains(status))throw bad("Only confirmed or planned orders can be shipped");
   try{db.update("INSERT INTO shipment_order(shipment_id,order_id) VALUES(?,?)",id.toString(),order.toString());}catch(Exception e){throw new ApiException(HttpStatus.CONFLICT,"ORDER_ALREADY_ASSIGNED","Order is already assigned to a shipment");}
   db.update("UPDATE sales_order SET status='PLANNED',version=version+1 WHERE id=?",order.toString());
   String address=(String)row.get("ship_to_json"); db.update("INSERT INTO shipment_stop(id,shipment_id,sequence_no,stop_type,address_json,status) VALUES(?,?,?,'DELIVERY',?,'PENDING')",UUID.randomUUID().toString(),id.toString(),++seq,address);
   orderEvent(org,order,status,"PLANNED","Order assigned to shipment "+no);
  }
  event(org,id,"ShipmentPlanned",Map.of("shipmentId",id,"orderIds",orderIds)); return get(org,id);
 }
 @Transactional(readOnly=true) public List<Map<String,Object>> list(UUID org){return db.queryForList("SELECT id,shipment_number shipmentNumber,status,created_at createdAt FROM shipment WHERE organization_id=? ORDER BY created_at DESC",org.toString());}
 @Transactional(readOnly=true) public Map<String,Object> get(UUID org,UUID id){var rows=db.queryForList("SELECT id,shipment_number shipmentNumber,status,version,created_at createdAt FROM shipment WHERE id=? AND organization_id=?",id.toString(),org.toString());if(rows.isEmpty())throw missing();var m=new LinkedHashMap<>(rows.get(0));m.put("orders",db.queryForList("SELECT o.id,o.order_number orderNumber,o.status FROM shipment_order so JOIN sales_order o ON o.id=so.order_id WHERE so.shipment_id=?",id.toString()));m.put("stops",db.queryForList("SELECT id,sequence_no sequence,stop_type stopType,address_json address,status,scheduled_at scheduledAt FROM shipment_stop WHERE shipment_id=? ORDER BY sequence_no",id.toString()));return m;}
 @Transactional public Map<String,Object> stop(UUID org,UUID actor,UUID stopId,String to,String reason){var r=db.queryForList("SELECT s.id,s.status,s.shipment_id FROM shipment_stop s JOIN shipment h ON h.id=s.shipment_id WHERE s.id=? AND h.organization_id=?",stopId.toString(),org.toString());if(r.isEmpty())throw missing();String from=(String)r.get(0).get("status");to=to.toUpperCase(Locale.ROOT);if(!Map.of("PENDING",Set.of("ARRIVED","SKIPPED"),"ARRIVED",Set.of("COMPLETED","FAILED","SKIPPED")).getOrDefault(from,Set.of()).contains(to))throw new ApiException(HttpStatus.CONFLICT,"STOP_TRANSITION_INVALID","Stop transition is not allowed");db.update("UPDATE shipment_stop SET status=? WHERE id=?",to,stopId.toString());db.update("INSERT INTO shipment_stop_history(id,stop_id,from_status,to_status,actor_id,reason) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),stopId.toString(),from,to,actor==null?null:actor.toString(),reason==null?"":reason);UUID shipment=UUID.fromString((String)r.get(0).get("shipment_id"));event(org,shipment,"ShipmentStopStatusChanged",Map.of("stopId",stopId,"from",from,"to",to));return get(org,shipment);}
 private Map<String,Object> order(UUID org,UUID id){var l=db.queryForList("SELECT id,status,ship_to_json FROM sales_order WHERE id=? AND organization_id=?",id.toString(),org.toString());if(l.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found");return l.get(0);}
 private String next(UUID org){String scope=org.toString();db.update("INSERT INTO number_sequence(id,scope_id,sequence_key,prefix,current_value) VALUES(?,?, 'SHP','SHP-',0) ON DUPLICATE KEY UPDATE sequence_key=VALUES(sequence_key)",UUID.randomUUID().toString(),scope);db.update("UPDATE number_sequence SET current_value=LAST_INSERT_ID(current_value+1) WHERE scope_id=? AND sequence_key='SHP'",scope);return "SHP-"+String.format("%08d",db.queryForObject("SELECT LAST_INSERT_ID()",Long.class));}
 private void orderEvent(UUID org,UUID id,String from,String to,String reason){db.update("INSERT INTO order_status_history(id,organization_id,order_id,from_status,to_status,reason) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),org.toString(),id.toString(),from,to,reason);event(org,id,"OrderStatusChanged",Map.of("orderId",id,"from",from,"to",to,"reason",reason));}
 private void event(UUID org,UUID id,String type,Object payload){try{db.update("INSERT INTO outbox_event(id,organization_id,event_type,schema_version,aggregate_type,aggregate_id,payload) VALUES(?,?,?,1,'Shipment',?,?)",UUID.randomUUID().toString(),org.toString(),type,id.toString(),mapper.writeValueAsString(payload));}catch(Exception e){throw new IllegalStateException(e);}}
 private ApiException bad(String s){return new ApiException(HttpStatus.BAD_REQUEST,"SHIPMENT_INVALID",s);}private ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"SHIPMENT_NOT_FOUND","Shipment or stop not found");}
}
