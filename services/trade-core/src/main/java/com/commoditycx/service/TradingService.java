package com.commoditycx.service;

import com.commoditycx.security.TradingPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TradingService {
    private final JdbcTemplate db;
    public TradingService(JdbcTemplate db){this.db=db;}
    @Transactional public Map<String,Object> createInstrument(Map<String,Object> in, TradingPrincipal actor) {
        UUID id=db.queryForObject("insert into instruments(symbol,name,commodity_type,grade,location,unit,currency,tick_size,min_quantity) values (?,?,?,?,?,?,?,?,?) returning id",
            UUID.class, req(in,"symbol"),req(in,"name"),req(in,"commodityType"),in.get("grade"),in.get("location"),
            req(in,"unit"),req(in,"currency"),decimal(in,"tickSize"),decimal(in,"minQuantity"));
        audit(actor,"INSTRUMENT_CREATED","INSTRUMENT",id.toString(),in);
        return db.queryForMap("select * from instruments where id=?",id);
    }
    public List<Map<String,Object>> instruments(){return db.queryForList("select * from instruments order by symbol");}
    @Transactional public Map<String,Object> createRfq(Map<String,Object> in,TradingPrincipal actor) {
        UUID participant=actor.participantId(); if(participant==null) bad("User is not linked to a participant");
        requireActiveParticipant(participant);
        if(!"BUY".equals(side(in))) bad("This RFQ workflow currently supports buyer-initiated RFQs only");
        if(!OffsetDateTime.parse(req(in,"expiresAt")).isAfter(OffsetDateTime.now())) bad("RFQ expiry must be in the future");
        UUID id=db.queryForObject("insert into rfqs(reference,instrument_id,buyer_participant_id,seller_participant_id,side,quantity,target_price,currency,delivery_location,delivery_date,expires_at,created_by) values (?,?,?,?,?,?,?,?,?,?,?,?) returning id",
            UUID.class,ref("RFQ"),UUID.fromString(req(in,"instrumentId")),participant,uuid(in.get("sellerParticipantId")),
            side(in),decimal(in,"quantity"),nullableDecimal(in.get("targetPrice")),req(in,"currency"),
            in.get("deliveryLocation"),in.get("deliveryDate"),OffsetDateTime.parse(req(in,"expiresAt")),actor.userId());
        audit(actor,"RFQ_CREATED","RFQ",id.toString(),in); return db.queryForMap("select * from rfqs where id=?",id);
    }
    @Transactional public Map<String,Object> quote(UUID rfqId,Map<String,Object> in,TradingPrincipal actor) {
        Map<String,Object> rfq=one("select * from rfqs where id=? and status in ('OPEN','QUOTED','NEGOTIATING') and expires_at>now()",rfqId,"RFQ is not open");
        UUID participant=actor.participantId(); if(participant==null || participant.equals(rfq.get("buyer_participant_id"))) bad("Only an eligible counterparty may quote");
        requireActiveParticipant(participant);
        BigDecimal quoteQuantity=decimal(in,"quantity");
        if(quoteQuantity.compareTo((BigDecimal)rfq.get("quantity"))!=0) bad("Quote must cover the full RFQ quantity; partial RFQ fills are not enabled");
        Object invited=rfq.get("seller_participant_id"); if(invited!=null && !invited.equals(participant)) forbidden("RFQ is restricted to the invited seller");
        OffsetDateTime validUntil=in.containsKey("validUntil")?OffsetDateTime.parse(req(in,"validUntil")):OffsetDateTime.now().plusMinutes(15);
        if(!validUntil.isAfter(OffsetDateTime.now())) bad("Quote validity must be in the future");
        UUID id=db.queryForObject("insert into quotes(rfq_id,participant_id,price,quantity,side,message,valid_until) values (?,?,?,?,?,?,?) returning id",
            UUID.class,rfqId,participant,decimal(in,"price"),quoteQuantity,"SELL",in.get("message"),validUntil);
        db.update("update rfqs set status='QUOTED' where id=?",rfqId);
        audit(actor,"QUOTE_SUBMITTED","QUOTE",id.toString(),in); return db.queryForMap("select * from quotes where id=?",id);
    }
    @Transactional public Map<String,Object> acceptQuote(UUID quoteId,TradingPrincipal actor) {
        Map<String,Object> q=one("select q.*,r.instrument_id,r.buyer_participant_id,r.currency,r.status rfq_status,r.quantity rfq_quantity from quotes q join rfqs r on r.id=q.rfq_id where q.id=? and r.status in ('OPEN','QUOTED','NEGOTIATING') and r.expires_at>now() for update",quoteId,"Quote not found");
        if(!Objects.equals(actor.participantId(),q.get("buyer_participant_id"))) forbidden("Only the RFQ buyer can accept this quote");
        if(!"ACTIVE".equals(q.get("status")) || !OffsetDateTime.now().isBefore(((OffsetDateTime)q.get("valid_until")))) bad("Quote is no longer active");
        if(((BigDecimal)q.get("quantity")).compareTo((BigDecimal)q.get("rfq_quantity"))!=0) bad("Partial RFQ fills are not enabled");
        UUID trade=db.queryForObject("insert into trades(reference,instrument_id,buyer_participant_id,seller_participant_id,price,quantity,currency,source) values (?,?,?,?,?,?,?,'RFQ') returning id",
            UUID.class,ref("TRD"),q.get("instrument_id"),q.get("buyer_participant_id"),q.get("participant_id"),q.get("price"),q.get("quantity"),q.get("currency"));
        db.update("update quotes set status=case when id=? then 'ACCEPTED' else 'REJECTED' end where rfq_id=? and status='ACTIVE'",quoteId,q.get("rfq_id"));
        db.update("update rfqs set status='ACCEPTED' where id=?",q.get("rfq_id"));
        audit(actor,"QUOTE_ACCEPTED","TRADE",trade.toString(),Map.of("quoteId",quoteId));
        return db.queryForMap("select * from trades where id=?",trade);
    }
    @Transactional public List<Map<String,Object>> submitOrder(Map<String,Object> in,TradingPrincipal actor) { return submitOrder(in,actor,null); }
    @Transactional public List<Map<String,Object>> submitOrder(Map<String,Object> in,TradingPrincipal actor,String idempotencyKey) {
        String requestHash=hash(in);
        if(idempotencyKey!=null && !idempotencyKey.isBlank()) {
            if(idempotencyKey.length()>120) bad("Idempotency-Key must be at most 120 characters");
            int inserted=db.update("insert into idempotency_keys(user_id,idempotency_key,request_hash) values (?,?,?) on conflict do nothing",actor.userId(),idempotencyKey,requestHash);
            if(inserted==0) {
                Map<String,Object> prior=one("select request_hash,response from idempotency_keys where user_id=? and idempotency_key=? for update",actor.userId(),idempotencyKey,"Idempotency record not found");
                if(!requestHash.equals(prior.get("request_hash"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency-Key was already used with a different request");
                Object response=prior.get("response");
                if(response==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Matching request is still being processed; retry shortly");
                try { return new ObjectMapper().readValue(response.toString(),new TypeReference<List<Map<String,Object>>>(){}); }
                catch(Exception ex){throw new IllegalStateException("Unable to replay idempotent response",ex);}
            }
        }
        UUID participant=actor.participantId(); if(participant==null) bad("User is not linked to a participant");
        requireActiveParticipant(participant);
        UUID instrument=UUID.fromString(req(in,"instrumentId"));
        Map<String,Object> ins=one("select * from instruments where id=? and active=true for update",instrument,"Instrument unavailable");
        String side=side(in), type=String.valueOf(in.getOrDefault("orderType","LIMIT")).toUpperCase(Locale.ROOT);
        if(!type.equals("LIMIT") && !type.equals("MARKET")) bad("orderType must be LIMIT or MARKET");
        String tif=String.valueOf(in.getOrDefault("timeInForce","GTC")).toUpperCase(Locale.ROOT);
        if(!tif.equals("GTC") && !tif.equals("IOC")) bad("timeInForce must be GTC or IOC");
        if(type.equals("MARKET") && !tif.equals("IOC")) bad("Market orders must use IOC time in force");
        BigDecimal qty=decimal(in,"quantity"), price=nullableDecimal(in.get("price"));
        if(qty.compareTo((BigDecimal)ins.get("min_quantity"))<0) bad("Quantity below instrument minimum");
        if("LIMIT".equals(type) && (price==null || price.signum()<=0)) bad("Limit orders require a positive price");
        if(price!=null && price.remainder((BigDecimal)ins.get("tick_size")).compareTo(BigDecimal.ZERO)!=0) bad("Price does not conform to instrument tick size");
        Map<String,Object> limits=one("select * from risk_limits where participant_id=?",participant,"Risk limits not configured");
        if(qty.compareTo((BigDecimal)limits.get("max_order_quantity"))>0) bad("Order quantity exceeds participant risk limit");
        BigDecimal riskPrice=price==null?decimal(in,"riskPrice"):price;
        if(qty.multiply(riskPrice).compareTo((BigDecimal)limits.get("max_order_notional"))>0) bad("Order notional exceeds participant risk limit");
        UUID order=db.queryForObject("insert into orders(reference,instrument_id,participant_id,user_id,side,order_type,price,quantity,remaining_quantity,time_in_force) values (?,?,?,?,?,?,?,?,?,?) returning id",
            UUID.class,ref("ORD"),instrument,participant,actor.userId(),side,type,price,qty,qty,tif);
        audit(actor,"ORDER_ACCEPTED","ORDER",order.toString(),in);
        match(instrument,order,side,type,price,qty,ins);
        List<Map<String,Object>> result=db.queryForList("select * from trades where buy_order_id=? or sell_order_id=? order by created_at",order,order);
        if(idempotencyKey!=null && !idempotencyKey.isBlank()) {
            try { db.update("update idempotency_keys set response=?::jsonb where user_id=? and idempotency_key=?",new ObjectMapper().writeValueAsString(result),actor.userId(),idempotencyKey); }
            catch(Exception ex){throw new IllegalStateException("Unable to persist idempotent response",ex);}
        }
        return result;
    }
    private void match(UUID instrument,UUID incoming,String side,String type,BigDecimal limit,BigDecimal qty,Map<String,Object> ins) {
        db.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,instrument.toString());
        db.update("insert into matching_partitions(partition_key,owner_id,lease_until,fencing_token) values (?,'postgres-advisory-lock',now()+interval '30 seconds',1) on conflict(partition_key) do update set owner_id='postgres-advisory-lock',lease_until=now()+interval '30 seconds',fencing_token=matching_partitions.fencing_token+1,updated_at=now()",instrument.toString());
        String opposite="BUY".equals(side)?"SELL":"BUY";
        String sort="BUY".equals(side)?"price asc,created_at asc":"price desc,created_at asc";
        String sql="select * from orders where instrument_id=? and side=? and status in ('OPEN','PARTIALLY_FILLED') and id<>? and participant_id<>? and order_type='LIMIT' and (?='MARKET' or (?='BUY' and price<=?) or (?='SELL' and price>=?)) order by "+sort+" for update";
        UUID participant=db.queryForObject("select participant_id from orders where id=?",UUID.class,incoming);
        List<Map<String,Object>> makers=db.queryForList(sql,instrument,opposite,incoming,participant,type,side,limit,side,limit);
        BigDecimal remaining=qty;
        for(Map<String,Object> maker:makers) {
            if(remaining.signum()==0) break;
            BigDecimal makerRemaining=(BigDecimal)maker.get("remaining_quantity");
            BigDecimal fill=remaining.min(makerRemaining), execution=(BigDecimal)maker.get("price");
            UUID buy="BUY".equals(side)?incoming:(UUID)maker.get("id");
            UUID sell="SELL".equals(side)?incoming:(UUID)maker.get("id");
            UUID buyer="BUY".equals(side)?participant:(UUID)maker.get("participant_id");
            UUID seller="SELL".equals(side)?participant:(UUID)maker.get("participant_id");
            UUID trade=db.queryForObject("insert into trades(reference,instrument_id,buy_order_id,sell_order_id,buyer_participant_id,seller_participant_id,price,quantity,currency,source) values (?,?,?,?,?,?,?,?,?,'ORDER_BOOK') returning id",
                UUID.class,ref("TRD"),instrument,buy,sell,buyer,seller,execution,fill,ins.get("currency"));
            BigDecimal makerLeft=makerRemaining.subtract(fill), incomingLeft=remaining.subtract(fill);
            updateOrder((UUID)maker.get("id"),makerLeft);
            remaining=incomingLeft;
            audit(null,"TRADE_MATCHED","TRADE",trade.toString(),Map.of("price",execution,"quantity",fill));
        }
        updateOrder(incoming,remaining);
        if("IOC".equals(db.queryForObject("select time_in_force from orders where id=?",String.class,incoming)) && remaining.signum()>0)
            db.update("update orders set status='CANCELLED',updated_at=now() where id=?",incoming);
    }
    private void updateOrder(UUID id,BigDecimal remaining) {
        db.update("update orders set remaining_quantity=?,status=?,updated_at=now() where id=?",remaining,
            remaining.signum()==0?"FILLED":remaining.compareTo(db.queryForObject("select quantity from orders where id=?",BigDecimal.class,id))<0?"PARTIALLY_FILLED":"OPEN",id);
    }
    @Transactional public Map<String,Object> cancelOrder(UUID id,TradingPrincipal actor) {
        int n=db.update("update orders set status='CANCELLED',updated_at=now() where id=? and participant_id=? and status in ('OPEN','PARTIALLY_FILLED')",id,actor.participantId());
        if(n==0) bad("Open order not found for this participant");
        audit(actor,"ORDER_CANCELLED","ORDER",id.toString(),Map.of());return db.queryForMap("select * from orders where id=?",id);
    }
    public List<Map<String,Object>> orderBook(UUID instrument) {
        return db.queryForList("select side,price,sum(remaining_quantity) quantity,count(*) order_count from orders where instrument_id=? and status in ('OPEN','PARTIALLY_FILLED') and order_type='LIMIT' group by side,price order by side,case when side='BUY' then price end desc,case when side='SELL' then price end asc",instrument);
    }
    @Transactional public Map<String,Object> confirmTrade(UUID id,TradingPrincipal actor) {
        Map<String,Object> t=one("select * from trades where id=? for update",id,"Trade not found");
        UUID p=actor.participantId();String col;
        if(Objects.equals(p,t.get("buyer_participant_id"))) col="buyer_confirmed_at";
        else if(Objects.equals(p,t.get("seller_participant_id"))) col="seller_confirmed_at";
        else throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only trade counterparties may confirm");
        db.update("update trades set "+col+"=coalesce("+col+",now()) where id=?",id);
        db.update("update trades set status='CONFIRMED' where id=? and buyer_confirmed_at is not null and seller_confirmed_at is not null",id);
        audit(actor,"TRADE_CONFIRMED","TRADE",id.toString(),Map.of());
        return db.queryForMap("select * from trades where id=?",id);
    }
    public List<Map<String,Object>> audit(UUID entityId){return db.queryForList("select * from audit_events where entity_id=? order by occurred_at",entityId.toString());}
    public void setRisk(UUID participant,Map<String,Object> in,TradingPrincipal actor) {
        db.update("insert into risk_limits(participant_id,max_order_quantity,max_order_notional) values (?,?,?) on conflict(participant_id) do update set max_order_quantity=excluded.max_order_quantity,max_order_notional=excluded.max_order_notional,updated_at=now()",
            participant,decimal(in,"maxOrderQuantity"),decimal(in,"maxOrderNotional"));
        audit(actor,"RISK_LIMIT_UPDATED","PARTICIPANT",participant.toString(),in);
    }
    private void requireActiveParticipant(UUID id){Integer active=db.queryForObject("select count(*) from participants where id=? and status='ACTIVE'",Integer.class,id);if(active==null||active==0)forbidden("Participant is not active");}
    private Map<String,Object> one(String sql,Object arg,String message){var r=db.queryForList(sql,arg);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,message);return r.get(0);}
    private Map<String,Object> one(String sql,Object arg1,Object arg2,String message){var r=db.queryForList(sql,arg1,arg2);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,message);return r.get(0);}
    private void audit(TradingPrincipal a,String type,String entity,String id,Map<String,Object> data){
        String payload=new ObjectMapper().valueToTree(data).toString();
        db.update("insert into audit_events(actor_user_id,participant_id,event_type,entity_type,entity_id,details) values (?,?,?,?,?,?::jsonb)",a==null?null:a.userId(),a==null?null:a.participantId(),type,entity,id,payload);
        db.update("insert into event_outbox(aggregate_type,aggregate_id,event_type,payload) values (?,?,?,jsonb_build_object('entityType',?,'entityId',?,'details',?::jsonb))",entity,id,type,entity,id,payload);
    }
    private static String hash(Map<String,Object> body){
        try { byte[] bytes=new ObjectMapper().writeValueAsBytes(new TreeMap<>(body));return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception ex){throw new IllegalArgumentException("Unable to hash request",ex);}
    }
    private static String req(Map<String,Object> m,String k){Object v=m.get(k);if(v==null||v.toString().isBlank())bad("Missing field: "+k);return v.toString();}
    private static BigDecimal decimal(Map<String,Object> m,String k){try{return new BigDecimal(req(m,k));}catch(NumberFormatException e){bad("Invalid decimal: "+k);return null;}}
    private static BigDecimal nullableDecimal(Object v){return v==null?null:new BigDecimal(v.toString());}
    private static UUID uuid(Object v){return v==null?null:UUID.fromString(v.toString());}
    private static String side(Map<String,Object> m){String s=req(m,"side").toUpperCase(Locale.ROOT);if(!s.equals("BUY")&&!s.equals("SELL"))bad("side must be BUY or SELL");return s;}
    private static String ref(String prefix){return prefix+"-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);}
    private static void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private static void forbidden(String m){throw new ResponseStatusException(HttpStatus.FORBIDDEN,m);}
}
