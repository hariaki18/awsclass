package com.xpertsedge.hrms;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
@org.springframework.core.annotation.Order(0)
public class WebmailBodyLimitFilter extends OncePerRequestFilter {
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
    if(!request.getRequestURI().startsWith("/api/webmail/")||!java.util.Set.of("POST","PUT","PATCH").contains(request.getMethod())){chain.doFilter(request,response);return;}
    int limit=2*1024*1024;if(request.getContentLengthLong()>limit){response.sendError(413,"Mail request exceeds 2 MB.");return;}
    byte[] body=request.getInputStream().readNBytes(limit+1);if(body.length>limit){response.sendError(413,"Mail request exceeds 2 MB.");return;}
    chain.doFilter(new HttpServletRequestWrapper(request){
      @Override public ServletInputStream getInputStream(){ByteArrayInputStream stream=new ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return stream.read();}public boolean isFinished(){return stream.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}};}
      @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
    },response);
  }
}
