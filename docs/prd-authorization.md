# PRD: Link Authorization — Creator-Only Edit & Delete

## Overview

Add JWT-based authentication and link ownership to Avivly so that only the user who created a link can edit or delete it. Short-link redirects and the link list remain fully public.

---

## Problem Statement

Avivly is currently anonymous: any visitor who knows a link's numeric `id` can modify or delete it. There is no concept of identity or ownership. This makes the service unsafe for real use — users cannot trust that their links will persist or remain unchanged.

---

## Goals

- Allow users to register and log in with email + password
- Associate every new link with the authenticated user who created it
- Restrict `PUT /api/links/{id}` and `DELETE /api/links/{id}` to the link's creator
- Return HTTP 401 for unauthenticated write attempts; HTTP 403 for unauthorized ones
- Keep short-link redirects (`GET /{shortCode}`) fully public
- Keep the link list (`GET /api/links`) fully public

## Non-Goals

- Admin roles or super-user overrides
- OAuth2 / social login (e.g., Google, GitHub)
- Per-link sharing or collaborator access
- Rate limiting

---

## User Stories

| # | As a… | I want to… | So that… |
|---|-------|-----------|----------|
| 1 | Visitor | Register with email + password | I have an account |
| 2 | Registered user | Log in and receive a JWT | I can authenticate my requests |
| 3 | Authenticated user | Create a link that is tied to my account | I am its owner |
| 4 | Authenticated user | Edit or delete a link I created | I can manage my own links |
| 5 | Any user | View the full link list and use short-link redirects | Public features remain open |
| 6 | Unauthenticated user | Receive a clear 401 when I try to edit/delete | I know I need to log in |
| 7 | Authenticated user | Receive a clear 403 when I try to edit/delete someone else's link | The system enforces ownership |

---

## Scope Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Auth mechanism | JWT (stateless Bearer tokens) | Fits the REST API; no server-side session state needed |
| Link list visibility | Public — all links visible | No change to current behavior; ownership only gates writes |
| Existing links | Purge on migration | Clean slate; no orphaned data to reason about |

---

## API Contract

### New endpoints

| Method | Path | Auth required | Description |
|--------|------|---------------|-------------|
| `POST` | `/api/auth/register` | No | Register: `{ email, password }` → `{ token }` |
| `POST` | `/api/auth/login` | No | Login: `{ email, password }` → `{ token }` |

### Changed endpoints

| Method | Path | Before | After |
|--------|------|--------|-------|
| `POST` | `/api/links` | Open | Requires Bearer JWT; link is associated with caller |
| `PUT` | `/api/links/{id}` | Open | Requires Bearer JWT + caller must be the owner → 403 otherwise |
| `DELETE` | `/api/links/{id}` | Open | Requires Bearer JWT + caller must be the owner → 403 otherwise |

### Unchanged endpoints

| Method | Path | Notes |
|--------|------|-------|
| `GET` | `/api/links` | Still public |
| `GET` | `/{shortCode}` | Still public (redirect) |
| `GET` | `/api/r/{shortCode}` | Still public (redirect alias) |
| `GET` | `/api/strategies` | Still public |

---

## Technical Requirements

### Backend (Spring Boot / Java)

#### Dependencies to add (`pom.xml`)
- `spring-boot-starter-security`
- `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson`

#### New: User model
- `model/User.java` — fields: `id`, `email` (unique), `passwordHash`, `createdAt`
- `repository/UserRepository.java` — `findByEmail(String)`

#### New: Database migration (V4)
- `db/migration/V4__add_auth.sql`
  - `DELETE FROM short_links;` — purge existing links
  - `CREATE TABLE users (id, email, password_hash, created_at)`
  - `ALTER TABLE short_links ADD COLUMN user_id BIGINT NOT NULL REFERENCES users(id)`
  - Indexes on `users(email)` and `short_links(user_id)`

#### New: JWT security layer
- `security/JwtTokenProvider.java` — generate, validate, and parse tokens
- `security/JwtAuthenticationFilter.java` — `OncePerRequestFilter` that reads the `Authorization: Bearer` header and populates `SecurityContextHolder`
- `config/SecurityConfig.java` — permits public routes, requires auth on write routes, stateless session, registers the JWT filter

#### New: Auth endpoints
- `controller/AuthController.java` — `POST /api/auth/register` and `POST /api/auth/login`
- `service/AuthService.java` — BCrypt password hashing, user creation, credential validation
- `dto/AuthRequest.java` — `{ email, password }`
- `dto/AuthResponse.java` — `{ token }`

#### Modified: Link ownership
- `model/ShortLink.java` — add `@ManyToOne User owner` (not null)
- `service/LinkService.java`:
  - `create()` — accept `userId`, associate link with the User before saving
  - `update()` / `delete()` — check `link.getOwner().getId().equals(callerId)`; throw `AccessDeniedException` if not the owner
- `controller/LinkController.java` — extract `userId` from `SecurityContextHolder` and pass to the service for `create`, `update`, `delete`
- `config/GlobalExceptionHandler.java` — map `AccessDeniedException` → 403 response

#### Modified: CORS
- `config/WebMvcConfig.java` — tighten `allowedOriginPatterns("*")` to the specific frontend origin

---

### Frontend (React)

#### Auth API layer (`api.js`)
- Add `register({ email, password })` and `login({ email, password })`
- Add Axios request interceptor: reads JWT from `localStorage`, attaches `Authorization: Bearer <token>` header

#### Auth UI
- `components/LoginForm.jsx` — email + password form; stores token in `localStorage` on success
- `components/RegisterForm.jsx` — email + password form; stores token on success
- `App.jsx` — add `/login` and `/register` routes; redirect to `/login` on 401 response

#### Conditional edit/delete
- `components/LinksTable.jsx` — show Edit and Delete buttons only when `link.ownerId === currentUser.id`

---

## Data Model Changes

```
users
  id            BIGSERIAL PRIMARY KEY
  email         VARCHAR UNIQUE NOT NULL
  password_hash VARCHAR NOT NULL
  created_at    TIMESTAMP

short_links (existing table — new column)
  ...existing columns...
  user_id       BIGINT NOT NULL REFERENCES users(id)
```

---

## Error Responses

| Scenario | HTTP Status |
|----------|-------------|
| No token on a protected route | 401 Unauthorized |
| Invalid or expired token | 401 Unauthorized |
| Valid token, but not the link's owner | 403 Forbidden |
| Email already registered | 409 Conflict |
| Wrong password on login | 401 Unauthorized |

---

## Files to Create

| File | Purpose |
|------|---------|
| `model/User.java` | User entity |
| `repository/UserRepository.java` | DB access for users |
| `security/JwtTokenProvider.java` | Token generation & validation |
| `security/JwtAuthenticationFilter.java` | Auth filter |
| `config/SecurityConfig.java` | Spring Security configuration |
| `controller/AuthController.java` | Register & login endpoints |
| `service/AuthService.java` | Auth business logic |
| `dto/AuthRequest.java` | Login/register request body |
| `dto/AuthResponse.java` | Token response |
| `db/migration/V4__add_auth.sql` | DB migration |
| `frontend/src/components/LoginForm.jsx` | Login UI |
| `frontend/src/components/RegisterForm.jsx` | Register UI |

## Files to Modify

| File | Change |
|------|--------|
| `model/ShortLink.java` | Add `owner` (User) field |
| `service/LinkService.java` | Ownership checks + creator association |
| `controller/LinkController.java` | Pass caller identity to service |
| `config/WebMvcConfig.java` | Tighten CORS |
| `config/GlobalExceptionHandler.java` | Handle 403 |
| `frontend/src/api.js` | Auth calls + Bearer interceptor |
| `frontend/src/App.jsx` | Auth routes + 401 redirect |
| `frontend/src/components/LinksTable.jsx` | Conditional edit/delete |
| `backend/pom.xml` | Add security + JWT dependencies |

---

## Acceptance Criteria

1. `POST /api/auth/register` creates a user and returns a valid JWT
2. `POST /api/auth/login` with correct credentials returns a JWT
3. `POST /api/links` without a token → 401
4. `POST /api/links` with a valid token → link created and owned by the caller
5. `PUT /api/links/{id}` with the owner's token → 200 OK
6. `PUT /api/links/{id}` with a different user's token → 403 Forbidden
7. `DELETE /api/links/{id}` without a token → 401
8. `GET /api/links` without a token → 200 OK (public)
9. `GET /{shortCode}` without a token → redirect works (public)
10. V4 migration runs cleanly: existing links purged, `users` table created, `user_id` column added to `short_links`
