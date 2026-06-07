# Agent Prompt: Update LinkController (AUTH-11)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2).
Working directory: project root. AUTH-01 through AUTH-10 are done.
File to modify: `backend/src/main/java/com/avivly/urlshortener/controller/LinkController.java`.

Current `LinkController` calls `linkService.create(req)`, `linkService.update(id, req)`, `linkService.delete(id)`
and returns raw `ShortLink` entities. After AUTH-09 and AUTH-10, the service now expects a `callerId` parameter
and the API should return `LinkResponse` DTOs instead of raw entities.

## Your Task
Four changes to `LinkController.java`:

### 1. Add imports
```java
import com.avivly.urlshortener.dto.LinkResponse;
import org.springframework.security.core.context.SecurityContextHolder;
```

### 2. Add `currentUserId()` helper (private method)
```java
private Long currentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return (Long) auth.getPrincipal();
}
```

### 3. Update `create` — pass callerId, return `LinkResponse`

Before:
```java
public ResponseEntity<ShortLink> create(@Valid @RequestBody CreateLinkRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(linkService.create(req));
}
```

After:
```java
public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(LinkResponse.from(linkService.create(req, currentUserId())));
}
```

### 4. Update `getAll` — return `List<LinkResponse>`

Before:
```java
public List<ShortLink> getAll() {
    return linkService.findAll();
}
```

After:
```java
public List<LinkResponse> getAll() {
    return linkService.findAll().stream().map(LinkResponse::from).toList();
}
```

### 5. Update `update` — pass callerId, return `LinkResponse`

Before:
```java
public ShortLink update(@PathVariable Long id, @Valid @RequestBody UpdateLinkRequest req) {
    return linkService.update(id, req);
}
```

After:
```java
public LinkResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLinkRequest req) {
    return LinkResponse.from(linkService.update(id, req, currentUserId()));
}
```

### 6. Update `delete` — pass callerId

Before:
```java
linkService.delete(id);
```

After:
```java
linkService.delete(id, currentUserId());
```

## Notes
- Remove the `import com.avivly.urlshortener.model.ShortLink;` import if it becomes unused after these changes.

## Acceptance Criteria
- `mvn compile -f backend/pom.xml` exits 0
- All endpoints return `LinkResponse` (or `List<LinkResponse>`) instead of raw `ShortLink`
- `currentUserId()` reads from `SecurityContextHolder`
