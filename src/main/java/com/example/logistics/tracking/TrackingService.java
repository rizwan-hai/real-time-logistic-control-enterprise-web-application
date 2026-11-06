package com.example.logistics.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class TrackingService {
    private static final Logger log=LoggerFactory.getLogger(TrackingService.class);
    private static final DefaultRedisScript<Long> UPDATE_LATEST = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current then
              local ok, value = pcall(cjson.decode, current)
              if ok and tonumber(value.recordedAtEpochMillis or -1) > tonumber(ARGV[2]) then
                return 0
              end
            end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
            return 1
            """, Long.class);
    private final JdbcTemplate db; private final StringRedisTemplate redis; private final ObjectMapper json; private final SimpMessagingTemplate messages;
    private final TrackingAlertProperties alertProperties;
    private final boolean mysql;
    public TrackingService(JdbcTemplate db, StringRedisTemplate redis, ObjectMapper json,SimpMessagingTemplate messages,DataSource source,TrackingAlertProperties alertProperties) {
        this.db=db; this.redis=redis; this.json=json;this.messages=messages;this.alertProperties=alertProperties;
        try(var connection=source.getConnection()){mysql=connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");}
        catch(java.sql.SQLException ex){throw new IllegalStateException("Unable to identify tracking database platform",ex);}
    }

    public Device authenticate(String deviceId, String token) {
        String hash=sha(token);
        var rows=db.query("SELECT id,organization_id,vehicle_id,driver_id FROM tracking_device WHERE id=? AND credential_hash=? AND enabled=TRUE",(r,n)->new Device(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),deviceId,hash);
        if(rows.isEmpty()) throw new DeviceAuthenticationException();
        String key="tracking:rate:"+deviceId; Long count=redis.opsForValue().increment(key); if(count!=null&&count==1)redis.expire(key,Duration.ofSeconds(60));
        if(count!=null&&count>120) throw new RateLimitException();
        return rows.get(0);
    }

    public void checkIdempotency(Device device,List<Position> batch){Map<String,String> seen=new HashMap<>();for(Position p:batch){String digest=sha(p.toString());String local=seen.putIfAbsent(p.eventId(),digest);if(local!=null&&!local.equals(digest))throw new IllegalArgumentException("An eventId was reused with a different payload");var prior=db.query("SELECT request_digest FROM tracking_dedupe WHERE device_id=? AND event_id=?",(r,n)->r.getString(1),device.id(),p.eventId());if(!prior.isEmpty()&&!prior.get(0).equals(digest))throw new IllegalArgumentException("An eventId was reused with a different payload");}}
    public void validateTrip(Device device,String tripId){
        if(db.queryForObject("SELECT COUNT(*) FROM trip WHERE id=? AND organization_id=? AND vehicle_id=? AND status IN ('DISPATCHED','STARTED')",Integer.class,tripId,device.organizationId(),device.vehicleId())==0)
            throw new IllegalArgumentException("Trip is not assigned to this device vehicle");
    }

    public Map<String,Object> provisionDevice(String organizationId,String vehicleId,String driverId){
        if(db.queryForList("SELECT id FROM master_data_record WHERE id=? AND organization_id=? AND resource_type='vehicles' AND status='ACTIVE'",vehicleId,organizationId).isEmpty())throw new IllegalArgumentException("Active vehicle not found in organization");
        if(driverId!=null&&db.queryForList("SELECT id FROM master_data_record WHERE id=? AND organization_id=? AND resource_type='drivers' AND status='ACTIVE'",driverId,organizationId).isEmpty())throw new IllegalArgumentException("Active driver not found in organization");
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);String token=HexFormat.of().formatHex(secret);String id=UUID.randomUUID().toString();db.update("INSERT INTO tracking_device(id,organization_id,vehicle_id,driver_id,credential_hash) VALUES(?,?,?,?,?)",id,organizationId,vehicleId,driverId,sha(token));return Map.of("deviceId",id,"deviceToken",token,"vehicleId",vehicleId,"message","Store the token securely. It is shown only once.");
    }

    @Async("trackingExecutor") @Transactional
    public CompletableFuture<Integer> ingest(Device device, List<Position> batch) {
        db.queryForObject("SELECT id FROM tracking_device WHERE id=? FOR UPDATE",String.class,device.id());
        Instant received=Instant.now();List<Projection> projections=new ArrayList<>();List<Pending> pending=new ArrayList<>();Map<String,String> seen=new HashMap<>();
        for(Position p:batch) {
            String digest=sha(p.toString());
            String duplicate=seen.putIfAbsent(p.eventId(),digest);if(duplicate!=null){if(!duplicate.equals(digest))throw new IllegalArgumentException("An eventId was reused with a different payload");continue;}
            var prior=db.query("SELECT request_digest FROM tracking_dedupe WHERE device_id=? AND event_id=?",(r,n)->r.getString(1),device.id(),p.eventId());
            if(!prior.isEmpty()) { if(!prior.get(0).equals(digest)) throw new IllegalArgumentException("An eventId was reused with a different payload"); continue; }
            pending.add(new Pending(UUID.randomUUID().toString(),p,digest));
        }
        if(!pending.isEmpty()){
            db.batchUpdate("INSERT INTO tracking_dedupe(device_id,event_id,request_digest,received_at) VALUES(?,?,?,?)",pending,pending.size(),new org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<Pending>(){@Override public void setValues(java.sql.PreparedStatement ps,Pending row)throws java.sql.SQLException{ps.setString(1,device.id());ps.setString(2,row.position().eventId());ps.setString(3,row.digest());ps.setTimestamp(4,Timestamp.from(received));}});
            String columns="id,device_id,organization_id,vehicle_id,trip_id,driver_id,latitude,longitude,speed_kph,heading,accuracy_m,battery_percent,source,recorded_at,received_at";
            String values="?,?,?,?,?,?,?,?,?,?,?,?,?,?,?";
            if(mysql){columns+=",location";values+=",ST_SRID(POINT(?,?),4326)";}
            db.batchUpdate("INSERT INTO gps_position("+columns+") VALUES("+values+")",pending,pending.size(),new org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<Pending>(){@Override public void setValues(java.sql.PreparedStatement ps,Pending row)throws java.sql.SQLException{Position p=row.position();ps.setString(1,row.id());ps.setString(2,device.id());ps.setString(3,device.organizationId());ps.setString(4,device.vehicleId());ps.setString(5,p.tripId());ps.setString(6,device.driverId());ps.setDouble(7,p.latitude());ps.setDouble(8,p.longitude());ps.setObject(9,p.speedKph());ps.setObject(10,p.heading());ps.setObject(11,p.accuracyM());ps.setObject(12,p.batteryPercent());ps.setString(13,p.source());ps.setTimestamp(14,Timestamp.from(p.recordedAt()));ps.setTimestamp(15,Timestamp.from(received));if(mysql){ps.setDouble(16,p.longitude());ps.setDouble(17,p.latitude());}}});
        }
        pending.sort(Comparator.comparing(row->row.position().recordedAt()));
        for(Pending row:pending){Position p=row.position();
            String status=p.speedKph()!=null&&p.speedKph()>3?"MOVING":"STOPPED";
            var latestData=new LinkedHashMap<String,Object>();latestData.put("vehicleId",device.vehicleId());latestData.put("tripId",p.tripId());latestData.put("latitude",p.latitude());latestData.put("longitude",p.longitude());latestData.put("speedKph",p.speedKph());latestData.put("recordedAt",p.recordedAt());latestData.put("recordedAtEpochMillis",p.recordedAt().toEpochMilli());latestData.put("status",status);
            String latest;
            try{latest=json.writeValueAsString(latestData);}catch(Exception ex){throw new IllegalStateException("Unable to serialize latest vehicle position",ex);}
            var event=new LinkedHashMap<String,Object>();event.put("vehicleId",device.vehicleId());event.put("tripId",p.tripId());event.put("latitude",p.latitude());event.put("longitude",p.longitude());event.put("speedKph",p.speedKph());event.put("recordedAt",p.recordedAt());event.put("status",status);
            if(p.tripId()!=null)event.put("eta",eta(p.tripId(),p.latitude(),p.longitude(),p.speedKph()==null?0:p.speedKph()));
            projections.add(new Projection(device.vehicleId(),device.organizationId(),p.tripId(),latest,event));evaluateGeofences(device,p,received);evaluateAlertRules(device,p);
        }
        db.update("UPDATE tracking_device SET last_seen_at=? WHERE id=?",received,device.id());
        resolveOfflineAlert(device,received);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){for(Projection p:projections){String key="tracking:latest:"+p.vehicleId();try{Long updated=redis.execute(UPDATE_LATEST,List.of(key),p.json(),String.valueOf(((Instant)p.event().get("recordedAt")).toEpochMilli()),"86400");if(updated==null||updated==0)continue;}catch(Exception ex){log.error("Unable to update Redis latest-position projection for vehicle {}",p.vehicleId(),ex);continue;}messages.convertAndSend("/topic/tracking/vehicles/"+p.organizationId(),p.event());if(p.tripId()!=null)messages.convertAndSend("/topic/tracking/trips/"+p.tripId(),p.event());}}});
        return CompletableFuture.completedFuture(pending.size());
    }

    private void evaluateGeofences(Device device,Position p,Instant received){
        var fences=db.query("SELECT id,name,kind,shape_json,debounce_seconds FROM geofence WHERE organization_id=? AND enabled=TRUE",(r,n)->new Object[]{r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5)},device.organizationId());
        for(Object[] f:fences){
            String id=(String)f[0],name=(String)f[1],kind=(String)f[2];com.fasterxml.jackson.databind.JsonNode shape;
            try{shape=json.readTree((String)f[3]);}catch(java.io.IOException ex){throw new IllegalStateException("Persisted geofence shape is invalid for "+id,ex);}
            boolean inside;
            try{inside=GeofenceGeometry.contains(kind,shape,p.latitude(),p.longitude());}
            catch(IllegalArgumentException ex){throw new IllegalStateException("Persisted geofence is invalid: "+id,ex);}
            var prior=db.query("SELECT inside,changed_at,candidate_inside,candidate_since FROM geofence_state WHERE vehicle_id=? AND geofence_id=?",(r,n)->new Object[]{r.getBoolean(1),r.getTimestamp(2).toInstant(),(Boolean)r.getObject(3),r.getTimestamp(4)==null?null:r.getTimestamp(4).toInstant()},device.vehicleId(),id);
            if(prior.isEmpty()){db.update("INSERT INTO geofence_state(vehicle_id,geofence_id,inside,changed_at) VALUES(?,?,?,?)",device.vehicleId(),id,inside,received);if(inside)geofenceAlert(device,p,name,"ENTER");continue;}
            boolean was=(boolean)prior.get(0)[0];Boolean candidate=(Boolean)prior.get(0)[2];Instant candidateSince=(Instant)prior.get(0)[3];
            if(was==inside){if(candidate!=null)db.update("UPDATE geofence_state SET candidate_inside=NULL,candidate_since=NULL WHERE vehicle_id=? AND geofence_id=?",device.vehicleId(),id);}
            else if(candidate==null||candidate!=inside){db.update("UPDATE geofence_state SET candidate_inside=?,candidate_since=? WHERE vehicle_id=? AND geofence_id=?",inside,received,device.vehicleId(),id);}
            else if(candidateSince!=null&&Duration.between(candidateSince,received).getSeconds()>=(int)f[4]){db.update("UPDATE geofence_state SET inside=?,changed_at=?,candidate_inside=NULL,candidate_since=NULL WHERE vehicle_id=? AND geofence_id=?",inside,received,device.vehicleId(),id);geofenceAlert(device,p,name,inside?"ENTER":"EXIT");}
        }
    }
    private void geofenceAlert(Device d,Position p,String name,String transition){String id=UUID.randomUUID().toString();String message="Vehicle crossed geofence '"+name+"' ("+transition+")";db.update("INSERT INTO tracking_alert(id,organization_id,vehicle_id,trip_id,rule,severity,message) VALUES(?,?,?,?,?,?,?)",id,d.organizationId(),d.vehicleId(),p.tripId(),"GEOFENCE_BREACH","WARNING",message);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){messages.convertAndSend("/topic/tracking/alerts/"+d.organizationId(),Map.of("id",id,"vehicleId",d.vehicleId(),"rule","GEOFENCE_BREACH","severity","WARNING","message",message));}});}
    private void evaluateAlertRules(Device device,Position position){
        if(position.speedKph()!=null&&position.speedKph()>alertProperties.speedingKph())
            createAlert(device,position.tripId(),"SPEEDING",position.speedKph()>alertProperties.speedingKph()*1.25?"CRITICAL":"WARNING","Vehicle speed exceeded the configured limit");
        var previous=db.query("SELECT recorded_at,speed_kph FROM gps_position WHERE vehicle_id=? AND recorded_at<? ORDER BY recorded_at DESC LIMIT 1",
                (r,n)->new Object[]{r.getTimestamp(1).toInstant(),r.getObject(2)},device.vehicleId(),position.recordedAt());
        if(position.speedKph()!=null&&position.speedKph()<=3&&!previous.isEmpty()){
            Object[] prior=previous.get(0);
            if(prior[1]!=null&&((Number)prior[1]).doubleValue()<=3
                    &&Duration.between((Instant)prior[0],position.recordedAt()).toMinutes()>=alertProperties.idleMinutes())
                createAlert(device,position.tripId(),"IDLE_VEHICLE","WARNING","Vehicle has remained stationary beyond the configured threshold");
        }
        if(position.tripId()!=null){
            double deviation=routeDeviationKm(position.tripId(),position.latitude(),position.longitude());
            if(deviation>alertProperties.routeDeviationKm())
                createAlert(device,position.tripId(),"ROUTE_DEVIATION","WARNING","Vehicle is outside the configured route deviation distance");
            Map<String,Object> eta=eta(position.tripId(),position.latitude(),position.longitude(),position.speedKph()==null?0:position.speedKph());
            Object delay=eta.get("deliveryWindowDeviationMinutes");
            if(delay instanceof Number minutes&&minutes.longValue()>=alertProperties.lateDeliveryRiskMinutes())
                createAlert(device,position.tripId(),"LATE_DELIVERY_RISK","WARNING","Estimated delivery is at risk of missing its delivery window");
        }
    }
    private double routeDeviationKm(String tripId,double latitude,double longitude){
        var stops=db.query("SELECT latitude,longitude FROM trip_stop WHERE trip_id=? AND status<>'COMPLETED' AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY sequence_no",
                (r,n)->new double[]{r.getDouble(1),r.getDouble(2)},tripId);
        if(stops.size()<2)return 0;
        double nearest=Double.POSITIVE_INFINITY;
        for(int i=1;i<stops.size();i++)nearest=Math.min(nearest,pointToSegmentKm(latitude,longitude,stops.get(i-1),stops.get(i)));
        return nearest;
    }
    private static double pointToSegmentKm(double latitude,double longitude,double[] start,double[] end){
        double scaleX=111.32*Math.cos(Math.toRadians(latitude)),scaleY=110.57;
        double px=0,py=0,x1=(start[1]-longitude)*scaleX,y1=(start[0]-latitude)*scaleY;
        double x2=(end[1]-longitude)*scaleX,y2=(end[0]-latitude)*scaleY;
        double dx=x2-x1,dy=y2-y1,lengthSquared=dx*dx+dy*dy;
        double t=lengthSquared==0?0:Math.max(0,Math.min(1,((px-x1)*dx+(py-y1)*dy)/lengthSquared));
        return Math.hypot(px-(x1+t*dx),py-(y1+t*dy));
    }
    private void createAlert(Device device,String tripId,String rule,String severity,String message){
        if(db.queryForObject("SELECT COUNT(*) FROM tracking_alert WHERE organization_id=? AND vehicle_id=? AND rule=? AND status IN ('OPEN','ACKNOWLEDGED')",
                Integer.class,device.organizationId(),device.vehicleId(),rule)>0)return;
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO tracking_alert(id,organization_id,vehicle_id,trip_id,rule,severity,message) VALUES(?,?,?,?,?,?,?)",
                id,device.organizationId(),device.vehicleId(),tripId,rule,severity,message);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){
            messages.convertAndSend("/topic/tracking/alerts/"+device.organizationId(),Map.of("id",id,"vehicleId",device.vehicleId(),"tripId",tripId==null?"":tripId,"rule",rule,"severity",severity,"status","OPEN","message",message));
        }});
    }
    private void resolveOfflineAlert(Device device,Instant now){
        var alerts=db.query("SELECT id FROM tracking_alert WHERE organization_id=? AND vehicle_id=? AND rule='DEVICE_OFFLINE' AND status IN ('OPEN','ACKNOWLEDGED')",
                (r,n)->r.getString(1),device.organizationId(),device.vehicleId());
        for(String alertId:alerts){
            db.update("UPDATE tracking_alert SET status='RESOLVED',resolved_at=? WHERE id=?",now,alertId);
            db.update("INSERT INTO tracking_alert_history(id,alert_id,action,note,occurred_at) VALUES(?,?,?,?,?)",UUID.randomUUID().toString(),alertId,"RESOLVE","Automatically resolved when device reported a new position",now);
        }
    }
    @Scheduled(fixedDelayString="${tracking.alerts.sweep-interval-millis:60000}")
    @Transactional
    public void evaluateStaleDeviceAlerts(){
        Instant now=Instant.now();
        var devices=db.query("SELECT id,organization_id,vehicle_id,last_seen_at FROM tracking_device WHERE enabled=TRUE AND last_seen_at IS NOT NULL",
                (r,n)->new Object[]{r.getString(1),r.getString(2),r.getString(3),r.getTimestamp(4).toInstant()});
        for(Object[] device:devices){
            String id=(String)device[0],organizationId=(String)device[1],vehicleId=(String)device[2];
            Instant lastSeen=(Instant)device[3];
            if(Duration.between(lastSeen,now).toMinutes()>=alertProperties.deviceOfflineMinutes())
                createAlert(new Device(id,organizationId,vehicleId,null),null,"DEVICE_OFFLINE","CRITICAL","Tracking device has stopped reporting positions");
            var stopped=db.query("SELECT trip_id,recorded_at,speed_kph FROM gps_position WHERE organization_id=? AND vehicle_id=? ORDER BY recorded_at DESC LIMIT 1",
                    (r,n)->new Object[]{r.getString(1),r.getTimestamp(2).toInstant(),r.getObject(3)},organizationId,vehicleId);
            if(!stopped.isEmpty()&&stopped.get(0)[2]!=null&&((Number)stopped.get(0)[2]).doubleValue()<=3
                    &&Duration.between((Instant)stopped.get(0)[1],now).toMinutes()>=alertProperties.idleMinutes())
                createAlert(new Device(id,organizationId,vehicleId,null),(String)stopped.get(0)[0],"IDLE_VEHICLE","WARNING","Vehicle has remained stationary beyond the configured threshold");
        }
    }
    private static double haversine(double a,double b,double c,double d){double x=Math.toRadians(c-a),y=Math.toRadians(d-b),h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(y/2)*Math.sin(y/2);return 6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}

    public List<Map<String,Object>> latest(String organizationId) {
        var vehicles=db.query("SELECT DISTINCT vehicle_id FROM gps_position WHERE organization_id=? AND recorded_at>=?",(r,n)->r.getString(1),organizationId,Instant.now().minus(Duration.ofHours(24)));
        List<Map<String,Object>> result=new ArrayList<>(); for(String vehicle:vehicles){String value=null;try{value=redis.opsForValue().get("tracking:latest:"+vehicle);}catch(Exception ex){log.warn("Redis latest-position lookup failed; falling back to the GPS store for vehicle {}",vehicle,ex);}if(value!=null)try{result.add(json.readValue(value,Map.class));continue;}catch(Exception ex){log.warn("Ignoring malformed Redis latest-position value for vehicle {}",vehicle,ex);}var rows=db.query("SELECT latitude,longitude,speed_kph,recorded_at,trip_id FROM gps_position WHERE organization_id=? AND vehicle_id=? ORDER BY recorded_at DESC LIMIT 1",(r,n)->{var v=new LinkedHashMap<String,Object>();Instant recorded=r.getTimestamp(4).toInstant();v.put("vehicleId",vehicle);v.put("latitude",r.getBigDecimal(1));v.put("longitude",r.getBigDecimal(2));v.put("speedKph",r.getObject(3));v.put("recordedAt",recorded.toString());v.put("recordedAtEpochMillis",recorded.toEpochMilli());v.put("tripId",r.getString(5));v.put("status",r.getDouble(3)>3?"MOVING":"STOPPED");return v;},organizationId,vehicle);if(!rows.isEmpty()){var v=rows.get(0);result.add(v);try{redis.opsForValue().set("tracking:latest:"+vehicle,json.writeValueAsString(v),Duration.ofHours(24));}catch(Exception ex){log.error("Unable to rebuild Redis latest-position projection for vehicle {}",vehicle,ex);}}}
        return result;
    }

    public List<Map<String,Object>> geofences(String organizationId){return db.queryForList("SELECT id,name,kind,shape_json shapeJson,enabled,debounce_seconds debounceSeconds FROM geofence WHERE organization_id=? ORDER BY name",organizationId);}
    public Map<String,Object> createGeofence(String organizationId,String name,String kind,String shape,int debounce){
        if(!Set.of("CIRCLE","POLYGON").contains(kind))throw new IllegalArgumentException("Geofence kind must be CIRCLE or POLYGON");
        try{var body=json.readTree(shape);GeofenceGeometry.validate(kind,body);}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Invalid geofence shape JSON");}
        String id=UUID.randomUUID().toString();db.update("INSERT INTO geofence(id,organization_id,name,kind,shape_json,debounce_seconds) VALUES(?,?,?,?,?,?)",id,organizationId,name,kind,shape,debounce);return geofences(organizationId).stream().filter(g->id.equals(g.get("id"))).findFirst().orElseThrow();
    }
    public List<Map<String,Object>> alerts(String organizationId){return db.queryForList("SELECT id,vehicle_id vehicleId,trip_id tripId,rule,severity,status,message,assigned_to assignedTo,acknowledged_at acknowledgedAt,resolved_at resolvedAt,created_at createdAt FROM tracking_alert WHERE organization_id=? ORDER BY FIELD(status,'OPEN','ACKNOWLEDGED','RESOLVED'),created_at DESC LIMIT 500",organizationId);}
    public List<Map<String,Object>> alertHistory(String organizationId,String alertId){
        if(db.queryForObject("SELECT COUNT(*) FROM tracking_alert WHERE id=? AND organization_id=?",Integer.class,alertId,organizationId)==0)
            throw new IllegalArgumentException("Alert not found");
        return db.queryForList("SELECT h.id,h.actor_id actorId,h.action,h.note,h.occurred_at occurredAt FROM tracking_alert_history h WHERE h.alert_id=? ORDER BY h.occurred_at",alertId);
    }
    @Transactional public Map<String,Object> assignAlert(String organizationId,String alertId,String actor,String assignedTo){
        if(db.queryForObject("SELECT COUNT(*) FROM tracking_alert WHERE id=? AND organization_id=?",Integer.class,alertId,organizationId)==0)
            throw new IllegalArgumentException("Alert not found");
        if(assignedTo!=null&&db.queryForObject("SELECT COUNT(*) FROM user_account WHERE id=? AND organization_id=? AND active=TRUE",Integer.class,assignedTo,organizationId)==0)
            throw new IllegalArgumentException("Assignee is not active in this organization");
        db.update("UPDATE tracking_alert SET assigned_to=? WHERE id=? AND organization_id=?",assignedTo,alertId,organizationId);
        db.update("INSERT INTO tracking_alert_history(id,alert_id,actor_id,action,note) VALUES(?,?,?,?,?)",UUID.randomUUID().toString(),alertId,actor,"ASSIGN",assignedTo);
        return alerts(organizationId).stream().filter(a->alertId.equals(a.get("id"))).findFirst().orElseThrow();
    }
    @Transactional public Map<String,Object> alertAction(String organizationId,String alertId,String actor,String action,String note){
        String next=switch(action){case "ACKNOWLEDGE"->"ACKNOWLEDGED";case "RESOLVE"->"RESOLVED";default->throw new IllegalArgumentException("Action must be ACKNOWLEDGE or RESOLVE");};
        int changed=next.equals("ACKNOWLEDGED")?db.update("UPDATE tracking_alert SET status=?,acknowledged_at=CURRENT_TIMESTAMP(6) WHERE id=? AND organization_id=? AND status='OPEN'",next,alertId,organizationId):db.update("UPDATE tracking_alert SET status=?,resolved_at=CURRENT_TIMESTAMP(6) WHERE id=? AND organization_id=? AND status IN ('OPEN','ACKNOWLEDGED')",next,alertId,organizationId);
        if(changed==0)throw new IllegalArgumentException("Alert is missing or cannot transition from its current status");
        db.update("INSERT INTO tracking_alert_history(id,alert_id,actor_id,action,note) VALUES(?,?,?,?,?)",UUID.randomUUID().toString(),alertId,actor,action,note);
        return alerts(organizationId).stream().filter(a->alertId.equals(a.get("id"))).findFirst().orElseThrow();
    }

    public Map<String,Object> issuePublicToken(String organizationId,String shipmentId,int days){
        if(days<1||days>30)throw new IllegalArgumentException("Public token expiration must be 1 to 30 days");
        if(db.queryForList("SELECT id FROM shipment WHERE id=? AND organization_id=?",shipmentId,organizationId).isEmpty())throw new IllegalArgumentException("Shipment not found");
        byte[] raw=new byte[32];new java.security.SecureRandom().nextBytes(raw);String token=HexFormat.of().formatHex(raw);String id=UUID.randomUUID().toString();Instant expiry=Instant.now().plus(Duration.ofDays(days));
        db.update("INSERT INTO public_tracking_token(id,shipment_id,token_hash,expires_at) VALUES(?,?,?,?)",id,shipmentId,sha(token),expiry);
        return Map.of("linkId",id,"token",token,"url","/tracking/"+token,"expiresAt",expiry.toString());
    }
    @Transactional public Map<String,Object> revokePublicToken(String organizationId,String shipmentId,String linkId){
        int changed=db.update("UPDATE public_tracking_token SET revoked=TRUE WHERE id=? AND shipment_id=? AND EXISTS (SELECT 1 FROM shipment WHERE id=? AND organization_id=?)",
                linkId,shipmentId,shipmentId,organizationId);
        if(changed==0)throw new IllegalArgumentException("Public tracking link not found");
        return Map.of("linkId",linkId,"revoked",true);
    }
    public Map<String,Object> publicShipment(String token){
        String hash=sha(token);var rows=db.query("SELECT p.shipment_id,s.shipment_number,s.status,p.expires_at,p.revoked FROM public_tracking_token p JOIN shipment s ON s.id=p.shipment_id WHERE p.token_hash=?",(r,n)->new Object[]{r.getString(1),r.getString(2),r.getString(3),r.getTimestamp(4).toInstant(),r.getBoolean(5)},hash);
        if(rows.isEmpty()||(boolean)rows.get(0)[4]||((Instant)rows.get(0)[3]).isBefore(Instant.now()))throw new IllegalArgumentException("Tracking link is invalid or expired");
        String rateKey="tracking:public-rate:"+hash;Long hits=redis.opsForValue().increment(rateKey);if(hits!=null&&hits==1)redis.expire(rateKey,Duration.ofMinutes(1));if(hits!=null&&hits>30)throw new RateLimitException();
        String shipment=(String)rows.get(0)[0];var locations=db.query("SELECT g.trip_id,g.latitude,g.longitude,g.recorded_at FROM gps_position g JOIN trip_shipment ts ON ts.trip_id=g.trip_id WHERE ts.shipment_id=? AND g.recorded_at>=? ORDER BY g.recorded_at DESC LIMIT 1",(r,n)->new Object[]{r.getString(1),r.getDouble(2),r.getDouble(3),r.getTimestamp(4).toInstant()},shipment,Instant.now().minus(Duration.ofMinutes(15)));
        var result=new LinkedHashMap<String,Object>();result.put("shipmentNumber",rows.get(0)[1]);result.put("status",rows.get(0)[2]);result.put("eta",null);        if(!locations.isEmpty()){Object[] l=locations.get(0);result.put("location",Map.of("latitude",Math.round((double)l[1]*1000.0)/1000.0,"longitude",Math.round((double)l[2]*1000.0)/1000.0,"recordedAt",l[3]));result.put("eta",eta((String)l[0],(double)l[1],(double)l[2]).get("estimatedArrival"));}else result.put("location",null);return result;
    }
    public Map<String,Object> eta(String organizationId,String tripId){var rows=db.query("SELECT g.latitude,g.longitude,g.speed_kph FROM gps_position g WHERE g.organization_id=? AND g.trip_id=? ORDER BY g.recorded_at DESC LIMIT 1",(r,n)->new Object[]{r.getDouble(1),r.getDouble(2),r.getDouble(3)},organizationId,tripId);if(rows.isEmpty())throw new IllegalArgumentException("Trip has no current position");return eta(tripId,(double)rows.get(0)[0],(double)rows.get(0)[1],(double)rows.get(0)[2]);}
    public List<Map<String,Object>> tripHistory(String organizationId,String tripId,Instant from,Instant to){
        if(from==null||to==null||!from.isBefore(to)||Duration.between(from,to).compareTo(Duration.ofDays(7))>0)
            throw new IllegalArgumentException("History range must be positive and no longer than 7 days");
        if(db.queryForObject("SELECT COUNT(*) FROM trip WHERE id=? AND organization_id=?",Integer.class,tripId,organizationId)==0)
            throw new IllegalArgumentException("Trip not found");
        return db.query("SELECT latitude,longitude,speed_kph,heading,recorded_at FROM gps_position WHERE organization_id=? AND trip_id=? AND recorded_at>=? AND recorded_at<? ORDER BY recorded_at LIMIT 5000",
                (r,n)->{var position=new LinkedHashMap<String,Object>();position.put("latitude",r.getBigDecimal(1));position.put("longitude",r.getBigDecimal(2));position.put("speedKph",r.getObject(3));position.put("heading",r.getObject(4));position.put("recordedAt",r.getTimestamp(5).toInstant());return position;},
                organizationId,tripId,from,to);
    }
    private Map<String,Object> eta(String tripId,double lat,double lon){return eta(tripId,lat,lon,0);}
    private Map<String,Object> eta(String tripId,double lat,double lon,double observedSpeed){
        var stops=db.query("SELECT latitude,longitude,window_end FROM trip_stop WHERE trip_id=? AND status<>'COMPLETED' AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY sequence_no",(r,n)->new Object[]{r.getDouble(1),r.getDouble(2),r.getTimestamp(3)},tripId);
        if(stops.isEmpty())return Map.of("remainingDistanceKm",0,"remainingDurationMinutes",0,"estimatedArrival",Instant.now().toString(),"predictedDelay",false);
        double distance=0;for(Object[] stop:stops){distance+=haversine(lat,lon,(double)stop[0],(double)stop[1])/1000.0;lat=(double)stop[0];lon=(double)stop[1];}
        double speed=Math.max(10,Math.min(100,observedSpeed>2?observedSpeed:40));long minutes=(long)Math.ceil(distance/speed*60);Instant arrival=Instant.now().plus(Duration.ofMinutes(minutes));Timestamp window=(Timestamp)stops.get(stops.size()-1)[2];long deviation=window==null?0:Duration.between(window.toInstant(),arrival).toMinutes();
        return Map.of("remainingDistanceKm",Math.round(distance*10.0)/10.0,"remainingDurationMinutes",minutes,"estimatedArrival",arrival.toString(),"deliveryWindowDeviationMinutes",deviation,"predictedDelay",deviation>0,"speedKph",speed);
    }

    public static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record Device(String id,String organizationId,String vehicleId,String driverId){}
    public record Position(String eventId,Instant recordedAt,double latitude,double longitude,Double speedKph,Double heading,Double accuracyM,Integer batteryPercent,String source,String tripId){}
    private record Projection(String vehicleId,String organizationId,String tripId,String json,Map<String,Object> event){}
    private record Pending(String id,Position position,String digest){}
    public static class RateLimitException extends RuntimeException {}
    public static class DeviceAuthenticationException extends RuntimeException {}
}
