# Fix 1 — Add `GlobalExceptionHandler` for validation errors

## Context

**Prerequisites:** All previous fixes (#10, #8, #7, #6, #2, #5, #4, #3, #9) applied.

`backend/src/main/resources/application.yml:20` sets `include-message: never`, which strips the `message` field from **all** 4xx/5xx responses to prevent accidental leakage of exception messages from unexpected code paths. This is intentional and should not be changed.

`frontend/src/components/LinkForm.jsx:94` reads `err.response?.data?.message` and renders it as the form validation error text. With `include-message: never`, every validation failure falls through to the generic "Something went wrong." string — the specific field error is invisible to the user.

## Objective

Add a `@RestControllerAdvice` that intercepts `MethodArgumentNotValidException` and `ResponseStatusException` and explicitly writes a `{"message": "..."}` body. Do **not** change `application.yml` or `LinkForm.jsx`.

## Implementation

Create new file: `backend/src/main/java/com/avivly/urlshortener/config/GlobalExceptionHandler.java`

```java
package com.avivly.urlshortener.config;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return Map.of("message", msg);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String msg = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", msg));
    }
}
```

## Verify

```bash
cd backend && ./mvnw test
```

Full test suite must stay green. Pay attention to any controller integration tests that assert on error response bodies — they should now see `{"message": "..."}` instead of an empty body.

## Commit

`fix: add GlobalExceptionHandler to surface validation messages despite include-message:never (#1)`
