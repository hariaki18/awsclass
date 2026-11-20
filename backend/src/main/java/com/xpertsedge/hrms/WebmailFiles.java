package com.xpertsedge.hrms;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class WebmailFiles {
  public static final int MAX_BYTES=1024*1024,MAX_FILES=5;
  private final WebmailAttachmentRepo files;
  public WebmailFiles(WebmailAttachmentRepo files){this.files=files;}
  static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
  static String filename(String name){if(name==null)return "attachment";name=name.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);if(name.isBlank()||name.length()>255||name.chars().anyMatch(Character::isISOControl))throw bad("Invalid attachment filename.");return name;}
  static String contentType(String value){return value!=null&&value.length()<=120&&value.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")?value:"application/octet-stream";}
  public WebmailMessage view(WebmailMessage message){message.attachments=files.metadata(message.id);return message;}
  public void apply(WebmailMessage message,Map<String,Object> input,WebmailMessage forwardSource){
    List<WebmailAttachment> existing=files.findByMessageId(message.id);Set<String> keep=new HashSet<>();
    if(input.containsKey("attachmentIds")){
      if(!(input.get("attachmentIds") instanceof List<?> ids)||ids.size()>MAX_FILES)throw bad("Invalid attachment list.");
      for(Object id:ids){if(!(id instanceof String text)||existing.stream().noneMatch(f->f.id.equals(text)))throw bad("Attachment does not belong to this draft.");keep.add((String)id);}
    }else existing.forEach(file->keep.add(file.id));
    List<WebmailAttachment> result=new ArrayList<>(existing.stream().filter(file->keep.contains(file.id)).toList());
    List<WebmailAttachment> forwardFiles=forwardSource==null?List.of():files.findByMessageId(forwardSource.id);
    if(input.containsKey("forwardAttachmentIds")){
      if(!(input.get("forwardAttachmentIds") instanceof List<?> ids)||ids.size()>MAX_FILES)throw bad("Invalid forwarded attachment list.");
      for(Object id:ids)if(!(id instanceof String)||forwardFiles.stream().noneMatch(file->file.id.equals(id)))throw bad("Forwarded attachment does not belong to this message.");
      forwardFiles=forwardFiles.stream().filter(file->ids.contains(file.id)).toList();
    }
    for(WebmailAttachment source:forwardFiles){
      WebmailAttachment copy=new WebmailAttachment();copy.id=UUID.randomUUID().toString();copy.messageId=message.id;copy.filename=source.filename;copy.contentType=source.contentType;copy.data=source.data.clone();copy.byteSize=copy.data.length;result.add(copy);
    }
    if(input.containsKey("attachments")){
      if(!(input.get("attachments") instanceof List<?> uploads)||uploads.size()>MAX_FILES)throw bad("Maximum 5 attachments.");
      for(Object item:uploads){
        if(!(item instanceof Map<?,?> upload)||!(upload.get("data") instanceof String encoded)||encoded.length()>((MAX_BYTES+2)/3)*4)throw bad("Invalid or oversized attachment.");
        byte[] data;try{data=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException error){throw bad("Invalid attachment data.");}
        if(data.length>MAX_BYTES)throw bad("Attachments must total at most 1 MB.");
        if(!(upload.get("name") instanceof String name))throw bad("Attachment filename is required.");
        WebmailAttachment file=new WebmailAttachment();file.id=UUID.randomUUID().toString();file.messageId=message.id;file.filename=filename(name);file.contentType=contentType(upload.get("contentType") instanceof String type?type:null);file.data=data;file.byteSize=data.length;result.add(file);
      }
    }
    if(result.size()>MAX_FILES||result.stream().mapToLong(file->file.byteSize).sum()>MAX_BYTES)throw bad("Maximum 5 attachments, 1 MB total.");
    files.deleteAll(existing.stream().filter(file->!keep.contains(file.id)).toList());files.saveAll(result);
  }
}
