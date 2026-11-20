package com.xpertsedge.hrms;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import static com.xpertsedge.hrms.BasicValidation.*;

@RestController @RequestMapping("/api/org")
public class OrgController {
  private final UserRepo users;private final EmployeeRepo employees;private final AuthSessionRepo sessions;private final LoginAttemptRepo attempts;private final BCryptPasswordEncoder enc;private final NotificationService notices;
  public OrgController(UserRepo u,EmployeeRepo e,AuthSessionRepo s,LoginAttemptRepo a,BCryptPasswordEncoder p,NotificationService n){users=u;employees=e;sessions=s;attempts=a;enc=p;notices=n;}
  private AppUser user(Long id){return users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));}
  public static Map<String,Object> viewUser(AppUser u){
    String name=u.name==null || u.name.isBlank()?u.email:u.name.trim();String[] parts=name.split("\\s+");
    String initials=new String(Character.toChars(parts[0].codePointAt(0)))+(parts.length>1?new String(Character.toChars(parts[parts.length-1].codePointAt(0))):"");
    Map<String,Object> result=new LinkedHashMap<>();result.put("id",u.id);result.put("name",name);result.put("email",u.email);result.put("initials",initials.toUpperCase(Locale.ROOT));result.put("role",u.role);result.put("active",u.active);result.put("employeeId",u.employeeId);return result;
  }
  @GetMapping("/users") public List<Map<String,Object>> users(){return users.findAll(org.springframework.data.domain.Sort.by("name")).stream().map(OrgController::viewUser).toList();}
  @PostMapping("/users") @Transactional public Map<String,Object> create(@RequestBody Map<String,Object>b){
    AppUser u=new AppUser();u.email=email(b);if(users.findByEmail(u.email).isPresent())throw bad("This email already has an account.");u.passwordHash=enc.encode(password(b));fill(u,b);users.save(u);notices.add("User account created","A user account was created. Share credentials securely; no invitation email was sent.");return viewUser(u);
  }
  private void fill(AppUser u,Map<String,Object>b){
    u.name=text(b,"name",200,true);String role=text(b,"role",20,true);
    if(!Set.of("ADMIN","HR_ADMIN","MAIL_ADMIN","EMPLOYEE").contains(role))throw bad("Choose a valid role.");
    Long employeeId=id(b,"employeeId");if(employeeId!=null)employees.findById(employeeId).orElseThrow(()->bad("Choose an existing employee."));
    u.role=role;u.active=active(b,u.active);u.employeeId=employeeId;
  }
  @PutMapping("/users/{id}") @Transactional public Map<String,Object> edit(@PathVariable Long id,@RequestBody Map<String,Object>b,HttpServletRequest q){
    users.lockActiveAdmins();AppUser u=user(id);String oldRole=u.role;boolean oldActive=u.active;
    fill(u,b);
    if(u.email.equals(q.getAttribute("email")) && (!u.active || !u.role.equals("ADMIN")))throw bad("You cannot deactivate or remove your own administrator role.");
    if(oldActive && "ADMIN".equals(oldRole) && (!u.active || !"ADMIN".equals(u.role)) && users.countByRoleAndActiveTrue("ADMIN")==0)throw bad("At least one active administrator is required.");
    users.save(u);if(!oldRole.equals(u.role) || oldActive!=u.active)sessions.deleteByEmail(u.email);
    notices.add("User account updated","A user account's profile, role or active status was updated.");return viewUser(u);
  }
  @PostMapping("/users/{id}/reset-password") @Transactional public Map<String,Boolean> reset(@PathVariable Long id,@RequestBody Map<String,Object>b,HttpServletRequest q){
    AppUser u=user(id);if(u.email.equals(q.getAttribute("email")))throw bad("Use the authenticated change-password flow for your own password.");
    u.passwordHash=enc.encode(password(b));users.save(u);sessions.deleteByEmail(u.email);attempts.deleteById(u.email);notices.add("User password reset","A user password was reset and its sessions were revoked.");return Map.of("ok",true);
  }
  @DeleteMapping("/users/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional public void delete(@PathVariable Long id,HttpServletRequest q){
    users.lockActiveAdmins();AppUser u=user(id);if(u.email.equals(q.getAttribute("email")))throw bad("You cannot delete your own account.");
    if(u.active && "ADMIN".equals(u.role) && users.countByRoleAndActiveTrue("ADMIN")<=1)throw bad("At least one active administrator is required.");
    sessions.deleteByEmail(u.email);attempts.deleteById(u.email);users.delete(u);notices.add("User account deleted","A user account and its sessions were removed; any linked employee record remains.");
  }
}
