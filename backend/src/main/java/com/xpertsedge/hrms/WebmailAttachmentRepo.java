package com.xpertsedge.hrms;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface WebmailAttachmentRepo extends JpaRepository<WebmailAttachment,String> {
  List<WebmailAttachment> findByMessageId(String messageId);
  @Query("select new map(a.id as id,a.filename as name,a.contentType as contentType,a.byteSize as size) from WebmailAttachment a where a.messageId=:messageId")
  List<Map<String,Object>> metadata(@org.springframework.data.repository.query.Param("messageId") String messageId);
}
