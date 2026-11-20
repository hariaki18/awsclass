package com.xpertsedge.hrms;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AdminAuthService {
  private final UserRepo users; private final LoginAttemptRepo attempts; private final BCryptPasswordEncoder enc; private final AuthSessionRepo sessions;
  private final String dummyHash;
  public AdminAuthService(UserRepo users, LoginAttemptRepo attempts, BCryptPasswordEncoder enc, AuthSessionRepo sessions) {
    this.users=users; this.attempts=attempts; this.enc=enc; this.sessions=sessions; dummyHash=enc.encode("timing-placeholder-password");
  }
  public synchronized AppUser login(Object emailInput, Object passwordInput) {
    String email=emailInput instanceof String ? ((String)emailInput).trim().toLowerCase(Locale.ROOT) : "";
    String password=passwordInput instanceof String ? (String)passwordInput : "";
    if(email.length()>254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || password.getBytes(StandardCharsets.UTF_8).length>72 || password.isEmpty())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect email or password.");
    Instant now=Instant.now();
    LoginAttempt attempt=attempts.findById(email).orElseGet(() -> { LoginAttempt a=new LoginAttempt(); a.email=email; a.windowStart=now; return a; });
    if(attempt.windowStart==null || !attempt.windowStart.plusSeconds(900).isAfter(now)) { attempt.failures=0; attempt.windowStart=now; }
    if(attempt.failures>=5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many login attempts. Try again after 15 minutes.");
    AppUser user=users.findByEmail(email).orElse(null);
    boolean valid=enc.matches(password,user==null ? dummyHash : user.passwordHash);
    if(user==null || !valid || !user.active) {
      attempt.failures++; attempts.save(attempt);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect email or password.");
    }
    attempts.deleteById(email); return user;
  }
  @Transactional public void updatePassword(AppUser user, String password) {
    users.findById(user.id).orElseThrow().passwordHash=enc.encode(password);
    sessions.deleteByEmail(user.email);
  }
}
