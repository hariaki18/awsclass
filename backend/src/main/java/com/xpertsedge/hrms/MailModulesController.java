package com.xpertsedge.hrms;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/webmail") @Transactional
public class MailModulesController {
 private final WebmailAuth auth;private final MailContactRepo contacts;private final MailCalendarEventRepo events;private final MailPreferenceRepo prefs;private final WebmailMessageRepo messages;private final WebmailAttachmentRepo files;
 public MailModulesController(WebmailAuth auth,MailContactRepo contacts,MailCalendarEventRepo events,MailPreferenceRepo prefs,WebmailMessageRepo messages,WebmailAttachmentRepo files){this.auth=auth;this.contacts=contacts;this.events=events;this.prefs=prefs;this.messages=messages;this.files=files;}
 static String text(Map<String,Object>b,String k,int n){return WebmailController.text(b,k,n).trim();}
 static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Item not found.");}
 @GetMapping("/contacts") public List<MailContact> contacts(HttpServletRequest r){return contacts.findByOwnerIdOrderByNameAsc(auth.current(r).id);}
 @PostMapping("/contacts") public MailContact createContact(@RequestBody Map<String,Object>b,HttpServletRequest r){MailContact c=new MailContact();c.id=UUID.randomUUID().toString();c.ownerId=auth.current(r).id;return contact(c,b);}
 @PutMapping("/contacts/{id}") public MailContact editContact(@PathVariable String id,@RequestBody Map<String,Object>b,HttpServletRequest r){return contact(contacts.findByIdAndOwnerId(id,auth.current(r).id).orElseThrow(MailModulesController::missing),b);}
 MailContact contact(MailContact c,Map<String,Object>b){c.name=text(b,"name",100);c.email=text(b,"email",254).toLowerCase(Locale.ROOT);if(c.name.isBlank()||WebmailController.addresses(c.email).size()!=1||!c.email.contains("@")||!WebmailController.addresses(c.email).get(0).equals(c.email))throw WebmailController.bad("Name and one valid email are required.");c.phone=text(b,"phone",32);c.company=text(b,"company",100);c.notes=text(b,"notes",1000);return contacts.save(c);}
 @DeleteMapping("/contacts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteContact(@PathVariable String id,HttpServletRequest r){contacts.delete(contacts.findByIdAndOwnerId(id,auth.current(r).id).orElseThrow(MailModulesController::missing));}
 @GetMapping("/calendar") public List<MailCalendarEvent> calendar(HttpServletRequest r){return events.findByOwnerIdOrderByStartsAtAsc(auth.current(r).id);}
 @PostMapping("/calendar") public MailCalendarEvent createEvent(@RequestBody Map<String,Object>b,HttpServletRequest r){MailCalendarEvent e=new MailCalendarEvent();e.id=UUID.randomUUID().toString();e.ownerId=auth.current(r).id;return event(e,b);}
 @PutMapping("/calendar/{id}") public MailCalendarEvent editEvent(@PathVariable String id,@RequestBody Map<String,Object>b,HttpServletRequest r){return event(events.findByIdAndOwnerId(id,auth.current(r).id).orElseThrow(MailModulesController::missing),b);}
 MailCalendarEvent event(MailCalendarEvent e,Map<String,Object>b){e.title=text(b,"title",150);e.location=text(b,"location",200);e.notes=text(b,"notes",2000);try{e.startsAt=Instant.parse(text(b,"startsAt",40));e.endsAt=Instant.parse(text(b,"endsAt",40));}catch(DateTimeException ex){throw WebmailController.bad("Enter valid start and end dates.");}if(e.title.isBlank()||!e.endsAt.isAfter(e.startsAt))throw WebmailController.bad("Title and an end time after the start are required.");return events.save(e);}
 @DeleteMapping("/calendar/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteEvent(@PathVariable String id,HttpServletRequest r){events.delete(events.findByIdAndOwnerId(id,auth.current(r).id).orElseThrow(MailModulesController::missing));}
 @GetMapping("/preferences") public MailPreference preferences(HttpServletRequest r){return preference(auth.current(r).id);}
 MailPreference preference(Long id){return prefs.findById(id).orElseGet(()->{MailPreference p=new MailPreference();p.ownerId=id;return p;});}
 @PutMapping("/preferences") public MailPreference preferences(@RequestBody Map<String,Object>b,HttpServletRequest r){MailPreference p=preference(auth.current(r).id);p.signature=text(b,"signature",2000);p.theme=text(b,"theme",15);p.density=text(b,"density",15);p.timeZone=text(b,"timeZone",60);if(!Set.of("light","dark").contains(p.theme)||!Set.of("compact","comfortable").contains(p.density)||!(b.get("shortcuts") instanceof Boolean))throw WebmailController.bad("Invalid preferences.");try{ZoneId.of(p.timeZone);}catch(DateTimeException e){throw WebmailController.bad("Invalid time zone.");}p.shortcuts=(Boolean)b.get("shortcuts");return prefs.save(p);}
 @GetMapping("/storage") public Map<String,Object> storage(HttpServletRequest r){Long id=auth.current(r).id;List<WebmailMessage> rows=messages.findAll((root,q,cb)->cb.equal(root.get("ownerId"),id));long body=0,attachments=0;Map<String,Long> counts=new TreeMap<>();for(WebmailMessage m:rows){body+=(m.body==null?"":m.body).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;for(Map<String,Object> f:files.metadata(m.id))attachments+=((Number)f.get("size")).longValue();counts.merge(m.folder,1L,Long::sum);}return Map.of("messages",rows.size(),"bodyBytes",body,"attachmentBytes",attachments,"totalBytes",body+attachments,"folders",counts,"note","Message body and attachment bytes; excludes database overhead. Local MySQL storage.");}
}
