package com.xpertsedge.hrms;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.*;
import org.springframework.boot.actuate.health.*;
import org.springframework.boot.availability.*;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.slf4j.MDC;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
  "spring.datasource.driver-class-name=org.h2.Driver", "spring.flyway.locations=classpath:db/migration",
  "spring.datasource.url=jdbc:h2:mem:health-logging-test;DB_CLOSE_DELAY=-1",
  "spring.datasource.username=sa","spring.datasource.password=",
  "hrms.jwt.secret=isolated-health-test-signing-secret-32-bytes",
  "hrms.admin.email=health-test@example.invalid","hrms.admin.password=IsolatedHealthOnly@2026",
  "logging.file.name=target/test-logs/health.log"
})
@AutoConfigureMockMvc @ExtendWith(OutputCaptureExtension.class)
class HealthLoggingTest {
  @Autowired MockMvc mvc;
  @Autowired HealthContributorRegistry contributors;
  @Autowired ApplicationContext context;
  HealthContributor db;
  @BeforeEach void ready() {
    db=contributors.getContributor("db");
    AvailabilityChangeEvent.publish(context,ReadinessState.ACCEPTING_TRAFFIC);
    AvailabilityChangeEvent.publish(context,LivenessState.CORRECT);
  }
  @AfterEach void restore() { contributors.unregisterContributor("db");contributors.registerContributor("db",db); }
  @Test void publicHealthReturnsOnlyStatus() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
      .andExpect(jsonPath("$.components").doesNotExist()).andExpect(jsonPath("$.details").doesNotExist());
  }
  @Test void readinessIncludesDatabaseButLivenessDoesNot() throws Exception {
    contributors.unregisterContributor("db");contributors.registerContributor("db",(HealthIndicator)()->Health.down().withDetail("secret","should-never-appear").build());
    String body=mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value("DOWN")).andReturn().getResponse().getContentAsString();
    assertFalse(body.contains("should-never-appear"));
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
  }
  @Test void refusingTrafficMakesReadinessUnavailable() throws Exception {
    AvailabilityChangeEvent.publish(context,ReadinessState.REFUSING_TRAFFIC);
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable());
  }
  @Test void brokenLivenessIsUnavailable() throws Exception {
    AvailabilityChangeEvent.publish(context,LivenessState.BROKEN);
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isServiceUnavailable());
  }
  @Test void managementSecretsAreNotExposed() throws Exception {
    for(String path:new String[]{"/actuator","/actuator/env","/actuator/configprops","/actuator/heapdump","/actuator/loggers"})
      mvc.perform(get(path)).andExpect(status().isNotFound());
  }
  @Test void requestLogsDoNotIncludeCredentialsOrQuery(CapturedOutput output) throws Exception {
    String password="DO_NOT_LOG_PASSWORD_791",query="DO_NOT_LOG_QUERY_792",cookie="DO_NOT_LOG_COOKIE_793";
    var result=mvc.perform(post("/api/auth/login?api_key="+query).header("Origin","http://127.0.0.1:5173")
      .header("Authorization","Bearer DO_NOT_LOG_TOKEN_794").header("Cookie","other="+cookie)
      .contentType("application/json").content("{\"email\":\"missing@example.invalid\",\"password\":\""+password+"\"}"))
      .andExpect(status().isUnauthorized()).andExpect(header().exists("X-Request-ID")).andReturn();
    String id=result.getResponse().getHeader("X-Request-ID");assertNotNull(id);assertEquals(36,id.length());
    assertTrue(output.getOut().contains("route=/api/auth/login status=401"));
    for(String value:new String[]{password,query,cookie,"DO_NOT_LOG_TOKEN_794"}) assertFalse(output.getAll().contains(value),"Sensitive value was logged");
    assertNull(MDC.get("requestId"));
  }
  @Test void userRequestIdCannotInjectLogFields() throws Exception {
    mvc.perform(get("/actuator/health").header("X-Request-ID","untrusted-id"))
      .andExpect(header().string("X-Request-ID",org.hamcrest.Matchers.not("untrusted-id")));
  }
}
