package com.commoditycx.api;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(ResponseStatusException.class)
 ResponseEntity<?> status(ResponseStatusException ex){return ResponseEntity.status(ex.getStatusCode()).body(Map.of("timestamp",Instant.now(),"status",ex.getStatusCode().value(),"error",ex.getReason()==null?"Request failed":ex.getReason()));}
 @ExceptionHandler(DuplicateKeyException.class)
 ResponseEntity<?> duplicate(DuplicateKeyException ex){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("timestamp",Instant.now(),"status",409,"error","A record with a unique value already exists"));}
 @ExceptionHandler(IllegalArgumentException.class)
 ResponseEntity<?> invalid(IllegalArgumentException ex){return ResponseEntity.badRequest().body(Map.of("timestamp",Instant.now(),"status",400,"error",ex.getMessage()==null?"Invalid request":ex.getMessage()));}
}
