# Agent Prompt: Update LinkService with Ownership Checks (AUTH-10)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (Spring Boot 3.2).
Working directory: project root. AUTH-01 through AUTH-09 are done.
File to modify: `backend/src/main/java/com/avivly/urlshortener/service/LinkService.java`.

Current `LinkService` has three mutating methods:
- `create(CreateLinkRequest req)` — builds a `ShortLink` and saves it
- `update(Long id, UpdateLinkRequest req)` — finds by id, patches fields, saves
- `delete(Long id)` — finds by id, evicts cache, deletes

## Your Task
Add `callerId` (the authenticated user's id) to all three methods and enforce ownership on update/delete.

## Changes to `LinkService.java`

### 1. Add field
```java
private final UserRepository userRepo;
```
Also add import: `import com.avivly.urlshortener.repository.UserRepository;`

### 2. `create` — new signature + set owner before save

Change signature:
```java
public ShortLink create(CreateLinkRequest req, Long callerId)
```

After building `partialEntity` and before the first branch (`if (req.customAlias() ...)`), add:
```java
User owner = userRepo.findById(callerId)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
partialEntity.setOwner(owner);
```

Add import: `import com.avivly.urlshortener.model.User;`

### 3. `update` — new signature + ownership check

Change signature:
```java
public ShortLink update(Long id, UpdateLinkRequest req, Long callerId)
```

After `repo.findById` (the existing `.orElseThrow`), before patching fields, add:
```java
if (!link.getOwner().getId().equals(callerId)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner");
}
```

### 4. `delete` — new signature + ownership check

Change signature:
```java
public void delete(Long id, Long callerId)
```

After `repo.findById` (the existing `.orElseThrow`), before `evictCache`, add:
```java
if (!link.getOwner().getId().equals(callerId)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner");
}
```

## Important
- Use `ResponseStatusException(HttpStatus.FORBIDDEN, ...)` — NOT `AccessDeniedException`.
  Spring Security 6 intercepts `AccessDeniedException` before it reaches `GlobalExceptionHandler`,
  so the existing `handleResponseStatus` handler would never see it. `ResponseStatusException(FORBIDDEN)`
  is caught correctly.
- `GlobalExceptionHandler` needs no changes.

## Acceptance Criteria
- `mvn compile -f backend/pom.xml` exits 0
- All three method signatures updated
- Ownership check in `update` and `delete`; owner set in `create`
