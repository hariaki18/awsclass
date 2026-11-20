package com.xpertsedge.hrms;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.flywaydb.core.Flyway;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
  "spring.datasource.driver-class-name=org.h2.Driver", "spring.flyway.locations=classpath:db/migration",
  "spring.datasource.url=jdbc:h2:mem:application-schema-test;DB_CLOSE_DELAY=-1",
  "spring.datasource.username=sa", "spring.datasource.password=",
  "hrms.jwt.secret=isolated-test-signing-secret-32-bytes-minimum",
  "hrms.admin.email=schema-test@example.invalid", "hrms.admin.password=IsolatedTestOnly@2026",
  "hrms.admin.name=Schema Test", "spring.flyway.baseline-on-migrate=false"
})
class ApplicationSchemaTest {
  @Autowired Flyway flyway;
  @Autowired UserRepo users;
  @Autowired MailboxRepo mailboxes;
  @Test void flywaySchemaPassesHibernateValidationAndBootstrap() {
    assertEquals("10",flyway.info().current().getVersion().toString());
    assertTrue(users.findByEmail("schema-test@example.invalid").isPresent());
    assertEquals(0,mailboxes.count());
  }
}

