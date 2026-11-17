package com.commoditycx.service;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Component
public class OutboxPublisher {
 private final JdbcTemplate db;private final KafkaTemplate<String,String> kafka;private final ObjectMapper json;private final boolean enabled;private final String topic;
 public OutboxPublisher(JdbcTemplate db,KafkaTemplate<String,String> kafka,ObjectMapper json,@Value("${commoditycx.events.enabled:false}") boolean enabled,@Value("${commoditycx.events.topic:commoditycx.events}") String topic){this.db=db;this.kafka=kafka;this.json=json;this.enabled=enabled;this.topic=topic;}
 @Scheduled(fixedDelayString="${commoditycx.events.poll-ms:1000}") @Transactional
 public void publishPending(){
  if(!enabled)return;
  List<Map<String,Object>> batch=db.queryForList("select * from event_outbox where published_at is null order by id limit 100 for update skip locked");
  for(Map<String,Object> event:batch){long id=((Number)event.get("id")).longValue();try{
   Map<String,Object> envelope=new LinkedHashMap<>();envelope.put("eventId",id);envelope.put("aggregateType",event.get("aggregate_type"));envelope.put("aggregateId",event.get("aggregate_id"));envelope.put("eventType",event.get("event_type"));envelope.put("occurredAt",event.get("created_at"));envelope.put("payload",json.readTree(event.get("payload").toString()));
   kafka.send(topic,event.get("aggregate_id").toString(),json.writeValueAsString(envelope)).get();
   db.update("update event_outbox set published_at=now(),attempts=attempts+1,last_error=null where id=?",id);
  }catch(Exception ex){String message=String.valueOf(ex.getMessage());db.update("update event_outbox set attempts=attempts+1,last_error=? where id=?",message.substring(0,Math.min(1000,message.length())),id);break;}}
 }
}
