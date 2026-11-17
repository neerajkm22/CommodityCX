package com.commoditycx;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@Testcontainers(disabledWithoutDocker=true)
class DatabaseWorkflowIntegrationTest {
 @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17");
 @Test void flywayBuildsTradingAndControlSchemas(){
  Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").load().migrate();
  try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());var s=c.createStatement();var r=s.executeQuery("select count(*) from information_schema.tables where table_schema='public' and table_name in ('rfqs','negotiation_messages','event_outbox','idempotency_keys','settlement_instructions','ledger_entries','surveillance_alerts','regulatory_reports','refresh_tokens','revoked_access_tokens')")){
   assertTrue(r.next());assertEquals(10,r.getInt(1));
  }catch(Exception e){fail(e);}
 }
 @Test void postgresAdvisoryLockSerializesConcurrentWritersAcrossConnections() throws Exception {
  Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").load().migrate();
  ExecutorService pool=Executors.newSingleThreadExecutor();CountDownLatch started=new CountDownLatch(1),acquired=new CountDownLatch(1);
  try(var first=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())){
   first.setAutoCommit(false);try(var st=first.createStatement()){st.execute("select pg_advisory_xact_lock(hashtextextended('instrument-partition-1',0))");}
   Future<?> second=pool.submit(()->{try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())){c.setAutoCommit(false);started.countDown();try(var st=c.createStatement()){st.execute("select pg_advisory_xact_lock(hashtextextended('instrument-partition-1',0))");}acquired.countDown();c.commit();}catch(Exception e){throw new RuntimeException(e);}});
   assertTrue(started.await(2,TimeUnit.SECONDS));assertFalse(acquired.await(200,TimeUnit.MILLISECONDS));first.commit();assertTrue(acquired.await(5,TimeUnit.SECONDS));second.get(5,TimeUnit.SECONDS);
  }finally{pool.shutdownNow();}
 }
}
