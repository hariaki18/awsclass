package com.xpertsedge.hrms;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
@Component
public class DataSeeder implements CommandLineRunner {
  private final MailNamespaceRepo namespace;
  @Value("${hrms.admin.email}") private String email;
  @Value("${hrms.admin.password}") private String password;
  @Value("${hrms.admin.name:Admin}") private String name;
  @Value("${hrms.auth.production:false}") private boolean production;
  private final UserRepo users; private final BCryptPasswordEncoder enc;
  public DataSeeder(UserRepo u, BCryptPasswordEncoder e, MailNamespaceRepo namespace) { users = u; enc = e; this.namespace=namespace; }
  @Override public void run(String... args) {
    email=email.trim().toLowerCase(Locale.ROOT);
    if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || name.isBlank()) throw new IllegalStateException("Valid ADMIN_EMAIL and ADMIN_NAME required");
    if(production && (password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72 || "Admin@123".equals(password))) throw new IllegalStateException("Production ADMIN_PASSWORD must be 12+ characters, at most 72 bytes, and not the demo password");
    if(production && users.findAll().stream().anyMatch(u -> enc.matches("Admin@123",u.passwordHash))) throw new IllegalStateException("Existing database has a demo-password account. Rotate its password before production deployment.");
    if (users.count() == 0) { AppUser u = new AppUser(); u.email=email; u.name=name; u.passwordHash=enc.encode(password); users.save(u); }
    if(!namespace.existsById(1L)) { MailNamespace n=new MailNamespace(); n.id=1L; namespace.save(n); }
    org.slf4j.LoggerFactory.getLogger(DataSeeder.class).info("Administrator bootstrap verified");
  }
}
