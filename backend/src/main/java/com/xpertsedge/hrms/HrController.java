package com.xpertsedge.hrms;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.time.LocalDate;
import static com.xpertsedge.hrms.BasicValidation.*;

@RestController @RequestMapping("/api/hr")
public class HrController {
  private final DepartmentRepo departments;private final EmployeeRepo employees;private final UserRepo users;private final NotificationService notices;
  public HrController(DepartmentRepo d,EmployeeRepo e,UserRepo u,NotificationService n){departments=d;employees=e;users=u;notices=n;}
  private Department department(Long id){return departments.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Department not found"));}
  private Employee employee(Long id){return employees.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Employee not found"));}
  @GetMapping("/departments") public List<Department> departments(){return departments.findAll(org.springframework.data.domain.Sort.by("name"));}
  @PostMapping("/departments") @Transactional public Department createDepartment(@RequestBody Map<String,Object>b){return saveDepartment(new Department(),b);}
  @PutMapping("/departments/{id}") @Transactional public Department editDepartment(@PathVariable Long id,@RequestBody Map<String,Object>b){return saveDepartment(department(id),b);}
  private Department saveDepartment(Department d,Map<String,Object>b){
    String name=text(b,"name",100,true),key=name.toLowerCase(Locale.ROOT);
    if(!key.equals(d.nameKey) && departments.existsByNameKey(key))throw bad("Department name already exists.");
    d.name=name;d.nameKey=key;d.description=text(b,"description",500,false);d.active=active(b,d.active);departments.save(d);notices.add("Department saved",name+" department was saved.");return d;
  }
  @DeleteMapping("/departments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional public void deleteDepartment(@PathVariable Long id){Department d=department(id);if(employees.existsByDepartmentId(id))throw bad("Reassign employees before deleting this department.");departments.delete(d);notices.add("Department deleted",d.name+" department was deleted.");}
  @GetMapping("/employees") public List<Employee> employees(){return employees.findAll(org.springframework.data.domain.Sort.by("employeeCode"));}
  @GetMapping("/self") public Employee self(HttpServletRequest q){AppUser u=users.findByEmail((String)q.getAttribute("email")).orElseThrow();if(u.employeeId==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No employee profile is linked to your account.");return employee(u.employeeId);}
  @PostMapping("/employees") @Transactional public Employee createEmployee(@RequestBody Map<String,Object>b){return saveEmployee(new Employee(),b);}
  @PutMapping("/employees/{id}") @Transactional public Employee editEmployee(@PathVariable Long id,@RequestBody Map<String,Object>b){return saveEmployee(employee(id),b);}
  private Employee saveEmployee(Employee e,Map<String,Object>b){
    String code=text(b,"employeeCode",32,true).toUpperCase(Locale.ROOT),email=email(b);
    if(!code.matches("[A-Z0-9_-]+"))throw bad("Employee code may contain letters, numbers, underscores and dashes.");
    if(!code.equals(e.employeeCode) && employees.existsByEmployeeCode(code))throw bad("Employee code already exists.");
    if(!email.equals(e.email) && employees.existsByEmail(email))throw bad("Employee email already exists.");
    Long dept=id(b,"departmentId");if(dept!=null && !department(dept).active)throw bad("Choose an active department.");
    String type=text(b,"employmentType",20,true),status=text(b,"status",20,true);
    if(!Set.of("FULL_TIME","PART_TIME","CONTRACT","INTERN").contains(type) || !Set.of("ACTIVE","INACTIVE").contains(status))throw bad("Choose a valid employment type and status.");
    try {e.joiningDate=LocalDate.parse(text(b,"joiningDate",10,true));}catch(java.time.format.DateTimeParseException ex){throw bad("Enter a valid joining date (YYYY-MM-DD).");}
    e.employeeCode=code;e.email=email;e.departmentId=dept;e.firstName=text(b,"firstName",100,true);e.lastName=text(b,"lastName",100,false);e.phone=text(b,"phone",32,false);e.jobTitle=text(b,"jobTitle",100,false);e.employmentType=type;e.status=status;employees.save(e);
    notices.add("Employee saved",code+" employee record was saved.");return e;
  }
  @DeleteMapping("/employees/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional public void deleteEmployee(@PathVariable Long id){Employee e=employee(id);if(users.existsByEmployeeId(id))throw bad("Unlink the employee from its user account before deleting.");employees.delete(e);notices.add("Employee deleted",e.employeeCode+" employee record was deleted.");}
}
