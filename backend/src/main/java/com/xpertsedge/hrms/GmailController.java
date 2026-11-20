package com.xpertsedge.hrms;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Company-level Gmail administration. Employees never connect their own Google accounts. */
@RestController @RequestMapping("/api/webmail/gmail")
public class GmailController {
  private static final Logger log=LoggerFactory.getLogger(GmailController.class);
  private final WebmailAuth auth;private final GmailApi api;private final GmailAccountRepo accounts;private final GmailSync sync;private final String administrator;
  public GmailController(WebmailAuth auth,GmailApi api,GmailAccountRepo accounts,GmailSync sync,
    @Value("${GMAIL_COMPANY_ADMIN_MAILBOX:}") String administrator){
    this.auth=auth;this.api=api;this.accounts=accounts;this.sync=sync;this.administrator=administrator.trim().toLowerCase();
  }
  private GmailAccount company(){return api.enabled()?accounts.findFirstByOrderByConnectedAtAsc().orElse(null):null;}
  private boolean canManage(Mailbox box){return !administrator.isBlank()&&administrator.equals(auth.email(box));}
  private void requireManager(Mailbox box){if(!canManage(box))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the configured company Webmail administrator can manage the Gmail connection.");}
  Map<String,Object> view(Mailbox box){
    GmailAccount account=company();Map<String,Object> result=new LinkedHashMap<>();
    result.put("configured",api.enabled());result.put("connected",account!=null);result.put("canManage",canManage(box));
    result.put("gmailAddress",account==null?null:account.gmailAddress);
    result.put("lastSyncAt",account==null||account.lastSyncAt==null?null:account.lastSyncAt.toString());
    result.put("needsReconnect",account!=null&&"RECONNECT".equals(account.lastError));
    result.put("syncFailed",account!=null&&"SYNC_FAILED".equals(account.lastError));return result;
  }
  @GetMapping("/status") public Map<String,Object> status(HttpServletRequest request){return view(auth.current(request));}
  @GetMapping("/connect") public Map<String,Object> connect(HttpServletRequest request){
    Mailbox box=auth.current(request);requireManager(box);if(company()!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"A company Gmail account is already connected.");
    return Map.of("url",api.authorizationUrl(box.id));
  }
  @GetMapping("/callback") public ResponseEntity<Void> callback(@RequestParam(required=false) String code,@RequestParam(required=false) String state,@RequestParam(required=false) String error){
    if(!api.enabled())return finish("error");if(error!=null||code==null||state==null){api.discard(state);return finish("denied");}
    try{GmailAccount account=api.complete(state,code);try{sync.sync(account.mailboxId);}catch(RuntimeException ignored){}return finish("connected");}
    catch(ResponseStatusException failure){
      // The browser gets a generic safe message; the server log contains the actionable, non-secret reason.
      log.warn("Company Gmail OAuth connection failed: status={} reason={}",failure.getStatusCode().value(),failure.getReason());
      return finish("error");
    }
  }
  private ResponseEntity<Void> finish(String result){return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(api.frontendUrl()+"/?gmail="+result+"#/webmail")).header("Cache-Control","no-store").build();}
  @PostMapping("/disconnect") @ResponseStatus(HttpStatus.NO_CONTENT) public void disconnect(HttpServletRequest request){Mailbox box=auth.current(request);requireManager(box);GmailAccount account=company();if(account!=null)api.disconnect(account.mailboxId);}
  @PostMapping("/sync") public Map<String,Object> syncNow(HttpServletRequest request){
    Mailbox box=auth.current(request);requireManager(box);api.requireEnabled();GmailAccount account=company();
    if(account==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Company Gmail is not connected.");int imported=sync.sync(account.mailboxId);Map<String,Object> result=view(box);result.put("imported",imported);return result;
  }
}
