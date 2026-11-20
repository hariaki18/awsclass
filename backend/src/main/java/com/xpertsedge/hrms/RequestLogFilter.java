package com.xpertsedge.hrms;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.stereotype.Component;
import org.slf4j.*;
import java.io.IOException;
import java.util.*;

@Component
public class RequestLogFilter extends OncePerRequestFilter {
  private static final Logger log=LoggerFactory.getLogger(RequestLogFilter.class);
  private static final Set<String> PROBES=Set.of("/actuator/health","/actuator/health/liveness","/actuator/health/readiness");
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
    String id=UUID.randomUUID().toString();long start=System.nanoTime();boolean failed=false;
    MDC.put("requestId",id);response.setHeader("X-Request-ID",id);
    try {chain.doFilter(request,response);}
    catch(ServletException|IOException|RuntimeException e) {failed=true;throw e;}
    finally {
      Object pattern=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      String route=pattern==null?"unmatched":pattern.toString();
      int status=failed?500:response.getStatus();long elapsed=(System.nanoTime()-start)/1_000_000;
      // Route templates only: never log raw URL/query, headers, cookie, body or credentials.
      if(status>=500) log.error("request_end method={} route={} status={} duration_ms={}",request.getMethod(),route,status,elapsed);
      else if(status>=400) log.warn("request_end method={} route={} status={} duration_ms={}",request.getMethod(),route,status,elapsed);
      else if(!PROBES.contains(request.getRequestURI())) log.info("request_end method={} route={} status={} duration_ms={}",request.getMethod(),route,status,elapsed);
      MDC.remove("requestId");
    }
  }
}
