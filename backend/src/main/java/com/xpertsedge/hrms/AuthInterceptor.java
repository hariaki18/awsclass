package com.xpertsedge.hrms;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
@Component
public class AuthInterceptor implements HandlerInterceptor {
  private final JwtService jwt; private final UserRepo users; private final Set<String> origins;
  public AuthInterceptor(JwtService jwt, UserRepo users, @Value("${hrms.auth.origins}") String origins) { this.jwt = jwt; this.users=users; this.origins=Set.of(origins.split(",")); }
  @Override public boolean preHandle(HttpServletRequest q, HttpServletResponse r, Object h) throws Exception {
    if ("OPTIONS".equals(q.getMethod())) return true;
    r.setHeader("Cache-Control","no-store"); r.setHeader("X-Content-Type-Options","nosniff");
    String origin=q.getHeader("Origin");
    boolean mutation=!Set.of("GET","HEAD").contains(q.getMethod());
    if(mutation && origin!=null && !origins.contains(origin)) { r.sendError(403,"Origin not allowed"); return false; }
    // Webmail uses an independent mailbox-only cookie/session. It never grants HRMS access.
    if(q.getRequestURI().startsWith("/api/webmail/")) {
      if(mutation && origin==null) {r.sendError(403,"Origin required for webmail requests");return false;}
      return true;
    }
    if("/api/auth/login".equals(q.getRequestURI())) return true;
    String a = q.getHeader("Authorization");
    String token=null; boolean bearer=a!=null && a.startsWith("Bearer ");
    if(bearer) token=a.substring(7);
    else if(q.getCookies()!=null) for(Cookie cookie:q.getCookies()) if("xe_session".equals(cookie.getName())) token=cookie.getValue();
    if(mutation && !bearer && token!=null && origin==null) { r.sendError(403,"Origin required for cookie requests"); return false; }
    try { if(token!=null) {
      String email=jwt.verify(token);AppUser user=users.findByEmail(email).orElse(null);
      if(user!=null && user.active) {
        q.setAttribute("email",email);q.setAttribute("authToken",token);q.setAttribute("role",user.role);
        String path=q.getRequestURI();boolean admin="ADMIN".equals(user.role);
        boolean allowed=true;
        if(path.startsWith("/api/org/"))allowed=admin;
        else if(path.startsWith("/api/hr/"))allowed=path.equals("/api/hr/self")||admin||"HR_ADMIN".equals(user.role);
        else if(path.startsWith("/api/mail/"))allowed=admin||"MAIL_ADMIN".equals(user.role);
        if(!allowed){r.sendError(403,"Your role does not have access to this module.");return false;}return true;
      }
    } } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {}
    r.sendError(401, "Session expired"); return false;
  }
}
