# Task 8b — Update per-strategy unit tests

## Context
This is a Spring Boot URL shortener. Package root: `com.avivly.urlshortener`.
The three strategy test files are at:
- `backend/src/test/java/com/avivly/urlshortener/util/strategy/RandomBase62StrategyTest.java`
- `backend/src/test/java/com/avivly/urlshortener/util/strategy/HashTruncateStrategyTest.java`
- `backend/src/test/java/com/avivly/urlshortener/util/strategy/SequentialStrategyTest.java`

The strategy interface changed in a prior task. The new signature is:
```java
String generate(String originalUrl, Long id, Map<String, Object> params)
```
The old signature was `generate(String originalUrl, ShortLink partialEntity)`.

## Goal
Update all three test files so they compile and pass with the new signature,
and add the new test methods listed below.

---

## Changes to `RandomBase62StrategyTest.java`

### Fix existing tests
Replace every call `strategy.generate("https://example.com", dummy)` with
`strategy.generate("https://example.com", null, Map.of())`.

Remove `ShortLink dummy` field and any `ShortLink` import — no longer needed.

### Add new test methods

```java
@Test
void generate_withEmptyParams_usesDefaultLength() {
    assertThat(strategy.generate("https://example.com", null, Map.of())).hasSize(7);
}

@Test
void generate_customLength_returnsCorrectLength() {
    Map<String, Object> params = Map.of("length", 10);
    assertThat(strategy.generate("https://example.com", null, params)).hasSize(10);
}
```

---

## Changes to `HashTruncateStrategyTest.java`

### Fix existing tests
Replace every call `strategy.generate("https://example.com", dummy)` with
`strategy.generate("https://example.com", null, Map.of())`.

Remove `ShortLink dummy` field and any `ShortLink` import.

### Add new test methods

```java
@Test
void generate_withEmptyParams_usesDefaults() {
    String result = strategy.generate("https://example.com", null, Map.of());
    assertThat(result).hasSize(7).matches("[a-zA-Z0-9]+");
}

@Test
void generate_sha512_producesHash() {
    Map<String, Object> params = Map.of("algorithm", "SHA-512", "length", 7);
    String result = strategy.generate("https://example.com", null, params);
    assertThat(result).hasSize(7).matches("[a-zA-Z0-9]+");
}

@Test
void generate_customLength_applied() {
    Map<String, Object> params = Map.of("length", 12);
    assertThat(strategy.generate("https://example.com", null, params)).hasSize(12);
}
```

---

## Changes to `SequentialStrategyTest.java`

### Fix existing tests
The helper method currently builds a `ShortLink` and calls `strategy.generate(url, link)`.
Replace it:
```java
// before
private String encode(long id) {
    ShortLink link = ShortLink.builder().id(id).originalUrl("https://example.com").build();
    return strategy.generate("https://example.com", link);
}

// after
private String encode(long id) {
    return strategy.generate("https://example.com", id, Map.of());
}
```

Fix `generate_nullId_throwsIllegalState`:
```java
// before
ShortLink link = ShortLink.builder().originalUrl("https://example.com").build();
assertThatThrownBy(() -> strategy.generate("https://example.com", link))

// after
assertThatThrownBy(() -> strategy.generate("https://example.com", null, Map.of()))
```

Remove `ShortLink` import and field if present.

### Add new test methods

```java
@Test
void generate_withEmptyParams_usesDefaultPrefix() {
    String result = strategy.generate("https://example.com", 1L, Map.of());
    assertThat(result).doesNotContain("-");
}

@Test
void generate_withPrefix_prependsCorrectly() {
    Map<String, Object> params = Map.of("prefix", "s-");
    String result = strategy.generate("https://example.com", 1L, params);
    assertThat(result).startsWith("s-");
}
```

---

## Done condition
`mvn test -pl backend -Dtest=RandomBase62StrategyTest+HashTruncateStrategyTest+SequentialStrategyTest`
exits 0, all tests (old + new) pass.
