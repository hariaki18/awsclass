package com.xpertsedge.hrms;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.beans.factory.annotation.Value;
@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AuthInterceptor auth; private final String[] origins;
  public WebConfig(AuthInterceptor auth, @Value("${hrms.auth.origins}") String origins, @Value("${hrms.auth.production:false}") boolean production) {
    this.auth = auth; this.origins=origins.split(",");
    for(String origin:this.origins) {
      java.net.URI uri=java.net.URI.create(origin);
      if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getRawQuery()!=null || uri.getFragment()!=null || (uri.getPath()!=null && !uri.getPath().isEmpty()) || !("https".equals(uri.getScheme()) || (!production && "http".equals(uri.getScheme()))))
        throw new IllegalStateException("APP_ORIGINS must contain exact origins; production requires HTTPS");
    }
  }
  @Override public void addInterceptors(InterceptorRegistry r) { r.addInterceptor(auth).addPathPatterns("/api/**"); }
  @Override public void addCorsMappings(CorsRegistry r) { r.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS").allowedHeaders("Content-Type","Authorization").allowCredentials(true); }
  @Bean public BCryptPasswordEncoder encoder() { return new BCryptPasswordEncoder(); }
}
