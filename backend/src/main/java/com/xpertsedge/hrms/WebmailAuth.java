package com.xpertsedge.hrms;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
@Service
public class WebmailAuth {
  @org.springframework.beans.factory.annotation.Autowired private MailMfa mfa;
  private final MailboxRepo boxes; private final WebmailSessionRepo sessions; private final UserRepo users; private final AdminAuthService hrmsAuth;
  private final LoginAttemptRepo attempts; private final BCryptPasswordEncoder encoder;
  private final String dummy; private final boolean enabled,production,secure; private final String domain;
  public WebmailAuth(MailboxRepo boxes,WebmailSessionRepo sessions,LoginAttemptRepo attempts,BCryptPasswordEncoder encoder,UserRepo users,AdminAuthService hrmsAuth,
    @Value("${hrms.webmail.enabled:true}") boolean enabled,@Value("${hrms.auth.production:false}") boolean production,
    @Value("${hrms.auth.cookie-secure:false}") boolean secure,@Value("${hrms.mail.domain:xpertsedgetech.com}") String domain){
    this.boxes=boxes;this.sessions=sessions;this.attempts=attempts;this.encoder=encoder;this.users=users;this.hrmsAuth=hrmsAuth;this.enabled=enabled;this.production=production;this.secure=secure;this.domain=domain;dummy=encoder.encode(UUID.randomUUID().toString());
  }
  public void requireEnabled(){if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Start backend with the local-mail profile to enable local mail.");}
  public String email(Mailbox box){return box.address+"@"+domain;}
  public String domain(){return domain;}
  static String fingerprint(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
  private MailboxSession startSession(Mailbox box, boolean remember){
    Instant now=Instant.now();sessions.deleteByExpiresAtBefore(now);
    String token=UUID.randomUUID().toString()+UUID.randomUUID();WebmailSession session=new WebmailSession();
    session.id=fingerprint(token);session.ownerId=box.id;session.credentialFingerprint=fingerprint(box.passwordHash);
    session.expiresAt=now.plusSeconds(remember?2592000:3600);sessions.save(session);return new MailboxSession(box,token);
  }
  private record MailboxSession(Mailbox box,String token){}
  public synchronized Mailbox login(Map<String,Object> body,HttpServletResponse response){
    requireEnabled();String email=body.get("email") instanceof String s?s.trim().toLowerCase(Locale.ROOT):"";
    String password=body.get("password") instanceof String s?s:"";
    if(email.length()>254||password.isEmpty()||password.getBytes(StandardCharsets.UTF_8).length>72)throw unauthorized();
    String attemptKey="mail:"+fingerprint(email);Instant now=Instant.now();
    LoginAttempt attempt=attempts.findById(attemptKey).orElseGet(()->{LoginAttempt a=new LoginAttempt();a.email=attemptKey;return a;});
    if(attempt.windowStart==null||!attempt.windowStart.plusSeconds(900).isAfter(now)){attempt.windowStart=now;attempt.failures=0;}
    if(attempt.failures>=5)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many login attempts. Retry after 15 minutes.");
    Mailbox box=boxes.findAll().stream().filter(b->email(b).equals(email)).findFirst().orElse(null);
    boolean matches=encoder.matches(password,box==null?dummy:box.passwordHash);
    if(box==null||!box.active||!matches){
      // The company administrator is permitted to use HRMS credentials on
      // this form. Other roles remain mailbox-password-only.
      AppUser administrator=users.findByEmail(email).filter(user->user.active&&"ADMIN".equals(user.role)).orElse(null);
      if(administrator!=null){hrmsAuth.login(email,password);attempts.deleteById(attemptKey);return loginAdministrator(administrator,response);}
      attempt.failures++;attempts.save(attempt);throw unauthorized();
    }
    if(!mfa.verify(box.id,body.get("code") instanceof String otp?otp:"")){attempt.failures++;attempts.save(attempt);throw unauthorized();}
    attempts.deleteById(attemptKey);boolean remember=Boolean.TRUE.equals(body.get("rememberMe"));MailboxSession session=startSession(box,remember);
    response.addHeader("Set-Cookie",cookie(session.token,remember?2592000:-1));return box;
  }
  /** Active HRMS administrators may obtain a separate, mailbox-only session. */
  public synchronized Mailbox loginAdministrator(AppUser user,HttpServletResponse response){
    requireEnabled();
    if(user==null||!user.active||!"ADMIN".equals(user.role))throw unauthorized();
    String email=user.email==null?"":user.email.trim().toLowerCase(Locale.ROOT),suffix="@"+domain;
    if(!email.endsWith(suffix)||email.length()<=suffix.length())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Administrator email must use the company mail domain.");
    String address=email.substring(0,email.length()-suffix.length());
    Mailbox box=boxes.findByAddress(address).orElseGet(()->{Mailbox created=new Mailbox();created.address=address;created.displayName=user.name==null||user.name.isBlank()?address:user.name;created.passwordHash=encoder.encode(UUID.randomUUID().toString());return boxes.save(created);});
    if(!box.active)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"The administrator mailbox is suspended.");
    MailboxSession session=startSession(box,false);response.addHeader("Set-Cookie",cookie(session.token,-1));return box;
  }
  String cookie(String value,long seconds){return ResponseCookie.from("xe_mail_session",value).path("/api/webmail").httpOnly(true).secure(secure).sameSite("Strict").maxAge(seconds).build().toString();}
  private String token(HttpServletRequest request){if(request.getCookies()!=null)for(Cookie cookie:request.getCookies())if(cookie.getName().equals("xe_mail_session"))return cookie.getValue();return "";}
  public Mailbox current(HttpServletRequest request){
    requireEnabled();String token=token(request);if(token.length()>200||token.isEmpty())throw unauthorized();
    WebmailSession session=sessions.findById(fingerprint(token)).orElseThrow(WebmailAuth::unauthorized);
    Mailbox box=boxes.findById(session.ownerId).orElseThrow(WebmailAuth::unauthorized);
    if(!box.active||!session.expiresAt.isAfter(Instant.now())||!session.credentialFingerprint.equals(fingerprint(box.passwordHash)))throw unauthorized();return box;
  }
  public void logout(HttpServletRequest request,HttpServletResponse response){String token=token(request);if(!token.isEmpty())sessions.deleteById(fingerprint(token));response.addHeader("Set-Cookie",cookie("",0));}
  public Map<String,Object> view(Mailbox box){return Map.of("email",email(box),"name",box.displayName==null?box.address:box.displayName,"id",box.id);}
  static ResponseStatusException unauthorized(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect mailbox credentials or expired mail session.");}
}
