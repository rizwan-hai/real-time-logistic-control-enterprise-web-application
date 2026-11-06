package com.example.logistics.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class TrackingMaintenance {
    private static final Logger log=LoggerFactory.getLogger(TrackingMaintenance.class);
    private final JdbcTemplate db;private final DataSource source;
    public TrackingMaintenance(JdbcTemplate db,DataSource source){this.db=db;this.source=source;}

    @Scheduled(cron="0 20 2 * * *",zone="UTC")
    public void maintain(){
        try(var c=source.getConnection()){
            if(!c.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"))return;
            maintainPartitions();downsample();retainRaw();
        }catch(Exception ex){log.error("GPS maintenance failed",ex);}
    }
    private void maintainPartitions() throws Exception {
        var rows=db.queryForList("SELECT PARTITION_NAME FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='gps_position' AND PARTITION_NAME IS NOT NULL");
        var names=rows.stream().map(r->String.valueOf(r.get("PARTITION_NAME"))).toList();
        YearMonth latest=names.stream().filter(n->n.matches("p\\d{6}")).map(n->YearMonth.parse(n.substring(1),DateTimeFormatter.ofPattern("yyyyMM"))).max(YearMonth::compareTo).orElse(YearMonth.now(ZoneOffset.UTC));
        YearMonth through=YearMonth.now(ZoneOffset.UTC).plusMonths(3);
        while(latest.isBefore(through)){
            YearMonth next=latest.plusMonths(1);String partition="p"+next.format(DateTimeFormatter.ofPattern("yyyyMM"));String boundary=next.plusMonths(1).atDay(1).toString();
            db.execute("ALTER TABLE gps_position REORGANIZE PARTITION pmax INTO (PARTITION "+partition+" VALUES LESS THAN (TO_DAYS('"+boundary+"')), PARTITION pmax VALUES LESS THAN MAXVALUE)");latest=next;
        }
    }
    private void downsample(){
        db.update("INSERT IGNORE INTO gps_position_hourly(vehicle_id,trip_id,bucket_start,latitude,longitude,avg_speed_kph,sample_count) SELECT vehicle_id,COALESCE(trip_id,''),FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(recorded_at)/3600)*3600),AVG(latitude),AVG(longitude),AVG(speed_kph),COUNT(*) FROM gps_position WHERE recorded_at<UTC_TIMESTAMP()-INTERVAL 90 DAY GROUP BY vehicle_id,COALESCE(trip_id,''),FLOOR(UNIX_TIMESTAMP(recorded_at)/3600)");
    }
    private void retainRaw(){
        int deleted=0;
        boolean capped=true;
        for(int i=0;i<100;i++){int batch=db.update("DELETE FROM gps_position WHERE recorded_at<UTC_TIMESTAMP()-INTERVAL 90 DAY LIMIT 5000");deleted+=batch;if(batch<5000){capped=false;break;}}
        if(capped)log.warn("GPS raw retention reached its daily deletion cap; deleted {} rows and will continue on the next maintenance run",deleted);
        db.update("DELETE FROM tracking_dedupe WHERE received_at<UTC_TIMESTAMP()-INTERVAL 30 DAY LIMIT 5000");
    }
}
