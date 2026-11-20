package com.xpertsedge.hrms;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
@Component
public class JwtService {
  private static final String ISSUER="xpertsedge-hrms", AUDIENCE="xpertsedge-web";
  private final SecretKey key; private final long minutes; private final AuthSessionRepo sessions;
  public JwtService(@Value("${hrms.jwt.secret:}") String secret, @Value("${hrms.jwt.minutes:60}") long minutes,
                    @Value("${hrms.auth.production:false}") boolean production, AuthSessionRepo sessions) throws Exception {
    if(minutes<1 || minutes>1440) throw new IllegalStateException("JWT lifetime must be 1–1440 minutes");
    if(secret.isBlank()) {
      if(production) throw new IllegalStateException("JWT_SECRET is required in production");
      Path path=Path.of("data", ".jwt-secret"); Files.createDirectories(path.getParent());
      if(!Files.exists(path)) {
        String generated=Base64.getEncoder().encodeToString(Jwts.SIG.HS256.key().build().getEncoded());
        try { Files.writeString(path,generated,StandardOpenOption.CREATE_NEW); } catch(FileAlreadyExistsException ignored) {}
      }
      secret=Files.readString(path).trim();
    }
    if(secret.getBytes(StandardCharsets.UTF_8).length<32 || secret.contains("change-this-secret")) throw new IllegalStateException("Use a random JWT_SECRET of at least 32 bytes");
    this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.minutes=minutes; this.sessions=sessions;
  }
  byte[] mailEncryptionKey(){try{javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(key.getEncoded(),"HmacSHA256"));return mac.doFinal("xpertsedge-mail-mfa-v1".getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
  public long lifetimeSeconds() { return minutes*60; }
  @Transactional public String create(String email) {
    Instant now=Instant.now(), expiry=now.plusSeconds(lifetimeSeconds()); sessions.deleteByExpiresAtBefore(now);
    AuthSession session=new AuthSession(); session.id=UUID.randomUUID().toString(); session.email=email; session.expiresAt=expiry; sessions.save(session);
    return Jwts.builder().issuer(ISSUER).audience().add(AUDIENCE).and().id(session.id).subject(email)
      .issuedAt(Date.from(now)).expiration(Date.from(expiry)).signWith(key).compact();
  }
  private Claims claims(String token) {
    Claims c=Jwts.parser().verifyWith(key).requireIssuer(ISSUER).requireAudience(AUDIENCE).build().parseSignedClaims(token).getPayload();
    if(c.getExpiration()==null || c.getIssuedAt()==null || c.getId()==null || c.getSubject()==null || c.getSubject().isBlank()) throw new JwtException("Incomplete token");
    return c;
  }
  public String verify(String token) {
    Claims c=claims(token); AuthSession s=sessions.findById(c.getId()).orElseThrow(() -> new JwtException("Session revoked"));
    if(!s.email.equals(c.getSubject()) || !s.expiresAt.isAfter(Instant.now())) throw new JwtException("Session expired");
    return c.getSubject();
  }
  public void revoke(String token) { Claims c=claims(token); sessions.deleteById(c.getId()); }
}
