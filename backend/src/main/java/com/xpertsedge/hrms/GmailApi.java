package com.xpertsedge.hrms;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
/**
 * Gmail REST API client using OAuth 2.0 (authorization code + PKCE, offline access).
 * Tokens, authorization codes and message content are never logged.
 */
@Service
public class GmailApi {
  static final String SCOPES="https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/gmail.readonly";
  private static final String AUTH="https://accounts.google.com/o/oauth2/v2/auth",TOKEN="https://oauth2.googleapis.com/token",
    REVOKE="https://oauth2.googleapis.com/revoke",API="https://gmail.googleapis.com/gmail/v1/users/me";
  record Sent(String id,String threadId){}
  record Token(String value,Instant expires){}
  record Pending(Long mailboxId,String verifier,Instant expires){}
  static final class GmailHttpException extends RuntimeException{
    final int status;
    GmailHttpException(int status){super("Gmail API returned HTTP "+status);this.status=status;}
  }
  static final class ReconnectRequired extends ResponseStatusException{
    ReconnectRequired(){super(HttpStatus.CONFLICT,"Gmail access was revoked or has expired. Disconnect and connect Gmail again.");}
  }
  private final boolean enabled;
  private final String clientId,clientSecret,redirectUri,frontend;
  private final ObjectMapper json;
  private final GmailTokenCrypto crypto;
  private final GmailAccountRepo accounts;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final JavaMailSenderImpl mimeFactory=new JavaMailSenderImpl();
  private final SecureRandom random=new SecureRandom();
  private final Map<Long,Token> tokens=new ConcurrentHashMap<>();
  private final Map<String,Pending> pending=new ConcurrentHashMap<>();
  public GmailApi(@Value("${hrms.gmail.api.enabled:false}") boolean enabled,
    @Value("${GOOGLE_CLIENT_ID:}") String clientId,@Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret,
    @Value("${GOOGLE_REDIRECT_URI:http://localhost:8080/api/webmail/gmail/callback}") String redirectUri,
    @Value("${hrms.auth.origins}") String origins,ObjectMapper json,GmailTokenCrypto crypto,GmailAccountRepo accounts){
    this.enabled=enabled;this.clientId=clientId.trim();this.clientSecret=clientSecret.trim();this.redirectUri=redirectUri.trim();
    this.json=json;this.crypto=crypto;this.accounts=accounts;
    String first=origins.split(",")[0].trim();this.frontend=first.endsWith("/")?first.substring(0,first.length()-1):first;
    if(enabled){
      if(!this.clientId.endsWith(".apps.googleusercontent.com"))throw new IllegalStateException("GOOGLE_CLIENT_ID must be a Google OAuth web client ID.");
      if(this.clientSecret.isBlank())throw new IllegalStateException("GOOGLE_CLIENT_SECRET is required when the gmail-api profile is enabled.");
      URI uri;try{uri=URI.create(this.redirectUri);}catch(IllegalArgumentException error){throw new IllegalStateException("GOOGLE_REDIRECT_URI is not a valid URI.");}
      boolean local="localhost".equals(uri.getHost())||"127.0.0.1".equals(uri.getHost());
      if(uri.getHost()==null||!("https".equals(uri.getScheme())||("http".equals(uri.getScheme())&&local)))throw new IllegalStateException("GOOGLE_REDIRECT_URI must be HTTPS (HTTP is allowed only for localhost).");
    }
  }
  public boolean enabled(){return enabled;}
  String frontendUrl(){return frontend;}
  void requireEnabled(){if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Gmail API is not configured. Start the backend with the gmail-api profile and the Google OAuth environment variables.");}
  // ---- OAuth flow -------------------------------------------------------------------------------------------
  String authorizationUrl(Long mailboxId){
    requireEnabled();Instant now=Instant.now();
    pending.values().removeIf(value->value.expires().isBefore(now));
    if(pending.size()>=1000)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many Gmail connection attempts. Retry in a few minutes.");
    String state=randomToken(32),verifier=randomToken(48);
    pending.put(state,new Pending(mailboxId,verifier,now.plusSeconds(600)));
    Map<String,String> query=new LinkedHashMap<>();
    query.put("client_id",clientId);query.put("redirect_uri",redirectUri);query.put("response_type","code");query.put("scope",SCOPES);
    query.put("access_type","offline");query.put("prompt","consent");query.put("include_granted_scopes","false");query.put("state",state);
    query.put("code_challenge",Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier)));query.put("code_challenge_method","S256");
    return AUTH+"?"+form(query);
  }
  void discard(String state){if(state!=null)pending.remove(state);}
  /** Completes the browser redirect. The state is single-use, expires after 10 minutes and carries the mailbox identity. */
  GmailAccount complete(String state,String code){
    requireEnabled();Pending request=state==null?null:pending.remove(state);
    if(request==null||request.expires().isBefore(Instant.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The Gmail connection request expired. Start again from Settings.");
    if(code==null||code.isBlank()||code.length()>2048)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Google did not return an authorization code.");
    Map<String,String> params=new LinkedHashMap<>();
    params.put("code",code);params.put("client_id",clientId);params.put("client_secret",clientSecret);params.put("redirect_uri",redirectUri);
    params.put("grant_type","authorization_code");params.put("code_verifier",request.verifier());
    JsonNode token=tokenRequest(params,false);
    String refresh=text(token,"refresh_token"),access=text(token,"access_token"),scope=" "+text(token,"scope")+" ";
    if(refresh.isBlank()||access.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Google did not grant offline access. Remove this app at myaccount.google.com/permissions and connect again.");
    for(String required:SCOPES.split(" "))if(!scope.contains(" "+required+" "))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Both Gmail send and read permissions must be allowed. Connect again and keep every permission ticked.");
    JsonNode profile;
    try{profile=call("GET",API+"/profile",access,null);}catch(GmailHttpException error){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Connected to Google, but the Gmail profile could not be read.");}
    String address=text(profile,"emailAddress").toLowerCase(Locale.ROOT);
    if(!address.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Google returned an unexpected Gmail address.");
    GmailAccount company=accounts.findFirstByOrderByConnectedAtAsc().orElse(null);
    if(company!=null&&!company.mailboxId.equals(request.mailboxId()))throw new ResponseStatusException(HttpStatus.CONFLICT,"A company Gmail account is already connected. Disconnect it before replacing it.");
    accounts.findByGmailAddress(address).ifPresent(existing->{if(!existing.mailboxId.equals(request.mailboxId()))throw new ResponseStatusException(HttpStatus.CONFLICT,"This Gmail account is already connected.");});
    GmailAccount account=company!=null?company:accounts.findById(request.mailboxId()).orElseGet(GmailAccount::new);
    account.mailboxId=request.mailboxId();account.gmailAddress=address;account.refreshTokenCipher=crypto.encrypt(refresh,String.valueOf(account.mailboxId));
    account.historyId=null;account.lastSyncAt=null;account.connectedAt=Instant.now();account.lastError="";
    accounts.saveAndFlush(account);
    tokens.put(account.mailboxId,new Token(access,expiry(token)));
    return account;
  }
  void disconnect(Long mailboxId){
    GmailAccount account=accounts.findById(mailboxId).orElse(null);tokens.remove(mailboxId);
    if(account==null)return;
    try{
      String refresh=crypto.decrypt(account.refreshTokenCipher,String.valueOf(mailboxId));
      send(HttpRequest.newBuilder(URI.create(REVOKE)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form(Map.of("token",refresh)))).build());
    }catch(RuntimeException ignored){/* revoking is best effort; the local link is removed regardless */}
    accounts.deleteById(mailboxId);
  }
  // ---- Gmail calls ------------------------------------------------------------------------------------------
  Sent send(GmailAccount account,String to,String cc,String subject,String body,List<WebmailAttachment> files){
    String payload;
    try{payload=json.writeValueAsString(Map.of("raw",raw(MailMime.create(mimeFactory,account.gmailAddress,to,cc,subject,body,files))));}
    catch(Exception error){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The message could not be prepared for Gmail.");}
    try{JsonNode result=authed(account,"POST","/messages/send",payload);return new Sent(text(result,"id"),text(result,"threadId"));}
    catch(GmailHttpException error){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Gmail API did not confirm sending (HTTP "+error.status+"). Check Gmail Sent before retrying to avoid duplicates.");}
  }
  JsonNode get(GmailAccount account,String path){return authed(account,"GET",path,null);}
  static String raw(MimeMessage message)throws Exception{
    ByteArrayOutputStream out=new ByteArrayOutputStream();message.writeTo(out);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
  }
  private JsonNode authed(GmailAccount account,String method,String path,String body){
    for(int attempt=0;;attempt++){
      String access=accessToken(account);
      try{return call(method,API+path,access,body);}
      catch(GmailHttpException error){if(error.status==401&&attempt==0){tokens.remove(account.mailboxId);continue;}throw error;}
    }
  }
  private String accessToken(GmailAccount account){
    Token cached=tokens.get(account.mailboxId);if(cached!=null&&cached.expires().isAfter(Instant.now()))return cached.value();
    String refresh;try{refresh=crypto.decrypt(account.refreshTokenCipher,String.valueOf(account.mailboxId));}catch(IllegalStateException error){throw new ReconnectRequired();}
    Map<String,String> params=new LinkedHashMap<>();
    params.put("client_id",clientId);params.put("client_secret",clientSecret);params.put("refresh_token",refresh);params.put("grant_type","refresh_token");
    JsonNode token=tokenRequest(params,true);String access=text(token,"access_token");
    if(access.isBlank())throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Google did not return an access token.");
    tokens.put(account.mailboxId,new Token(access,expiry(token)));return access;
  }
  private JsonNode tokenRequest(Map<String,String> params,boolean refreshing){
    HttpResponse<String> response=send(HttpRequest.newBuilder(URI.create(TOKEN)).timeout(Duration.ofSeconds(20))
      .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(params))).build());
    if(response.statusCode()==200)return parse(response.body());
    if(response.statusCode()==400||response.statusCode()==401){
      String error=text(parse(response.body()),"error");
      if(refreshing&&("invalid_grant".equals(error)||"invalid_client".equals(error)))throw new ReconnectRequired();
      if(!refreshing)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Google rejected the authorization. Start again from Settings.");
    }
    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Google token service is unavailable. Try again later.");
  }
  private JsonNode call(String method,String url,String bearer,String body){
    HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+bearer).header("Accept","application/json");
    if(body==null)builder.method(method,HttpRequest.BodyPublishers.noBody());
    else builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));
    HttpResponse<String> response=send(builder.build());
    if(response.statusCode()/100!=2)throw new GmailHttpException(response.statusCode());
    return parse(response.body());
  }
  private HttpResponse<String> send(HttpRequest request){
    try{return http.send(request,HttpResponse.BodyHandlers.ofString());}
    catch(InterruptedException error){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"The Google request was interrupted.");}
    catch(Exception error){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Cannot reach Google. Check internet access.");}
  }
  private JsonNode parse(String body){try{return json.readTree(body==null?"":body);}catch(Exception error){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Google returned an unreadable response.");}}
  // ---- helpers ----------------------------------------------------------------------------------------------
  static String text(JsonNode node,String field){JsonNode value=node.path(field);return value.isTextual()?value.asText():"";}
  private static Instant expiry(JsonNode token){return Instant.now().plusSeconds(Math.max(30,token.path("expires_in").asLong(3600)-60));}
  private String randomToken(int bytes){byte[] value=new byte[bytes];random.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
  private static byte[] sha256(String value){try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));}catch(Exception error){throw new IllegalStateException(error);}}
  static String form(Map<String,String> values){
    StringJoiner joiner=new StringJoiner("&");
    values.forEach((key,value)->joiner.add(URLEncoder.encode(key,StandardCharsets.UTF_8)+"="+URLEncoder.encode(value,StandardCharsets.UTF_8)));
    return joiner.toString();
  }
}
