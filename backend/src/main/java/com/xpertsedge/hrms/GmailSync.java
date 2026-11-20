package com.xpertsedge.hrms;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
/**
 * Pulls new Gmail messages into the company Inbox. Replies are routed to the local mailbox that sent their thread.
 * remembers Gmail's history id; later runs use users.history.list so only new messages are fetched.
 * Message ids are de-duplicated through WebmailMessage.sourceKey, so repeated or overlapping syncs are safe.
 */
@Service
public class GmailSync {
  private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(GmailSync.class);
  private final GmailApi api;private final GmailAccountRepo accounts;private final WebmailMessageRepo messages;
  private final WebmailAttachmentRepo attachments;private final WebmailDeletedCaptureRepo deleted;private final LocalMailBridge bridge;
  private final Set<Long> running=ConcurrentHashMap.newKeySet();
  public GmailSync(GmailApi api,GmailAccountRepo accounts,WebmailMessageRepo messages,WebmailAttachmentRepo attachments,WebmailDeletedCaptureRepo deleted,LocalMailBridge bridge){
    this.api=api;this.accounts=accounts;this.messages=messages;this.attachments=attachments;this.deleted=deleted;this.bridge=bridge;
  }
  /** Returns the number of messages imported. A sync already running for this mailbox is skipped (returns 0). */
  public int sync(Long mailboxId){
    if(!running.add(mailboxId))return 0;
    try{
      GmailAccount account=accounts.findById(mailboxId).orElse(null);if(account==null)return 0;
      try{
        int imported=run(account);account.lastSyncAt=Instant.now();account.lastError="";touch(account);return imported;
      }catch(GmailApi.ReconnectRequired error){account.lastError="RECONNECT";touch(account);throw error;}
      catch(RuntimeException error){account.lastError="SYNC_FAILED";touch(account);throw error;}
    }finally{running.remove(mailboxId);}
  }
  private void touch(GmailAccount account){if(accounts.existsById(account.mailboxId))accounts.save(account);}
  private int run(GmailAccount account){
    if(account.historyId==null||account.historyId.isBlank())return initial(account);
    LinkedHashSet<String> ids=new LinkedHashSet<>();String newest=account.historyId,page=null;int pages=0;
    try{
      do{
        JsonNode result=api.get(account,"/history?startHistoryId="+enc(account.historyId)+"&historyTypes=messageAdded&maxResults=100"+(page==null?"":"&pageToken="+enc(page)));
        for(JsonNode entry:result.path("history"))for(JsonNode added:entry.path("messagesAdded")){String id=GmailApi.text(added.path("message"),"id");if(!id.isBlank())ids.add(id);}
        String latest=GmailApi.text(result,"historyId");if(!latest.isBlank())newest=latest;
        page=GmailApi.text(result,"nextPageToken");if(page.isBlank())page=null;
      }while(page!=null&&++pages<50);
    }catch(GmailApi.GmailHttpException error){
      if(error.status==404)return initial(account); // Gmail no longer keeps history that old: fall back to the recent Inbox.
      throw error;
    }
    if(page!=null)return initial(account);
    int imported=0;for(String id:ids)if(importMessage(account,id))imported++;
    account.historyId=newest;return imported;
  }
  private int initial(GmailAccount account){
    String history=GmailApi.text(api.get(account,"/profile"),"historyId");
    JsonNode list=api.get(account,"/messages?labelIds=INBOX&maxResults=20");
    int imported=0;for(JsonNode item:list.path("messages"))if(importMessage(account,GmailApi.text(item,"id")))imported++;
    if(!history.isBlank())account.historyId=history;return imported;
  }
  private boolean importMessage(GmailAccount account,String id){
    if(!id.matches("[A-Za-z0-9_-]{1,64}"))return false;
    String key="gmail:"+id+":"+account.mailboxId;
    if(messages.existsBySourceKey(key)||deleted.existsById(key))return false;
    JsonNode item;
    try{item=api.get(account,"/messages/"+id+"?format=raw");}
    catch(GmailApi.GmailHttpException error){if(error.status==404)return false;throw error;}
    Set<String> labels=new HashSet<>();for(JsonNode label:item.path("labelIds"))labels.add(label.asText());
    if(labels.contains("TRASH"))return false;
    if((labels.contains("SENT")||labels.contains("DRAFT"))&&!labels.contains("INBOX"))return false; // copies of mail sent from this app or Gmail
    MimeMessage mime=null;
    try{mime=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(Base64.getUrlDecoder().decode(GmailApi.text(item,"raw"))));}catch(Exception ignored){}
    String thread=GmailApi.text(item,"threadId");
    Long owner=messages.findFirstByGmailThreadIdOrderByCreatedAtDesc(thread).map(value->value.ownerId).orElse(account.mailboxId);
    WebmailMessage message=new WebmailMessage();
    message.id=UUID.randomUUID().toString();message.ownerId=owner;message.sourceKey=key;message.gmailMessageId=id;
    message.gmailThreadId=thread.isBlank()?null:thread;
    fill(message,mime,account.gmailAddress);
    message.folder=labels.contains("SPAM")?"Spam":"Inbox";message.originalFolder=message.folder;message.deliveryStatus="RECEIVED_GMAIL";message.seen=!labels.contains("UNREAD");
    try{message.createdAt=Instant.ofEpochMilli(Long.parseLong(GmailApi.text(item,"internalDate")));}catch(Exception ignored){}
    message.updatedAt=Instant.now();
    try{messages.saveAndFlush(message);}catch(DataIntegrityViolationException duplicate){return false;}
    if(mime!=null){
      try{List<WebmailAttachment> found=new ArrayList<>();bridge.extract(mime,message.id,found,0,new int[]{0});if(!found.isEmpty())attachments.saveAll(found);}
      catch(Exception error){message.body=cut(cut(message.body,19500)+"\n[Some attachments were not imported; webmail supports 5 files / 1 MB total.]",20000);messages.save(message);}
    }
    return true;
  }
  static void fill(WebmailMessage message,MimeMessage mime,String ownAddress){
    String from="",to="",cc="",replyTo="",subject="",body="";
    if(mime!=null){
      try{from=list(mime.getFrom());}catch(Exception ignored){}
      try{to=list(mime.getRecipients(Message.RecipientType.TO));}catch(Exception ignored){}
      try{cc=list(mime.getRecipients(Message.RecipientType.CC));}catch(Exception ignored){}
      try{String[] header=mime.getHeader("Reply-To");if(header!=null&&header.length>0)replyTo=list(mime.getReplyTo());}catch(Exception ignored){}
      try{subject=mime.getSubject()==null?"":mime.getSubject();}catch(Exception ignored){}
      body=bodyText(mime);
    }
    message.fromAddress=cut(from.isBlank()?"unknown":from,255);
    message.toAddress=to.isBlank()?ownAddress:to;message.ccAddress=cc;message.replyToAddress=replyTo;
    message.subject=cut(subject.replace('\r',' ').replace('\n',' ').trim(),255);
    message.body=cut(body.isBlank()?"[No text content]":body,20000);
  }
  static String list(Address[] values){
    if(values==null)return "";List<String> out=new ArrayList<>();
    for(Address value:values){if(out.size()>=20)break;String text=value instanceof InternetAddress address?address.getAddress():value.toString();if(text!=null&&!text.isBlank())out.add(text.trim());}
    return cut(String.join(", ",out),2000);
  }
  static String bodyText(Part part){
    try{
      String plain=find(part,"text/plain",0,new int[]{0});if(plain!=null&&!plain.isBlank())return plain.strip();
      String html=find(part,"text/html",0,new int[]{0});return html==null?"":htmlToText(html);
    }catch(Exception error){return "";}
  }
  private static String find(Part part,String type,int depth,int[] seen)throws Exception{
    if(depth>12||++seen[0]>200||Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()))return null;
    if(part.isMimeType(type)){Object content=part.getContent();return content instanceof String text?text:null;}
    if(part.isMimeType("multipart/*")){
      Multipart multipart=(Multipart)part.getContent();
      for(int index=0;index<multipart.getCount();index++){String result=find(multipart.getBodyPart(index),type,depth+1,seen);if(result!=null)return result;}
    }
    return null;
  }
  static String htmlToText(String html){
    String text=html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>","").replaceAll("(?i)<br\\s*/?>|</(p|div|tr|li|h[1-6])>","\n").replaceAll("(?s)<[^>]*>","");
    text=text.replace("&nbsp;"," ").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&#39;","'").replace("&amp;","&");
    return text.replaceAll("[ \\t]+\\n","\n").replaceAll("\\n{3,}","\n\n").strip();
  }
  private static String cut(String text,int max){return text.length()>max?text.substring(0,max):text;}
  private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
