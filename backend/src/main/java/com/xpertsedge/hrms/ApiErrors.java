package com.xpertsedge.hrms;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> conflict() {
    return Map.of("message", "This record conflicts with existing data or is linked to another record. Refresh and check duplicates/dependencies.");
  }
}
