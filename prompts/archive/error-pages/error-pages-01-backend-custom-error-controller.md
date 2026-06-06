# Task: Backend — CustomErrorController

## Goal

Create `backend/src/main/java/com/avivly/urlshortener/controller/CustomErrorController.java`.

This controller claims Spring Boot's `/error` mapping so that any unhandled request or exception returns a polished HTML page (for browser requests) or a consistent JSON body (for API requests), instead of the Whitelabel fallback.

No other files need to change for this task.

---

## File to create

```
backend/src/main/java/com/avivly/urlshortener/controller/CustomErrorController.java
```

---

## Full implementation spec

### Class declaration

```java
package com.avivly.urlshortener.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<?> handleError(HttpServletRequest request) { ... }
}
```

### Request-type detection (JSON vs HTML)

```java
String accept = request.getHeader("Accept");
String uri = String.valueOf(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));

boolean jsonRequest =
    (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))
    || uri.startsWith("/api/");
```

### Status code resolution

```java
Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
int status = statusAttr != null ? Integer.parseInt(statusAttr.toString()) : 500;
HttpStatus httpStatus;
try {
    httpStatus = HttpStatus.valueOf(status);
} catch (IllegalArgumentException e) {
    httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
}
```

### JSON branch (returned when `jsonRequest == true`)

Shape must match `GlobalExceptionHandler` so the API contract stays consistent:

```java
return ResponseEntity.status(status).body(Map.of(
    "status",  status,
    "error",   httpStatus.getReasonPhrase(),
    "message", descriptionFor(status)
));
```

### HTML branch

```java
String statusText = httpStatus.getReasonPhrase();
String description = descriptionFor(status);

return ResponseEntity.status(status)
    .contentType(MediaType.TEXT_HTML)
    .body(htmlPage(status, statusText, description));
```

---

## Private helper: `descriptionFor(int status)`

Returns a `String` from the following map; default case: `"Something went wrong. Please try again later."`

| Status | Message |
|--------|---------|
| 400 | `The request was invalid or malformed.` |
| 403 | `You don't have permission to access this resource.` |
| 404 | `The page you're looking for doesn't exist.` |
| 405 | `The HTTP method is not allowed for this endpoint.` |
| 500 | `An unexpected error occurred on our end.` |
| 503 | `The service is temporarily unavailable.` |

---

## Private helper: `iconFor(int status)`

Returns an inline `<svg>` string (32×32, Heroicons outline, stroke `#2563eb`, strokeWidth `1.5`).

Use these exact SVG path strings:

| Status | Heroicon | `d` attribute |
|--------|----------|---------------|
| 400 | x-circle | `M9.75 9.75l4.5 4.5m0-4.5l-4.5 4.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z` |
| 403 | lock-closed | `M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z` |
| 404 | magnifying-glass | `M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z` |
| 405 | no-symbol | `M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636` |
| 500 | wrench-screwdriver | `M11.42 15.17L17.25 21A2.652 2.652 0 0021 17.25l-5.877-5.877M11.42 15.17l2.496-3.03c.317-.384.74-.626 1.208-.766M11.42 15.17l-4.655 5.653a2.548 2.548 0 11-3.586-3.586l6.837-5.63m5.108-.233c.55-.164 1.163-.188 1.743-.14a4.5 4.5 0 004.486-6.336l-3.276 3.277a3.004 3.004 0 01-2.25-2.25l3.276-3.276a4.5 4.5 0 00-6.336 4.486c.091 1.076-.071 2.264-.904 2.95l-.102.085m-1.745 1.437L5.909 7.5H4.5L2.25 3.75l1.5-1.5L7.5 4.5v1.409l4.26 4.26m-1.745 1.437l1.745-1.437m6.615 8.206L15.75 15.75M4.867 19.125h.008v.008h-.008v-.008z` |
| 503 | clock | `M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z` |
| default | exclamation-circle | `M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z` |

Each SVG element:
```html
<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"
     fill="none" viewBox="0 0 24 24"
     stroke="#2563eb" stroke-width="1.5"
     stroke-linecap="round" stroke-linejoin="round">
  <path d="{d}" />
</svg>
```

---

## Private helper: `htmlPage(int status, String statusText, String description)`

Returns a self-contained HTML string (text block). **Inline CSS only — no `<link>`, `<script>`, or external resources.** Target weight < 3 KB. No stack traces.

### Structure

```
<body> (flex column, min-height 100vh, background #f9fafb)
  <header> (background #2563eb, color white, padding 1rem 1.5rem, box-shadow)
    <h1> "URL Shortener"
  <main> (flex-1, display flex, align-items center, justify-content center)
    <div class="card"> (background white, border-radius .75rem, box-shadow, max-width 420px, padding 2.5rem 2rem, text-align center)
      <div class="icon-wrap"> (display inline-flex, width 4rem, height 4rem, border-radius 50%, background #eff6ff, align/justify center, margin-bottom 1rem)
        {iconFor(status)}
      <div class="code">   {status}       — font-size 5rem, font-weight 800, color #2563eb, line-height 1
      <p class="title">    {statusText}   — font-size 1.2rem, font-weight 600, color #111827, margin .5rem 0
      <p class="desc">     {description}  — font-size 0.9rem, color #6b7280, margin 0 0 1.5rem
      <a href="/" class="btn"> "Back to Dashboard"
  <footer> (text-align center, font-size .75rem, color #9ca3af, padding 1rem)
    "Avivly URL Shortener"
```

### CTA button style

```css
.btn {
  display: inline-block;
  background: #2563eb;
  color: white;
  text-decoration: none;
  padding: .625rem 1.5rem;
  border-radius: .5rem;
  font-size: .9rem;
  font-weight: 500;
}
.btn:hover { background: #1d4ed8; }
```

### Responsive rule — single `@media (max-width: 480px)`

- Card padding reduces to `2rem 1.5rem`
- `.code` font-size reduces to `3.5rem`

---

## Verification

Run the existing test suite to confirm nothing breaks:

```bash
cd backend && mvn test
```

Then start the backend (`mvn spring-boot:run`) and manually verify:

1. `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/nonexistent` → `404`
2. `curl -s -H "Accept: application/json" http://localhost:8080/api/nonexistent` → JSON with keys `status`, `error`, `message`
3. `curl -s http://localhost:8080/some-unknown-path` → HTML containing "Back to Dashboard"
