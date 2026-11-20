package com.xpertsedge.hrms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExistingDatabaseMigrationTest {
  static String backupPath() { return System.getProperty("existing.db.backup",System.getenv("MIGRATION_BACKUP")); }
  static boolean backupAvailable() { return backupPath()!=null && !backupPath().isBlank(); }
  @Test @EnabledIf("backupAvailable")
  void copiedRealBackupRetainsAllRowsAndSequencePositions(@TempDir Path folder) throws Exception {
    Path source=Path.of(backupPath()).toAbsolutePath();
    Path destination=folder.resolve("copied.mv.db");Files.copy(source,destination);
    String url="jdbc:h2:file:"+folder.resolve("copied").toString().replace('\\','/');
    Map<String,String> before=snapshot(url);
    Flyway f=Flyway.configure().dataSource(url,"","").baselineOnMigrate(true).baselineVersion("0").cleanDisabled(true).load();
    int pending=f.info().pending().length;assertTrue(pending>0);
    assertEquals(pending,f.migrate().migrationsExecuted);
    assertEquals("8",f.info().current().getVersion().toString());
    assertEquals(before,snapshot(url),"Copied legacy data or sequence positions changed");
    assertEquals(0,f.migrate().migrationsExecuted); f.validate();
  }
  Map<String,String> snapshot(String url) throws Exception {
    Map<String,String> result=new TreeMap<>();
    try(Connection c=DriverManager.getConnection(url,"","");Statement s=c.createStatement()) {
      for(String table:List.of("app_user","mailbox","mail_alias","app_notification","auth_session","login_attempt","mail_namespace")) {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        String columns=table.equals("app_user")?"id,email,name,password_hash":table.equals("mailbox")?"id,active,address,display_name,password_hash,quota_gb,used_gb":table.equals("mail_alias")?"id,alias_name,target":"*";
        try(ResultSet r=s.executeQuery("SELECT "+columns+" FROM "+table+" ORDER BY 1")) {
          while(r.next()) for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
            String value=r.getString(i);digest.update((value==null?"<NULL>":value).getBytes(StandardCharsets.UTF_8));digest.update((byte)0);
          }
        }
        result.put(table,HexFormat.of().formatHex(digest.digest()));
      }
      try(ResultSet r=s.executeQuery("SELECT SEQUENCE_NAME, BASE_VALUE FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_SCHEMA='PUBLIC' AND SEQUENCE_NAME IN ('APP_USER_SEQ','MAILBOX_SEQ','MAIL_ALIAS_SEQ','APP_NOTIFICATION_SEQ') ORDER BY SEQUENCE_NAME")) {
        while(r.next()) result.put("sequence:"+r.getString(1),r.getString(2));
      }
    }
    return result;
  }
}

