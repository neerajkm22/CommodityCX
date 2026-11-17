package com.commoditycx.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
@Service
public class SurveillanceService {
 private final JdbcTemplate db;
 public SurveillanceService(JdbcTemplate db){this.db=db;}
 @Scheduled(fixedDelayString="${commoditycx.surveillance.poll-ms:60000}")
 @Transactional public void detect() {
  db.update("""
   INSERT INTO surveillance_alerts(rule_code,severity,participant_id,description)
   SELECT 'RAPID_CANCEL','MEDIUM',o.participant_id,'Participant cancelled at least 10 orders in the last 5 minutes'
   FROM orders o WHERE o.updated_at>now()-interval '5 minutes' AND o.status='CANCELLED'
   GROUP BY o.participant_id HAVING count(*)>=10
   AND NOT EXISTS (SELECT 1 FROM surveillance_alerts a WHERE a.rule_code='RAPID_CANCEL' AND a.participant_id=o.participant_id AND a.detected_at>now()-interval '5 minutes')
  """);
  db.update("""
   INSERT INTO surveillance_alerts(rule_code,severity,participant_id,description)
   SELECT 'COUNTERPARTY_CONCENTRATION','LOW',t.buyer_participant_id,'Five or more trades with the same counterparty in the last hour'
   FROM trades t WHERE t.created_at>now()-interval '1 hour'
   GROUP BY t.buyer_participant_id,t.seller_participant_id
   HAVING count(*)>=5 AND NOT EXISTS (SELECT 1 FROM surveillance_alerts a WHERE a.rule_code='COUNTERPARTY_CONCENTRATION' AND a.participant_id=t.buyer_participant_id AND a.detected_at>now()-interval '1 hour')
  """);
 }
 public List<Map<String,Object>> alerts(){return db.queryForList("select * from surveillance_alerts order by detected_at desc limit 500");}
 @Transactional public Map<String,Object> resolve(long id,String status,String note) {
  if(!List.of("CLOSED","ESCALATED","FALSE_POSITIVE").contains(status))throw new IllegalArgumentException("Invalid alert disposition");
  int n=db.update("update surveillance_alerts set status=?,resolution_note=? where id=? and status='OPEN'",status,note,id);
  if(n==0)throw new IllegalArgumentException("Open alert not found");
  return db.queryForMap("select * from surveillance_alerts where id=?",id);
 }
 @Transactional public Map<String,Object> generateReport(LocalDate from,LocalDate to,UUID actor) {
  if(to.isBefore(from) || to.isAfter(LocalDate.now()))throw new IllegalArgumentException("Invalid reporting period");
  List<Map<String,Object>> rows=reportRows(from,to);
  String payload;try{payload=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rows);}catch(Exception e){throw new IllegalStateException(e);}
  UUID id=db.queryForObject("insert into regulatory_reports(report_type,period_start,period_end,generated_by,record_count,payload) values ('TRADE_ACTIVITY',?,?,?,?,?::jsonb) returning id",UUID.class,from,to,actor,rows.size(),payload);
  return db.queryForMap("select * from regulatory_reports where id=?",id);
 }
 public List<Map<String,Object>> reportRows(LocalDate from,LocalDate to) {
  return db.queryForList("select reference,instrument_id,buyer_participant_id,seller_participant_id,price,quantity,currency,status,created_at from trades where created_at>=? and created_at<? order by created_at",from,to.plusDays(1));
 }
}
