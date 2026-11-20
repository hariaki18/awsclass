package com.xpertsedge.hrms;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
  private final NotificationRepo repo;
  private final HttpServletRequest request;
  public NotificationService(NotificationRepo repo, HttpServletRequest request) { this.repo=repo; this.request=request; }
  public void add(String title, String body) {
    Object email=request.getAttribute("email");
    if(email==null) return;
    AppNotification n=new AppNotification(); n.recipient=email.toString(); n.title=title; n.body=body; repo.save(n);
  }
}
