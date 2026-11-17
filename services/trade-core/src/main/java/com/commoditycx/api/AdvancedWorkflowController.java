package com.commoditycx.api;
import com.commoditycx.security.TradingPrincipal;
import com.commoditycx.service.SettlementService;
import com.commoditycx.service.SurveillanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class AdvancedWorkflowController {
 private final JdbcTemplate db;private final SettlementService settlement;private final SurveillanceService surveillance;
 public AdvancedWorkflowController(JdbcTemplate db,SettlementService settlement,SurveillanceService surveillance){this.db=db;this.settlement=settlement;this.surveillance=surveillance;}
 @PostMapping("/rfqs/{rfqId}/negotiations") @Transactional
 public Map<String,Object> negotiate(@PathVariable UUID rfqId,@RequestBody Map<String,Object> in,@AuthenticationPrincipal TradingPrincipal actor){
  Map<String,Object> rfq=one("select * from rfqs where id=? and status in ('OPEN','QUOTED','NEGOTIATING') and expires_at>now() for update",rfqId);
  UUID participant=actor.participantId(),buyer=(UUID)rfq.get("buyer_participant_id");UUID invited=(UUID)rfq.get("seller_participant_id");
  if(participant==null||participant.equals(buyer)==false&&invited!=null&&!participant.equals(invited))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Participant is not entitled to negotiate this RFQ");
  Integer active=db.queryForObject("select count(*) from participants where id=? and status='ACTIVE'",Integer.class,participant);if(active==null||active==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Participant is not active");
  String type=String.valueOf(in.getOrDefault("type","COUNTER")).toUpperCase(Locale.ROOT);
  if(!List.of("OFFER","COUNTER","ACCEPT","REJECT","WITHDRAW").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported negotiation type");
  UUID tradeId=null;
  if(type.equals("ACCEPT")){
   List<Map<String,Object>> offers=db.queryForList("select * from negotiation_messages where rfq_id=? order by created_at desc limit 1 for update",rfqId);
   if(offers.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"There is no open counter-offer to accept");
   Map<String,Object> offer=offers.get(0);if(!List.of("OFFER","COUNTER").contains(offer.get("message_type"))||Objects.equals(offer.get("sender_participant_id"),participant))throw new ResponseStatusException(HttpStatus.CONFLICT,"The latest negotiation message is not an offer from the counterparty");UUID seller=Objects.equals(offer.get("sender_participant_id"),buyer)?participant:(UUID)offer.get("sender_participant_id");
   if(seller.equals(buyer))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid negotiation counterparty");
   if(invited!=null&&!invited.equals(seller))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Counterparty does not match RFQ invitation");
   Object price=offer.get("price"),quantity=offer.get("quantity");
   if(price==null||quantity==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Offer must contain price and quantity");
   if(new java.math.BigDecimal(quantity.toString()).compareTo((java.math.BigDecimal)rfq.get("quantity"))!=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Offer must cover the full RFQ quantity; partial RFQ fills are not enabled");
   tradeId=db.queryForObject("insert into trades(reference,instrument_id,buyer_participant_id,seller_participant_id,price,quantity,currency,source) values (?,?,?,?,?,?,?,'RFQ') returning id",UUID.class,"TRD-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT),rfq.get("instrument_id"),buyer,seller,price,quantity,offer.get("currency"));
   db.update("update rfqs set status='ACCEPTED',version=version+1 where id=?",rfqId);
   db.update("update quotes set status='REJECTED' where rfq_id=? and status='ACTIVE'",rfqId);
  } else if(type.equals("REJECT")) db.update("update rfqs set status='REJECTED',version=version+1 where id=?",rfqId);
  else if(type.equals("WITHDRAW")) {
   if(!participant.equals(buyer))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the RFQ initiator can withdraw the RFQ");
   db.update("update rfqs set status='CANCELLED',version=version+1 where id=?",rfqId);
  } else {
   if(in.get("price")==null||in.get("quantity")==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"OFFER/COUNTER requires price and quantity");
   if(new java.math.BigDecimal(in.get("price").toString()).signum()<=0||new java.math.BigDecimal(in.get("quantity").toString()).signum()<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Price and quantity must be positive");
   if(new java.math.BigDecimal(in.get("quantity").toString()).compareTo((java.math.BigDecimal)rfq.get("quantity"))!=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Partial RFQ fills are not enabled");
   db.update("update rfqs set status='NEGOTIATING',version=version+1 where id=?",rfqId);
  }
  UUID message=db.queryForObject("insert into negotiation_messages(rfq_id,sender_participant_id,message_type,price,quantity,currency,note) values (?,?,?,?,?,?,?) returning id",UUID.class,rfqId,participant,type,in.get("price"),in.get("quantity"),(in.get("currency")==null?rfq.get("currency"):in.get("currency")),in.get("note"));
  db.update("insert into event_outbox(aggregate_type,aggregate_id,event_type,payload) values ('RFQ',?,?,jsonb_build_object('rfqId',?,'messageId',?,'type',?,'tradeId',?))",rfqId.toString(),"RFQ_NEGOTIATION_"+type,rfqId,message,type,tradeId);
  Map<String,Object> result=new LinkedHashMap<>(db.queryForMap("select * from negotiation_messages where id=?",message));if(tradeId!=null)result.put("tradeId",tradeId);return result;
 }
 @GetMapping("/rfqs/{rfqId}/negotiations") public List<Map<String,Object>> history(@PathVariable UUID rfqId,@AuthenticationPrincipal TradingPrincipal actor){
  Integer allowed=db.queryForObject("select count(*) from rfqs r where r.id=? and (r.buyer_participant_id=? or r.seller_participant_id=? or (r.seller_participant_id is null and exists(select 1 from negotiation_messages n where n.rfq_id=r.id and n.sender_participant_id=?)))",Integer.class,rfqId,actor.participantId(),actor.participantId(),actor.participantId());
  if(allowed==null||allowed==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Not entitled to view this negotiation");
  return db.queryForList("select * from negotiation_messages where rfq_id=? order by created_at",rfqId);
 }
 @PostMapping("/trades/{tradeId}/settlement") @PreAuthorize("hasAnyRole('ADMIN','RISK_OFFICER','COMPLIANCE')")
 public Map<String,Object> settlement(@PathVariable UUID tradeId,@AuthenticationPrincipal TradingPrincipal actor){return settlement.instruct(tradeId,actor);}
 @PostMapping("/settlements/{id}/complete") @PreAuthorize("hasAnyRole('ADMIN','RISK_OFFICER')")
 public Map<String,Object> complete(@PathVariable UUID id,@RequestBody Map<String,String> in){return settlement.markSettled(id,in.get("externalReference"));}
 @GetMapping("/admin/surveillance/alerts") @PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE','RISK_OFFICER')")
 public List<Map<String,Object>> alerts(){return surveillance.alerts();}
 @PostMapping("/admin/surveillance/alerts/{id}/resolve") @PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE','RISK_OFFICER')")
 public Map<String,Object> resolve(@PathVariable long id,@RequestBody Map<String,String> in){return surveillance.resolve(id,in.getOrDefault("status","CLOSED"),in.get("note"));}
 @PostMapping("/admin/reports/trades") @PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE')")
 public Map<String,Object> report(@RequestBody Map<String,String> in,@AuthenticationPrincipal TradingPrincipal actor){return surveillance.generateReport(LocalDate.parse(in.get("from")),LocalDate.parse(in.get("to")),actor.userId());}
 @GetMapping(value="/admin/reports/trades.csv",produces="text/csv") @PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE')")
 public org.springframework.http.ResponseEntity<String> csv(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){
  List<Map<String,Object>> rows=surveillance.reportRows(from,to);StringBuilder b=new StringBuilder("reference,instrument_id,buyer_participant_id,seller_participant_id,price,quantity,currency,status,created_at\n");
  for(Map<String,Object> r:rows){List<String> cells=new ArrayList<>();for(Object value:r.values())cells.add("\""+String.valueOf(value).replace("\"","\"\"")+"\"");b.append(String.join(",",cells)).append("\n");}
  return org.springframework.http.ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=commoditycx-trades.csv").contentType(MediaType.parseMediaType("text/csv")).body(b.toString());
 }
 private Map<String,Object> one(String sql,Object id){List<Map<String,Object>> rows=db.queryForList(sql,id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"RFQ not found");return rows.get(0);}
}