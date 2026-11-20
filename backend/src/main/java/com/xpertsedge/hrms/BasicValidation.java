package com.xpertsedge.hrms;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
final class BasicValidation {
  static ResponseStatusException bad(String text) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
  static String text(Map<String,Object> b,String key,int max,boolean required) {
    Object raw=b.get(key);
    if(raw==null && !required)return "";
    if(!(raw instanceof String))throw bad(key+" must be text.");
    String value=((String)raw).trim();
    if((required && value.isBlank()) || value.length()>max || value.chars().anyMatch(Character::isISOControl))throw bad("Enter a valid "+key+" (maximum "+max+" characters).");
    return value;
  }
  static String email(Map<String,Object>b) {String value=text(b,"email",254,true).toLowerCase(Locale.ROOT);if(!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw bad("Enter a valid email.");return value;}
  static Long id(Map<String,Object>b,String key) {
    if(b.get(key)==null || "".equals(b.get(key)))return null;
    try {long value=Long.parseLong(b.get(key).toString());if(value>0)return value;}catch(NumberFormatException ignored){}
    throw bad("Choose a valid "+key+".");
  }
  static boolean active(Map<String,Object>b,boolean fallback) {if(!b.containsKey("active"))return fallback;if(!(b.get("active") instanceof Boolean))throw bad("active must be true or false.");return (Boolean)b.get("active");}
  static String password(Map<String,Object>b) {String p=b.get("password") instanceof String?(String)b.get("password"):"";if(p.isBlank() || p.length()<12 || p.getBytes(StandardCharsets.UTF_8).length>72)throw bad("Use a password of at least 12 characters, maximum 72 bytes.");return p;}
}
