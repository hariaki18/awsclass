package com.xpertsedge.hrms;
import java.util.Map;
/** Provider status boundary. Real provisioning requires a selected, authenticated provider adapter. */
public interface MailProvider {
  Map<String,Object> status();
}
