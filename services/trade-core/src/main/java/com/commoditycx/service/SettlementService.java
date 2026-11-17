package com.commoditycx.service;
import com.commoditycx.security.TradingPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
@Service
public class SettlementService {
 private final JdbcTemplate db;
 public SettlementService(JdbcTemplate db){this.db=db;}
 @Transactional public Map<String,Object> instruct(UUID tradeId,TradingPrincipal actor) {
  Map<String,Object> t=one("select * from trades where id=? for update",tradeId);
  if(!"CONFIRMED".equals(t.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Both counterparties must confirm before settlement");
  UUID settlement=db.queryForObject("insert into settlement_instructions(trade_id,status) values (?,'INSTRUCTED') on conflict(trade_id) do update set updated_at=now() returning id",UUID.class,tradeId);
  Integer journals=db.queryForObject("select count(*) from ledger_journals where settlement_id=?",Integer.class,settlement);
  if(journals==null || journals==0) {
   BigDecimal amount=((BigDecimal)t.get("price")).multiply((BigDecimal)t.get("quantity"));String currency=(String)t.get("currency");
   long buyer=account("PARTICIPANT:"+t.get("buyer_participant_id")+":"+currency,"PARTICIPANT",(UUID)t.get("buyer_participant_id"),currency);
   long seller=account("PARTICIPANT:"+t.get("seller_participant_id")+":"+currency,"PARTICIPANT",(UUID)t.get("seller_participant_id"),currency);
   long clearing=account("CLEARING:"+currency,"CLEARING",null,currency);
   UUID journal=db.queryForObject("insert into ledger_journals(settlement_id,description) values (?,?) returning id",UUID.class,settlement,"Trade "+t.get("reference")+" settlement");
   post(journal,buyer,amount.negate(),currency);post(journal,clearing,amount,currency);
   post(journal,seller,amount,currency);post(journal,clearing,amount.negate(),currency);
   db.update("insert into event_outbox(aggregate_type,aggregate_id,event_type,payload) values ('SETTLEMENT',?,'SETTLEMENT_INSTRUCTED',jsonb_build_object('settlementId',?,'tradeId',?,'notional',?,'currency',?))",settlement.toString(),settlement,tradeId,amount,currency);
  }
  return db.queryForMap("select * from settlement_instructions where id=?",settlement);
 }
 @Transactional public Map<String,Object> markSettled(UUID id,String externalReference) {
  if(externalReference==null||externalReference.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"External settlement reference is required");
  int n=db.update("update settlement_instructions set status='SETTLED',external_reference=?,updated_at=now() where id=? and status='INSTRUCTED'",externalReference,id);
  if(n==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Settlement instruction not found or not instructed");
  db.update("insert into event_outbox(aggregate_type,aggregate_id,event_type,payload) values ('SETTLEMENT',?,'SETTLEMENT_COMPLETED',jsonb_build_object('settlementId',?,'externalReference',?))",id.toString(),id,externalReference);
  return db.queryForMap("select * from settlement_instructions where id=?",id);
 }
 private void post(UUID journal,long account,BigDecimal amount,String currency){db.update("insert into ledger_entries(journal_id,account_id,amount,currency) values (?,?,?,?)",journal,account,amount,currency);}
 private long account(String code,String type,UUID participant,String currency){
  return db.queryForObject("insert into ledger_accounts(account_code,account_type,participant_id,currency) values (?,?,?,?) on conflict(account_code) do update set account_code=excluded.account_code returning id",Long.class,code,type,participant,currency);
 }
 private Map<String,Object> one(String sql,Object id){List<Map<String,Object>> rows=db.queryForList(sql,id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Trade not found");return rows.get(0);}
}
