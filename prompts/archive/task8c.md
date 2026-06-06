# Task 8c — New StrategyControllerTest + additions to LinkControllerIntegrationTest

## Context
This is a Spring Boot URL shortener. Package root: `com.avivly.urlshortener`.
Integration tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`
and `@ActiveProfiles("test")`. See existing `LinkControllerIntegrationTest` for the pattern.

`CreateLinkRequest` record constructor (7 args, in order):
```java
new CreateLinkRequest(originalUrl, customAlias, strategy, strategyParams, maxClicks, expiresAt, tags)
```

`GET /api/strategies` returns a JSON object like:
```json
{
  "RANDOM_BASE62": [{ "name": "length", "type": "integer", "required": false, "description": "..." }],
  ...
}
```
The response must NOT contain keys `defaultValue`, `min`, or `max`.

---

## File 1 — Create `StrategyControllerTest.java`

**Path:** `backend/src/test/java/com/avivly/urlshortener/StrategyControllerTest.java`

Write exactly these two test methods:

### `GET_api_strategies_returns200_withAllStrategies`
- `GET /api/strategies` → status 200
- Response body is a JSON object containing keys `"RANDOM_BASE62"`, `"HASH_TRUNCATE"`, `"SEQUENTIAL"`
- Each value is a non-empty JSON array

### `GET_api_strategies_doesNotExposeDefaultValueOrMinOrMax`
- `GET /api/strategies` → parse body as `String`
- Assert the body string does NOT contain `"defaultValue"`
- Assert the body string does NOT contain `"\"min\""` (JSON key min)
- Assert the body string does NOT contain `"\"max\""` (JSON key max)

Use `TestRestTemplate` and `@LocalServerPort` — same pattern as `LinkControllerIntegrationTest`.
Use `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` and `@ActiveProfiles("test")`.
Parse the response as `Map<String, Object>` using `ParameterizedTypeReference` or
`ObjectMapper` — your choice.

---

## File 2 — Add to `LinkControllerIntegrationTest.java`

**Path:** `backend/src/test/java/com/avivly/urlshortener/LinkControllerIntegrationTest.java`

Add the following 6 test methods to the existing class. Do not modify any existing tests.

### `createLink_withValidStrategyParams_returns201`
```java
CreateLinkRequest req = new CreateLinkRequest(
    "https://example.com/params-valid", null, "RANDOM_BASE62",
    Map.of("length", 10), null, null, null);
ResponseEntity<ShortLink> response = restTemplate.postForEntity(url("/api/links"), req, ShortLink.class);
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
assertThat(response.getBody().getShortCode()).hasSize(10);
```

### `createLink_withUnknownParamKey_returns400`
```java
CreateLinkRequest req = new CreateLinkRequest(
    "https://example.com/params-unknown", null, "RANDOM_BASE62",
    Map.of("bogusKey", 5), null, null, null);
ResponseEntity<String> response = restTemplate.postForEntity(url("/api/links"), req, String.class);
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
```

### `createLink_withOutOfRangeLength_returns400`
```java
CreateLinkRequest req = new CreateLinkRequest(
    "https://example.com/params-range", null, "RANDOM_BASE62",
    Map.of("length", 99), null, null, null);
ResponseEntity<String> response = restTemplate.postForEntity(url("/api/links"), req, String.class);
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
```

### `createLink_withInvalidAlgorithm_returns400`
```java
CreateLinkRequest req = new CreateLinkRequest(
    "https://example.com/params-algo", null, "HASH_TRUNCATE",
    Map.of("algorithm", "MD5"), null, null, null);
ResponseEntity<String> response = restTemplate.postForEntity(url("/api/links"), req, String.class);
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
```

### `createLink_withNullStrategyParams_usesDefaults`
```java
CreateLinkRequest req = new CreateLinkRequest(
    "https://example.com/params-null", null, "RANDOM_BASE62",
    null, null, null, null);
ResponseEntity<ShortLink> response = restTemplate.postForEntity(url("/api/links"), req, ShortLink.class);
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
assertThat(response.getBody().getShortCode()).hasSize(7);
```

### `createLink_strategyParams_integerDeserializedCorrectly`
```java
// Verifies Jackson round-trip: integer sent as JSON number, coerced correctly by validator
CreateLinkRequest req = new CreateLinkRequest(
    "https://example.com/params-jackson", null, "RANDOM_BASE62",
    Map.of("length", 8), null, null, null);
ResponseEntity<ShortLink> response = restTemplate.postForEntity(url("/api/links"), req, ShortLink.class);
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
assertThat(response.getBody().getShortCode()).hasSize(8);
```

---

## Done condition
`mvn test -pl backend -Dtest=StrategyControllerTest+LinkControllerIntegrationTest`
exits 0, all tests (old + new) pass.
