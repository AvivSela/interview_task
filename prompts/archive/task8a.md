# Task 8a — New test class: StrategyParamValidatorTest

## Context
This is a Spring Boot URL shortener. Package root: `com.avivly.urlshortener`.
Test classes live in `backend/src/test/java/com/avivly/urlshortener/`.

`StrategyParamValidator` is a `@Component` but can be instantiated directly in unit tests
with `new StrategyParamValidator()` — no Spring context needed.

`StrategyParamDefinition` constructor signature:
```java
new StrategyParamDefinition(String name, ParamType type, boolean required,
                             String defaultValue, String description,
                             Integer min, Integer max)
```
Factory method for no bounds:
```java
StrategyParamDefinition.of(name, type, required, defaultValue, description)
```

## Goal
Create `StrategyParamValidatorTest.java` with exactly the 12 test methods listed below.
Each test must be a JUnit 5 `@Test`. Use AssertJ assertions.
Throwing `ResponseStatusException` with status 400 should be asserted with:
```java
assertThatThrownBy(() -> validator.validate(...))
    .isInstanceOf(ResponseStatusException.class)
    .hasMessageContaining("...");  // use a substring of the expected message
```

---

## File to create

**Path:** `backend/src/test/java/com/avivly/urlshortener/util/strategy/StrategyParamValidatorTest.java`

Write all 12 test methods. Method names must match exactly:

1. `unknownKey_returns400`
   - Schema has one param `"length"`. Pass `{"bogus": 5}`. Expect 400, message contains `"Unknown"`.

2. `requiredParamMissing_returns400`
   - Schema has one required param `"length"` (INTEGER, required=true, no default). Pass empty map. Expect 400, message contains `"missing"`.

3. `integerParam_invalidString_returns400`
   - Schema has `"length"` INTEGER. Pass `{"length": "abc"}`. Expect 400, message contains `"integer"`.

4. `integerParam_belowMin_returns400`
   - Schema has `"length"` INTEGER, min=4. Pass `{"length": "3"}`. Expect 400, message contains `">= 4"`.

5. `integerParam_aboveMax_returns400`
   - Schema has `"length"` INTEGER, max=20. Pass `{"length": "21"}`. Expect 400, message contains `"<= 20"`.

6. `integerParam_atBounds_passes`
   - Schema has `"length"` INTEGER, min=4, max=20.
   - Pass `{"length": "4"}` → result contains `"length" = 4`.
   - Pass `{"length": "20"}` → result contains `"length" = 20`.

7. `algorithmParam_invalidValue_returns400`
   - Schema has `"algorithm"` STRING. Pass `{"algorithm": "MD5"}`. Expect 400, message contains `"Unsupported algorithm"`.

8. `algorithmParam_allowedValues_pass`
   - Schema has `"algorithm"` STRING. Pass `{"algorithm": "SHA-256"}` → passes (no exception).
   - Pass `{"algorithm": "SHA-512"}` → passes.

9. `prefixParam_invalidChars_returns400`
   - Schema has `"prefix"` STRING. Pass `{"prefix": "../etc"}`. Expect 400, message contains `"prefix"`.

10. `prefixParam_tooLong_returns400`
    - Schema has `"prefix"` STRING. Pass `{"prefix": "aaaaaaaaaaaaaaaaa"}` (17 chars). Expect 400, message contains `"prefix"`.

11. `nullParams_usesDefaults_passes`
    - Schema has `"length"` INTEGER, defaultValue=`"7"`. Pass `null` as rawParams.
    - Result must contain `"length" = 7`.

12. `emptyParams_usesDefaults_passes`
    - Same schema as above. Pass `Map.of()` (empty map).
    - Result must contain `"length" = 7`.

---

## Done condition
`mvn test -pl backend -Dtest=StrategyParamValidatorTest` exits 0, all 12 tests pass.
