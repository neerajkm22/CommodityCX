package com.commoditycx;
import com.commoditycx.security.TradingPrincipal;
import com.commoditycx.service.TradingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker=true)
class TradingServiceIntegrationTest {
 @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
  r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
  r.add("commoditycx.events.enabled",()->"false");r.add("commoditycx.security.jwt-secret",()->"integration-test-signing-secret-which-is-long-enough");
  r.add("commoditycx.security.oidc-issuer-uri",()->"");
 }
 @Autowired JdbcTemplate db;@Autowired TradingService service;
 UUID instrument,buyerParticipant,sellerParticipant,buyerUser,sellerUser;
 @BeforeEach void setup(){
  db.update("delete from audit_events");db.update("delete from user_roles");db.update("delete from event_outbox");db.update("delete from idempotency_keys");db.update("delete from trades");db.update("delete from orders");db.update("delete from risk_limits");db.update("delete from users");db.update("delete from participants");db.update("delete from instruments");db.update("delete from matching_partitions");
  buyerParticipant=participant("IT-BUYER");sellerParticipant=participant("IT-SELLER");
  buyerUser=user(buyerParticipant,"buyer-it@example.test");sellerUser=user(sellerParticipant,"seller-it@example.test");
  db.update("insert into risk_limits(participant_id,max_order_quantity,max_order_notional) values (?,?,?)",buyerParticipant,new BigDecimal("1000"),new BigDecimal("100000"));
  db.update("insert into risk_limits(participant_id,max_order_quantity,max_order_notional) values (?,?,?)",sellerParticipant,new BigDecimal("1000"),new BigDecimal("100000"));
  instrument=db.queryForObject("insert into instruments(symbol,name,commodity_type,unit,currency,tick_size,min_quantity) values ('IT-CRUDE','Integration Crude','ENERGY','BBL','USD',0.01,1) returning id",UUID.class);
 }
 @Test void matchesByPriceTimeAndReplaysIdempotentOrders(){
  TradingPrincipal buyer=new TradingPrincipal(buyerUser,"buyer-it@example.test",buyerParticipant);
  TradingPrincipal seller=new TradingPrincipal(sellerUser,"seller-it@example.test",sellerParticipant);
  Map<String,Object> buy=order("BUY","10.00","5");
  assertTrue(service.submitOrder(buy,buyer,"integration-buy-key").isEmpty());
  assertTrue(service.submitOrder(buy,buyer,"integration-buy-key").isEmpty());
  assertEquals(1,db.queryForObject("select count(*) from orders where user_id=?",Integer.class,buyerUser));
  Map<String,Object> conflicting=order("BUY","10.00","6");
  assertThrows(ResponseStatusException.class,()->service.submitOrder(conflicting,buyer,"integration-buy-key"));
  List<Map<String,Object>> executions=service.submitOrder(order("SELL","9.00","3"),seller,null);
  assertEquals(1,executions.size());assertEquals(0,new BigDecimal("3").compareTo((BigDecimal)executions.get(0).get("quantity")));
  assertEquals(1,db.queryForObject("select count(*) from trades",Integer.class));
  assertEquals("PARTIALLY_FILLED",db.queryForObject("select status from orders where user_id=?",String.class,buyerUser));
 }
 @Test void doesNotMatchParticipantAgainstItsOwnRestingOrder(){
  TradingPrincipal buyer=new TradingPrincipal(buyerUser,"buyer-it@example.test",buyerParticipant);
  service.submitOrder(order("BUY","10.00","2"),buyer,null);
  assertTrue(service.submitOrder(order("SELL","9.00","2"),buyer,null).isEmpty());
  assertEquals(0,db.queryForObject("select count(*) from trades",Integer.class));
 }
 private UUID participant(String code){return db.queryForObject("insert into participants(code,legal_name,country_code,status) values (?,?, 'IN','ACTIVE') returning id",UUID.class,code,code+" Legal Entity");}
 private UUID user(UUID participant,String email){return db.queryForObject("insert into users(participant_id,email,full_name,password_hash,enabled) values (?,?,?,'test-hash',true) returning id",UUID.class,participant,email,email);}
 private Map<String,Object> order(String side,String price,String quantity){Map<String,Object> m=new HashMap<>();m.put("instrumentId",instrument.toString());m.put("side",side);m.put("orderType","LIMIT");m.put("timeInForce","GTC");m.put("price",new BigDecimal(price));m.put("quantity",new BigDecimal(quantity));return m;}
}
