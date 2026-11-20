package com.xpertsedge.hrms;
public interface MailCalendarEventRepo extends org.springframework.data.jpa.repository.JpaRepository<MailCalendarEvent,String>{java.util.List<MailCalendarEvent> findByOwnerIdOrderByStartsAtAsc(Long ownerId); java.util.Optional<MailCalendarEvent> findByIdAndOwnerId(String id,Long ownerId);}
