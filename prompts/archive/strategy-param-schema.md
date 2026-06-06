# Strategy Parameter Schema — Implementation Plan (v2)

> Revised after multi-perspective review: backend architecture, security, frontend/UX, and testing.
> Review findings are called out inline where they changed the design.

---

## Goal

Make each code-generation strategy self-describing: every strategy declares the parameters it
accepts (name, type, required, default, min/max). The backend validates incoming params against
that schema. The frontend fetches the schema and renders the correct input fields dynamically.

---

## Current State

| What | State |
|------|-------|
| `RandomBase62Strategy` | Always generates exactly 7 chars — hardcoded |
| `HashTruncateStrategy` | Always SHA-256, always 7 chars — hardcoded |
| `SequentialStrategy` | Always raw Base62(id), no prefix — hardcoded |
| `CreateLinkRequest` | No `strategyParams` field |
| `LinkForm.jsx` | No strategy selector; silently always uses `RANDOM_BASE62` |

---

## Backend Changes

### 1. New enum: `ParamType.java`

> **Architecture review:** `type` as a raw `String` ("number", "string") is fragile. Use an enum
> so the compiler catches typos and serialization is unambiguous.

```
backend/src/main/java/com/avivly/urlshortener/util/strategy/ParamType.java
```

```java
public enum ParamType {
    STRING,
    INTEGER,
    BOOLEAN
}
```

---

### 2. New record: `StrategyParamDefinition.java`

> **Architecture review:** `Object defaultValue` breaks under Jackson deserialization (`Integer`
> vs `Long` vs `Double`).
> **Security review:** same `Object` type enables gadget-chain deserialization if polymorphic
> Jackson config is ever enabled.
> **Fix:** `defaultValue` is a `String`; it is coerced to the declared `ParamType` at validation
> time. Add `min`/`max` for numeric bounds enforcement.

```
backend/src/main/java/com/avivly/urlshortener/util/strategy/StrategyParamDefinition.java
```

```java
public record StrategyParamDefinition(
    String   name,          // key used in the API payload, e.g. "length"
    ParamType type,         // STRING | INTEGER | BOOLEAN
    boolean  required,
    String   defaultValue,  // always a String; coerced to `type` at validation time
    String   description,   // shown in the UI as a label/placeholder
    Integer  min,           // inclusive lower bound for INTEGER params; null = no bound
    Integer  max            // inclusive upper bound for INTEGER params; null = no bound
) {}
```

**Convenience factory for params without bounds:**

```java
public static StrategyParamDefinition of(String name, ParamType type,
                                          boolean required, String defaultValue,
                                          String description) {
    return new StrategyParamDefinition(name, type, required, defaultValue, description, null, null);
}
```

---

### 3. New component: `StrategyParamValidator.java`

> **Architecture review:** Validation logic does not belong in `LinkService`. Move it to a
> dedicated component so every caller goes through one place and `LinkService` stays focused.

```
backend/src/main/java/com/avivly/urlshortener/util/strategy/StrategyParamValidator.java
```

Responsibilities:
1. Reject unknown keys (keys not declared in the schema) → `400`
2. Enforce required params → `400`
3. Coerce and type-check each value against its declared `ParamType` → `400`
4. Enforce `min`/`max` on `INTEGER` params → `400`
5. Enforce the algorithm allowlist for `HashTruncateStrategy` → `400`

```java
@Component
public class StrategyParamValidator {

    // Only these algorithm names may be passed by callers.
    // See security note in §4.
    private static final Set<String> ALLOWED_ALGORITHMS =
        Set.of("SHA-256", "SHA-512");

    /**
     * Validates params against the schema.
     * Returns a clean, coerced Map<String, Object> ready for use by the strategy.
     * Throws ResponseStatusException(400) on any violation.
     */
    public Map<String, Object> validate(List<StrategyParamDefinition> schema,
                                        Map<String, Object> rawParams) {
        Map<String, Object> params = rawParams == null ? Map.of() : rawParams;
        Map<String, StrategyParamDefinition> byName = schema.stream()
            .collect(Collectors.toMap(StrategyParamDefinition::name, d -> d));

        // Reject unknown keys
        for (String key : params.keySet()) {
            if (!byName.containsKey(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown strategy parameter: " + key);
            }
        }

        Map<String, Object> coerced = new HashMap<>();
        for (StrategyParamDefinition def : schema) {
            Object raw = params.getOrDefault(def.name(),
                def.defaultValue() != null ? def.defaultValue() : null);

            if (raw == null) {
                if (def.required()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Required strategy parameter missing: " + def.name());
                }
                continue; // optional, absent → strategy uses its own default
            }

            coerced.put(def.name(), coerce(def, raw));
        }
        return coerced;
    }

    private Object coerce(StrategyParamDefinition def, Object raw) {
        return switch (def.type()) {
            case INTEGER -> {
                int value;
                try {
                    value = Integer.parseInt(raw.toString());
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parameter '" + def.name() + "' must be an integer");
                }
                if (def.min() != null && value < def.min()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parameter '" + def.name() + "' must be >= " + def.min());
                }
                if (def.max() != null && value > def.max()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parameter '" + def.name() + "' must be <= " + def.max());
                }
                yield value;
            }
            case STRING -> {
                String value = raw.toString();
                // Algorithm allowlist enforced here so it applies regardless of which
                // code path reaches the validator.
                if ("algorithm".equals(def.name()) && !ALLOWED_ALGORITHMS.contains(value)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported algorithm '" + value +
                        "'. Allowed: " + ALLOWED_ALGORITHMS);
                }
                // Prefix safety: only alphanumeric, hyphen, underscore, max 16 chars.
                if ("prefix".equals(def.name()) && !value.matches("[A-Za-z0-9_\\-]{0,16}")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parameter 'prefix' must match [A-Za-z0-9_-]{0,16}");
                }
                yield value;
            }
            case BOOLEAN -> {
                String s = raw.toString().toLowerCase();
                if (!s.equals("true") && !s.equals("false")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parameter '" + def.name() + "' must be true or false");
                }
                yield Boolean.parseBoolean(s);
            }
        };
    }
}
```

---

### 4. Update: `CodeGenerationStrategy.java`

> **Architecture review:** Passing `ShortLink partialEntity` couples code generation to the
> persistence model. Strategies only need the original URL and their params. `SequentialStrategy`
> is the only exception — it needs the DB-assigned ID. Pass that as a plain `Long`.

```java
public interface CodeGenerationStrategy {

    /**
     * @param originalUrl the URL being shortened
     * @param id          the persisted entity ID; null for strategies that don't use it
     * @param params      validated, coerced param map (never null, may be empty)
     */
    String generate(String originalUrl, Long id, Map<String, Object> params);

    List<StrategyParamDefinition> paramSchema();
}
```

`getParamSchema()` renamed to `paramSchema()` to follow Java record/interface convention (no
`get` prefix on non-bean methods).

---

### 5. Update: `RandomBase62Strategy.java`

**Schema:**

| name | type | required | default | min | max | description |
|------|------|----------|---------|-----|-----|-------------|
| `length` | INTEGER | false | `"7"` | 4 | 20 | Number of characters to generate |

```java
private static final List<StrategyParamDefinition> SCHEMA = List.of(
    new StrategyParamDefinition("length", ParamType.INTEGER, false, "7",
        "Number of characters to generate", 4, 20)
);

@Override
public List<StrategyParamDefinition> paramSchema() { return SCHEMA; }

@Override
public String generate(String originalUrl, Long id, Map<String, Object> params) {
    int length = (int) params.getOrDefault("length", 7);
    return Base62.generate(length);
}
```

---

### 6. Update: `HashTruncateStrategy.java`

> **Security review:** Passing attacker-supplied strings directly to `MessageDigest.getInstance()`
> is algorithm injection. The fix is an allowlist in the validator (§3) — the strategy itself
> trusts the already-validated value.

**Schema:**

| name | type | required | default | min | max | description |
|------|------|----------|---------|-----|-----|-------------|
| `length` | INTEGER | false | `"7"` | 4 | 20 | Characters to take from hash output |
| `algorithm` | STRING | false | `"SHA-256"` | — | — | Hash function: `SHA-256` or `SHA-512` |

```java
private static final List<StrategyParamDefinition> SCHEMA = List.of(
    new StrategyParamDefinition("length", ParamType.INTEGER, false, "7",
        "Characters to take from hash output", 4, 20),
    StrategyParamDefinition.of("algorithm", ParamType.STRING, false, "SHA-256",
        "Hash algorithm: SHA-256 or SHA-512")
);

@Override
public List<StrategyParamDefinition> paramSchema() { return SCHEMA; }

@Override
public String generate(String originalUrl, Long id, Map<String, Object> params) {
    String algorithm = (String) params.getOrDefault("algorithm", "SHA-256");
    int length = (int) params.getOrDefault("length", 7);
    // algorithm is already allowlist-validated by StrategyParamValidator
    MessageDigest digest = MessageDigest.getInstance(algorithm);
    // ... loop to `length`
}
```

---

### 7. Update: `SequentialStrategy.java`

> **Security review:** Unconstrained `prefix` allows log injection, path collision, and delimiter
> abuse. The validator enforces `[A-Za-z0-9_-]{0,16}` before the value reaches here.

**Schema:**

| name | type | required | default | min | max | description |
|------|------|----------|---------|-----|-----|-------------|
| `prefix` | STRING | false | `""` | — | — | Prepended to the encoded ID (e.g. `"s-"`). Max 16 chars, alphanumeric/hyphen/underscore only |

```java
@Override
public String generate(String originalUrl, Long id, Map<String, Object> params) {
    if (id == null) throw new IllegalStateException("SequentialStrategy requires a persisted ID");
    String prefix = (String) params.getOrDefault("prefix", "");
    return prefix + encodeId(id);
}
```

---

### 8. Update: `StrategyRegistry.java`

```java
@Component
@RequiredArgsConstructor
public class StrategyRegistry {

    private final StrategyParamValidator validator;
    private final Map<StrategyType, CodeGenerationStrategy> strategies;

    public StrategyRegistry(StrategyParamValidator validator) {
        this.validator = validator;
        this.strategies = new EnumMap<>(StrategyType.class);
        strategies.put(StrategyType.RANDOM_BASE62, new RandomBase62Strategy());
        strategies.put(StrategyType.HASH_TRUNCATE, new HashTruncateStrategy());
        strategies.put(StrategyType.SEQUENTIAL,    new SequentialStrategy());
    }

    /**
     * Validates params, then generates the short code.
     * Single entry point — callers never call validate() and generate() separately.
     */
    public String validateAndGenerate(StrategyType type, String url, Long id,
                                       Map<String, Object> rawParams) {
        CodeGenerationStrategy strategy = strategies.getOrDefault(
            type, strategies.get(StrategyType.RANDOM_BASE62));
        Map<String, Object> params = validator.validate(strategy.paramSchema(), rawParams);
        return strategy.generate(url, id, params);
    }

    public List<StrategyParamDefinition> getSchema(StrategyType type) {
        return strategies.get(type).paramSchema();
    }

    public Map<StrategyType, List<StrategyParamDefinition>> getAllSchemas() {
        Map<StrategyType, List<StrategyParamDefinition>> result = new EnumMap<>(StrategyType.class);
        strategies.forEach((type, s) -> result.put(type, s.paramSchema()));
        return result;
    }
}
```

> **Architecture review:** `validateAndGenerate()` is the single entry point. `LinkService` no
> longer needs to know anything about schema validation.

---

### 9. Update: `CreateLinkRequest.java`

> **Architecture review:** `Map<String, Object>` deserialization is type-ambiguous (Jackson
> produces `Integer` or `Double` depending on configuration). Accepting it as raw `Map<String,
> Object>` is fine at the DTO boundary because `StrategyParamValidator` normalizes all values to
> typed primitives before they reach any strategy.

```java
public record CreateLinkRequest(
    @NotBlank String originalUrl,
    String customAlias,
    String strategy,
    Map<String, Object> strategyParams,   // null = use all defaults
    Integer maxClicks,
    LocalDateTime expiresAt,
    String tags
) {}
```

---

### 10. Update: `LinkService.create()`

Validation is removed from `LinkService`. The only change is replacing the direct
`strategyRegistry.generate()` call with `strategyRegistry.validateAndGenerate()`:

```java
// Before:
String code = strategyRegistry.generate(strategyType, req.originalUrl(), partialEntity);

// After (non-SEQUENTIAL path):
String code = strategyRegistry.validateAndGenerate(
    strategyType, req.originalUrl(), null, req.strategyParams());

// After (SEQUENTIAL path — ID available after first save):
ShortLink saved = repo.saveAndFlush(partialEntity);
String code = strategyRegistry.validateAndGenerate(
    strategyType, req.originalUrl(), saved.getId(), req.strategyParams());
```

`LinkService` no longer imports anything from the `util/strategy` package except `StrategyType`
and `StrategyRegistry`.

---

### 11. New file: `StrategyController.java`

> **Security review:** `GET /api/strategies` was unauthenticated and exposed internal defaults and
> algorithm names. Since this project has no auth layer, the fix is to return only `name`, `type`,
> `required`, and `description` — omit `defaultValue`, `min`, and `max` from the public response.
> If auth is added later, the full schema can be gated behind it.

```
backend/src/main/java/com/avivly/urlshortener/controller/StrategyController.java
```

Public response DTO (strips implementation details):

```java
public record StrategyParamView(
    String name,
    String type,        // ParamType.name() lowercased
    boolean required,
    String description
) {
    public static StrategyParamView from(StrategyParamDefinition d) {
        return new StrategyParamView(
            d.name(), d.type().name().toLowerCase(), d.required(), d.description());
    }
}
```

Controller:

```java
@RestController
@RequestMapping("/api/strategies")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyRegistry strategyRegistry;

    @GetMapping
    public Map<String, List<StrategyParamView>> getAll() {
        Map<String, List<StrategyParamView>> result = new LinkedHashMap<>();
        strategyRegistry.getAllSchemas().forEach((type, defs) ->
            result.put(type.name(),
                defs.stream().map(StrategyParamView::from).toList()));
        return result;
    }
}
```

**Response shape (public, no sensitive internals):**

```json
{
  "RANDOM_BASE62": [
    { "name": "length",    "type": "integer", "required": false,
      "description": "Number of characters to generate" }
  ],
  "HASH_TRUNCATE": [
    { "name": "length",    "type": "integer", "required": false,
      "description": "Characters to take from hash output" },
    { "name": "algorithm", "type": "string",  "required": false,
      "description": "Hash algorithm: SHA-256 or SHA-512" }
  ],
  "SEQUENTIAL": [
    { "name": "prefix",    "type": "string",  "required": false,
      "description": "Prepended to the encoded ID (e.g. 's-'). Max 16 chars, alphanumeric/hyphen/underscore only" }
  ]
}
```

---

## Frontend Changes

### 12. Update: `api.js`

```js
export const getStrategies = () => api.get('/strategies');
```

---

### 13. Update: `LinkForm.jsx`

#### New state

> **UX review:** Params are now keyed per strategy so switching strategies and back preserves
> entered values. Added `schemaLoading` and `schemaError` states.

```js
const [strategy, setStrategy] = useState('RANDOM_BASE62');
const [strategySchemas, setStrategySchemas] = useState({});
const [allStrategyParams, setAllStrategyParams] = useState({});  // { [strategyName]: { ...params } }
const [schemaLoading, setSchemaLoading] = useState(true);
const [schemaError, setSchemaError] = useState('');
```

Current strategy's params:

```js
const strategyParams = allStrategyParams[strategy] ?? {};
const setStrategyParam = (name, value) =>
  setAllStrategyParams(prev => ({
    ...prev,
    [strategy]: { ...(prev[strategy] ?? {}), [name]: value }
  }));
```

#### Fetch schema once on mount

> **UX review:** The dropdown and submit button are disabled while loading. A visible error with a
> retry link is shown on failure — not a silent empty state.

```js
useEffect(() => {
  setSchemaLoading(true);
  setSchemaError('');
  getStrategies()
    .then(res => setStrategySchemas(res.data))
    .catch(() => setSchemaError('Could not load strategy options. Please refresh.'))
    .finally(() => setSchemaLoading(false));
}, []);
```

#### Edit mode handling

> **UX review:** When entering edit mode, strategy is locked (cannot be changed after creation).
> `strategyParams` is populated from `editTarget` if present.

```js
useEffect(() => {
  if (editTarget) {
    setOriginalUrl(editTarget.originalUrl);
    setCustomCode(editTarget.shortCode);
    setTags(editTarget.tags ?? '');
    setMaxClicks(editTarget.maxClicks ?? '');
    setExpiresAt(editTarget.expiresAt ? editTarget.expiresAt.slice(0, 16) : '');
    setStrategy(editTarget.strategy ?? 'RANDOM_BASE62');
    setAllStrategyParams(
      editTarget.strategyParams
        ? { [editTarget.strategy]: editTarget.strategyParams }
        : {}
    );
  } else {
    // reset all fields including strategy
    setStrategy('RANDOM_BASE62');
    setAllStrategyParams({});
    // ... other resets
  }
  setError('');
}, [editTarget]);
```

#### Strategy selector

```jsx
{schemaError && (
  <p className="text-red-500 text-sm">{schemaError}</p>
)}

<select
  value={strategy}
  onChange={e => setStrategy(e.target.value)}
  disabled={schemaLoading || !!editTarget}
  className="border rounded-lg px-3 py-2 text-sm ..."
>
  {schemaLoading
    ? <option>Loading strategies…</option>
    : Object.keys(strategySchemas).map(name => (
        <option key={name} value={name}>{name}</option>
      ))
  }
</select>

{editTarget && (
  <p className="text-xs text-gray-400">Strategy is fixed at creation and cannot be changed.</p>
)}
```

#### Dynamic param fields

> **UX review:** `id`/`htmlFor` pair added for accessibility. `aria-required` added for screen
> readers. Unknown `param.type` renders a visible fallback instead of silently dropping the field.

```jsx
{(strategySchemas[strategy] ?? []).map(param => {
  const inputId = `strategy-param-${param.name}`;
  return (
    <div key={param.name}>
      <label htmlFor={inputId} className="text-xs text-gray-500">
        {param.description}
        {param.required && (
          <span className="text-red-500 ml-1" aria-hidden="true">*</span>
        )}
      </label>

      {param.type === 'integer' && (
        <input
          id={inputId}
          type="number"
          placeholder={`default: ${param.defaultValue ?? 'none'}`}
          value={strategyParams[param.name] ?? ''}
          aria-required={param.required}
          onChange={e => setStrategyParam(
            param.name,
            e.target.value === '' ? undefined : Number(e.target.value)
          )}
          className="border rounded-lg px-3 py-2 text-sm w-full ..."
        />
      )}

      {param.type === 'string' && (
        <input
          id={inputId}
          type="text"
          placeholder={param.defaultValue !== '' ? `default: ${param.defaultValue}` : 'optional'}
          value={strategyParams[param.name] ?? ''}
          aria-required={param.required}
          onChange={e => setStrategyParam(
            param.name,
            e.target.value === '' ? undefined : e.target.value
          )}
          className="border rounded-lg px-3 py-2 text-sm w-full ..."
        />
      )}

      {param.type === 'boolean' && (
        <input
          id={inputId}
          type="checkbox"
          checked={strategyParams[param.name] ?? false}
          aria-required={param.required}
          onChange={e => setStrategyParam(param.name, e.target.checked)}
        />
      )}

      {!['integer', 'string', 'boolean'].includes(param.type) && (
        <p className="text-xs text-yellow-600">
          Unsupported param type: {param.type}
        </p>
      )}
    </div>
  );
})}
```

> **UX review:** `e.target.value || undefined` was replaced with
> `e.target.value === '' ? undefined : e.target.value` to avoid coercing valid falsy-ish strings
> (e.g. `"0"`) to `undefined`.

#### Submit button disabled while schema is loading

```jsx
<button
  type="submit"
  disabled={loading || schemaLoading}
  className="..."
>
  {loading ? 'Saving...' : editTarget ? 'Update' : 'Shorten'}
</button>
```

#### Updated payload on submit

```js
const cleanParams = Object.fromEntries(
  Object.entries(strategyParams).filter(([, v]) => v !== undefined)
);

const payload = {
  originalUrl,
  customAlias: customCode || undefined,
  strategy,
  strategyParams: Object.keys(cleanParams).length > 0 ? cleanParams : undefined,
  tags: tags.trim() || undefined,
  maxClicks: maxClicks !== '' ? Number(maxClicks) : undefined,
  expiresAt: expiresAt ? new Date(expiresAt).toISOString().slice(0, 19) : undefined,
};
```

---

## UI Behavior

### Form layout

```
[ Long URL                              ]
[ Custom code (optional)                ]
[ Strategy ▼ RANDOM_BASE62              ]  ← disabled on edit with note
  Number of characters to generate
  [ placeholder: default: 7             ]
[ Tags                                  ]
[ Max clicks ]  [ Expires at           ]
[ Shorten ]  ← disabled while schema loading
```

### Per-strategy fields

| Strategy | Fields |
|---|---|
| `RANDOM_BASE62` | `length` (integer, placeholder "default: 7") |
| `HASH_TRUNCATE` | `length` (integer) + `algorithm` (string, placeholder "default: SHA-256") |
| `SEQUENTIAL` | `prefix` (string, placeholder "optional") |

### Error and loading states

| State | UI |
|---|---|
| Schema fetch in-flight | Dropdown shows "Loading strategies…", submit disabled |
| Schema fetch failed | Red error message above dropdown, submit disabled |
| Invalid param on submit | Backend returns 400, existing error display shows message |

---

## Testing Plan

### Tests to add

**`StrategyParamValidatorTest`** (new unit test class)
- `unknownKey_returns400`
- `requiredParamMissing_returns400`
- `integerParam_invalidString_returns400`
- `integerParam_belowMin_returns400`
- `integerParam_aboveMax_returns400`
- `integerParam_atBounds_passes` (value == min and value == max)
- `algorithParam_invalidValue_returns400`
- `algorithParam_allowedValues_pass` — SHA-256, SHA-512
- `prefixParam_invalidChars_returns400` — e.g. `"../etc"`
- `prefixParam_tooLong_returns400` — 17 chars
- `nullParams_usesDefaults_passes`
- `emptyParams_usesDefaults_passes`

**Per-strategy unit tests** (update existing classes)
- Update every existing `generate()` call to the new signature `generate(url, id, params)`
- Add `generate_withEmptyParams_usesDefaults` for each strategy
- `RandomBase62Strategy`: `generate_customLength_returnsCorrectLength`
- `HashTruncateStrategy`: `generate_sha512_producesHash`, `generate_customLength_applied`
- `SequentialStrategy`: `generate_withPrefix_prependsCorrectly`, `generate_withNullId_throws`

**`StrategyController` integration test** (new)
- `GET_api_strategies_returns200_withAllStrategies`
- `GET_api_strategies_doesNotExposeDefaultValueOrMin` — assert response JSON has no `defaultValue`, `min`, `max` keys

**`LinkControllerIntegrationTest` additions**
- `createLink_withValidStrategyParams_returns201`
- `createLink_withUnknownParamKey_returns400`
- `createLink_withOutOfRangeLength_returns400`
- `createLink_withInvalidAlgorithm_returns400`
- `createLink_withNullStrategyParams_usesDefaults`
- `createLink_strategyParams_integerDeserializedCorrectly` — verifies Jackson round-trip

### Tests to update

| File | What to update |
|------|----------------|
| `RandomBase62StrategyTest` | Add `params` arg to all `generate()` calls; add empty/null-params variants |
| `HashTruncateStrategyTest` | Same; add algorithm-specific tests |
| `SequentialStrategyTest` | Same; add prefix tests |
| `LinkControllerIntegrationTest` | Add `strategyParams` to request builders to stay representative |

---

## File Change Summary

| File | Change |
|------|--------|
| `util/strategy/ParamType.java` | **New** — `STRING`, `INTEGER`, `BOOLEAN` enum |
| `util/strategy/StrategyParamDefinition.java` | **New** — typed record with `min`/`max` |
| `util/strategy/StrategyParamValidator.java` | **New** — all validation logic in one place |
| `util/strategy/CodeGenerationStrategy.java` | New signature: `generate(url, id, params)` + `paramSchema()` |
| `util/strategy/RandomBase62Strategy.java` | Implement schema + read `length` from params |
| `util/strategy/HashTruncateStrategy.java` | Implement schema + read `length`, `algorithm` from params |
| `util/strategy/SequentialStrategy.java` | Implement schema + read `prefix` from params |
| `util/strategy/StrategyRegistry.java` | `validateAndGenerate()` replaces `generate()`; add `getSchema()`, `getAllSchemas()` |
| `controller/StrategyController.java` | **New** — `GET /api/strategies`, returns `StrategyParamView` (no internals) |
| `dto/CreateLinkRequest.java` | Add `Map<String, Object> strategyParams` |
| `service/LinkService.java` | Replace `generate()` with `validateAndGenerate()`; remove param validation logic |
| `frontend/src/api.js` | Add `getStrategies()` |
| `frontend/src/components/LinkForm.jsx` | Strategy dropdown + dynamic param fields + loading/error states + edit-mode handling |

---

## Data Flow

```
User picks RANDOM_BASE62, sets length=10 → clicks Shorten
        ↓
POST /api/links
{ "strategy": "RANDOM_BASE62", "strategyParams": { "length": 10 } }
        ↓
LinkService.create()
  strategyRegistry.validateAndGenerate(RANDOM_BASE62, url, null, { "length": 10 })
        ↓
StrategyParamValidator.validate(schema, { "length": 10 })
  ✓ "length" is declared
  ✓ coerced to INTEGER: 10
  ✓ 4 ≤ 10 ≤ 20
  returns { "length": 10 }
        ↓
RandomBase62Strategy.generate(url, null, { "length": 10 })
  reads length = 10 → Base62.generate(10) → "aB3kR7mNpQx"
        ↓
ShortLink saved with shortCode = "aB3kR7mNpQx"
```

---

## Security Decisions Log

| Risk | Decision |
|---|---|
| Algorithm injection via `MessageDigest.getInstance(userValue)` | Hard allowlist in validator: `SHA-256`, `SHA-512` only |
| DoS via unbounded `length` (e.g. 2 billion chars) | `min`/`max` on `StrategyParamDefinition`; enforced in validator before strategy runs |
| Log/path injection via `prefix` | Validator enforces `[A-Za-z0-9_-]{0,16}` regex |
| Deserialization gadget via `Object defaultValue` | Replaced with `String defaultValue`; coerced to typed primitive in validator |
| Schema endpoint exposing internal defaults/bounds | `StrategyParamView` strips `defaultValue`, `min`, `max` from public response |
