package com.xpertsedge.hrms;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL", matches="jdbc:mysql:.*")
@ActiveProfiles("mysql")
@SpringBootTest(properties={
  "spring.datasource.url=${MYSQL_TEST_URL}",
  "spring.datasource.username=${MYSQL_TEST_USERNAME}",
  "spring.datasource.password=${MYSQL_TEST_PASSWORD}",
  "hrms.jwt.secret=isolated-mysql-test-signing-secret-32-bytes-minimum",
  "hrms.admin.email=mysql-test@example.invalid", "hrms.admin.password=IsolatedTestOnly@2026"
})
@Transactional
class MySqlSchemaTest {
  @Autowired EntityManager em;
  @Autowired Flyway flyway;
  @Autowired NotificationRepo notifications;

  @Test void schemaIdentifiersRelationsAndUtcNotificationsWork() {
    assertEquals("4", flyway.info().current().getVersion().toString());
    String suffix=UUID.randomUUID().toString();
    Department department=new Department();
    department.name="MySQL test"; department.nameKey=suffix;
    em.persist(department);
    Employee employee=new Employee();
    employee.employeeCode=suffix.substring(0,32); employee.firstName="Test";
    employee.email=suffix+"@example.invalid"; employee.joiningDate=LocalDate.of(2026,1,1);
    employee.employmentType="FULL_TIME"; employee.status="ACTIVE"; employee.departmentId=department.id;
    em.persist(employee);
    AppUser user=new AppUser(); user.name="Test"; user.email=employee.email;
    user.passwordHash="test-only"; user.role="EMPLOYEE"; user.employeeId=employee.id;
    em.persist(user);
    Mailbox box=new Mailbox(); box.address=suffix+"@example.invalid"; em.persist(box);
    Alias alias=new Alias(); alias.alias="alias-"+box.address; alias.target=box.address; em.persist(alias);
    AppNotification notice=new AppNotification(); notice.recipient=user.email; notice.title="MySQL test";
    notice.createdAt=Instant.parse("2026-01-02T03:04:05.123456Z"); em.persist(notice);
    AuthSession session=new AuthSession(); session.id=suffix; session.email=user.email;
    session.expiresAt=notice.createdAt.plusSeconds(3600); em.persist(session);
    em.flush(); em.clear();
    assertNotNull(department.id); assertNotNull(employee.id); assertNotNull(box.id); assertNotNull(alias.id);
    assertEquals(department.id,em.find(Employee.class,employee.id).departmentId);
    assertEquals(employee.id,em.find(AppUser.class,user.id).employeeId);
    assertEquals(notice.createdAt,em.find(AppNotification.class,notice.id).createdAt);
    assertEquals(session.expiresAt,em.find(AuthSession.class,session.id).expiresAt);
    assertEquals(1,notifications.countByRecipientAndReadFalse(user.email));
    assertEquals(1,notifications.markAllRead(user.email)); em.flush(); em.clear();
    assertTrue(em.find(AppNotification.class,notice.id).read);
  }
}
