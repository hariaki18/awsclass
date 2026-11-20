package com.xpertsedge.hrms;

import org.flywaydb.core.*;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MigrationTest {
  String url;
  @BeforeEach void database() { url="jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1"; }
  Flyway flyway(boolean adopt) { return Flyway.configure().dataSource(url,"sa","").baselineOnMigrate(adopt).baselineVersion("0").cleanDisabled(true).load(); }
  void sql(String sql) throws Exception { try(Connection c=DriverManager.getConnection(url,"sa","");Statement s=c.createStatement()) { s.execute(sql); } }
  long number(String sql) throws Exception { try(Connection c=DriverManager.getConnection(url,"sa","");Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)) {r.next();return r.getLong(1);} }
  void legacy() throws Exception {
    new ResourceDatabasePopulator(new ClassPathResource("legacy-hibernate-schema.sql")).execute(new DriverManagerDataSource(url,"sa",""));
    sql("INSERT INTO app_user VALUES(501,'existing@example.invalid','Existing Admin','preserved-password-hash')");
    sql("INSERT INTO mailbox VALUES(701,true,'existing','Existing mailbox','preserved-mail-hash',2,0)");
    sql("INSERT INTO mail_alias VALUES(801,'support','existing')");
    sql("INSERT INTO app_notification VALUES(901,'Existing notice',CURRENT_TIMESTAMP,false,'existing@example.invalid','Existing title')");
    sql("INSERT INTO auth_session VALUES('session-existing','existing@example.invalid',DATEADD('HOUR',1,CURRENT_TIMESTAMP))");
    sql("INSERT INTO login_attempt VALUES('existing@example.invalid',2,CURRENT_TIMESTAMP)");
    sql("ALTER SEQUENCE mailbox_seq RESTART WITH 1001");
  }
  @Test void freshInstallAndRestart() throws Exception {
    assertEquals(10,flyway(false).migrate().migrationsExecuted);
    assertEquals(0,flyway(false).migrate().migrationsExecuted);
    flyway(false).validate();
    assertEquals(10,number("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\"=true AND \"version\" IS NOT NULL"));
  }
  @Test void existingDatabaseRequiresExplicitAdoption() throws Exception {
    legacy(); assertThrows(FlywayException.class,()->flyway(false).migrate());
    assertEquals(1,number("SELECT COUNT(*) FROM mailbox"));
  }
  @Test void adoptionPreservesRecordsAndSequences() throws Exception {
    legacy(); assertEquals(10,flyway(true).migrate().migrationsExecuted);
    for(String table:List.of("app_user","mailbox","mail_alias","app_notification","auth_session","login_attempt")) assertEquals(1,number("SELECT COUNT(*) FROM "+table));
    assertEquals(1001,number("SELECT NEXT VALUE FOR mailbox_seq"));
    assertEquals(1,number("SELECT COUNT(*) FROM app_user WHERE password_hash='preserved-password-hash'"));
    assertEquals(0,flyway(false).migrate().migrationsExecuted);
  }
  @Test void orphanAliasStopsAdoptionWithoutDeletingRows() throws Exception {
    legacy();sql("UPDATE mail_alias SET target='missing'");
    assertThrows(FlywayException.class,()->flyway(true).migrate());
    assertEquals(1,number("SELECT COUNT(*) FROM mail_alias WHERE target='missing'"));
  }
  @Test void adoptionAddsTablesMissingFromTheOriginalZip() throws Exception {
    legacy();
    for(String table:List.of("app_notification","auth_session","login_attempt","mail_namespace")) sql("DROP TABLE "+table);
    assertEquals(10,flyway(true).migrate().migrationsExecuted);
    assertEquals(1,number("SELECT COUNT(*) FROM mailbox"));
    assertEquals(0,number("SELECT COUNT(*) FROM auth_session"));
    assertEquals(0,number("SELECT COUNT(*) FROM app_notification"));
  }
  @Test void constraintsRejectInvalidWrites() throws Exception {
    flyway(false).migrate();
    assertThrows(SQLException.class,()->sql("INSERT INTO mailbox(id,active,address,display_name,password_hash,quota_gb,used_gb) VALUES(1,true,'invalid','Invalid','hash',0,0)"));
    assertThrows(SQLException.class,()->sql("INSERT INTO mail_alias(id,alias_name,target) VALUES(1,'orphan','missing')"));
  }
  @Test void cleanIsDisabled() { flyway(false).migrate(); assertThrows(FlywayException.class,()->flyway(false).clean()); }
  @Test void editedMigrationFailsChecksumValidation(@TempDir Path folder) throws Exception {
    for(String file:List.of("V1__initial_schema.sql","V2__notification_and_session_indexes.sql","V3__mail_integrity_constraints.sql","V4__basic_hr_and_user_roles.sql","V5__local_webmail.sql","V6__mail_actions_and_attachments.sql","V7__personal_mail_modules.sql","V8__gmail_oauth.sql","V9__mailersend_provider.sql","V10__webmail_starred.sql")) {
      try(var stream=getClass().getResourceAsStream("/db/migration/"+file)) { Files.copy(Objects.requireNonNull(stream),folder.resolve(file)); }
    }
    Flyway f=Flyway.configure().dataSource(url,"sa","").locations("filesystem:"+folder).load(); f.migrate();
    Files.writeString(folder.resolve("V1__initial_schema.sql"),"\nCREATE TABLE checksum_probe(id INT);",StandardOpenOption.APPEND);
    assertThrows(FlywayException.class,f::validate);
  }
}

