# Tasks: QR Code Feature — Agent Execution Plan

Related TDD: [tdd-qr-code.md](./tdd-qr-code.md)

Each task is atomic, self-contained, and ends with a verification command.
Work in `frontend/` unless told otherwise. Execute in order — later tasks depend on earlier ones.

---

## Task 1 — Install test infrastructure

**Goal:** Add Vitest + Testing Library so the test runner works with zero test files.

**Steps:**

1. From `frontend/`, run:
   ```bash
   npm install -D vitest @vitest/ui jsdom @testing-library/react @testing-library/user-event @testing-library/jest-dom
   ```

2. Edit `frontend/vite.config.js` — add a `test` key inside `defineConfig`:
   ```js
   test: {
     environment: 'jsdom',
     globals: true,
     setupFiles: './src/test-setup.js',
   },
   ```

3. Create `frontend/src/test-setup.js`:
   ```js
   import '@testing-library/jest-dom';
   ```

**Verify:**
```bash
cd frontend && npx vitest run --passWithNoTests
```
Expected: exits 0, "no test files found" (or similar).

---

## Task 2 — Install `qrcode` production dependency

**Goal:** The `QrPopover` component needs the `qrcode` npm package at runtime.

**Steps:**
```bash
cd frontend && npm install qrcode
```

**Verify:**
```bash
node -e "require('./frontend/node_modules/qrcode')" && echo "ok"
```
Expected: prints `ok`.

---

## Task 3 — Create the `qrcode` Vitest mock

**Goal:** Prevent real SVG generation during tests; keep assertions deterministic.

**Steps:**

Create `frontend/src/__mocks__/qrcode.js`:
```js
export default {
  toString: vi.fn(() => Promise.resolve('<svg data-testid="mock-qr"></svg>')),
};
```

No test runner verification needed — the mock is picked up automatically by Vitest when a test imports `qrcode`.

---

## Task 4 — Write `QrPopover` tests (RED)

**Goal:** Capture the full contract of `QrPopover` as failing tests before any implementation exists.

**Steps:**

Create `frontend/src/components/QrPopover.test.jsx` implementing all 22 cases from `tdd-qr-code.md`:

- **Rendering** (tests 1–4): mount with a URL; assert popover container, QRCode.toString called with `{ type: 'svg' }`, SVG in DOM, "Copy SVG" button present.
- **Copy behavior** (tests 5–6): click "Copy SVG" → `navigator.clipboard.writeText` called with SVG string; button label changes to "Copied!".
- **Close behavior** (tests 7–10 / 17–20 — deduplicated): Escape calls `onClose`; mousedown outside calls `onClose`; mousedown inside does NOT; `onClose` not called on mount.
- **Loading state** (tests 11–12): spinner in DOM before promise resolves; spinner gone and SVG present after.
- **Error state** (test 13): when `QRCode.toString` rejects, error message visible; SVG and "Copy SVG" absent.
- **Confirmation reset** (test 14): with fake timers, "Copied!" resets to "Copy SVG" after timeout.
- **Accessibility** (tests 15–16): `document.activeElement` inside popover on mount; returns to trigger button on close.
- **Cleanup** (tests 21–22): unmount → `document.removeEventListener` called for both `mousedown` and `keydown`.

Use the mock data and mock patterns from `tdd-qr-code.md`.

**Verify:**
```bash
cd frontend && npx vitest run QrPopover
```
Expected: all 22 tests **fail** (component file does not exist yet). Zero passing is the correct RED state.

---

## Task 5 — Implement `QrPopover` (GREEN)

**Goal:** Make all 22 `QrPopover` tests pass.

**Steps:**

Create `frontend/src/components/QrPopover.jsx`. The component must:

- Accept props: `url` (string), `onClose` (function), `triggerRef` (ref to the button that opened the popover).
- On mount, call `QRCode.toString(url, { type: 'svg' })` and store the result in state.
- Show a loading indicator (spinner/skeleton) while the promise is pending.
- Render the resolved SVG string via `dangerouslySetInnerHTML` (or equivalent) once resolved.
- Show a human-readable error message if the promise rejects; hide the SVG and "Copy SVG" button in this state.
- Render a "Copy SVG" button that:
  - Calls `navigator.clipboard.writeText` with the SVG string.
  - Changes its label to "Copied!" for 2 000 ms (match whatever timeout the tests assert with `vi.advanceTimersByTime`), then resets.
- On mount, move focus inside the popover.
- On unmount, return focus to `triggerRef.current` (if provided).
- Add a `keydown` listener on `document` for `Escape` → calls `onClose`.
- Add a `mousedown` listener on `document` → if the click target is outside the popover ref, calls `onClose`.
- Remove both listeners on unmount.

**Verify:**
```bash
cd frontend && npx vitest run QrPopover
```
Expected: all 22 tests **pass**.

---

## Task 6 — Write `LinksTable` QR tests (RED)

**Goal:** Capture the 7 QR-related `LinksTable` behaviours as failing tests.

**Steps:**

Create `frontend/src/components/LinksTable.test.jsx` implementing all 7 cases from `tdd-qr-code.md`:

- **Button presence** (tests 23–25): N rows → N "QR" buttons; single-item list; empty list → 0 buttons.
- **Popover open/close** (tests 26–28): clicking "QR" mounts `QrPopover` with the correct URL prop; clicking a second row unmounts the first and mounts a new one; `onClose` callback unmounts the popover.
- **URL construction** (test 29): URL passed to `QrPopover` equals `window.location.origin + '/api/r/' + shortCode`.

Mock `QrPopover` at the module level so these tests don't exercise QR rendering:
```js
vi.mock('./QrPopover', () => ({
  default: ({ url, onClose }) => (
    <div data-testid="qr-popover" data-url={url}>
      <button onClick={onClose}>close</button>
    </div>
  ),
}));
```

Use the `mockLinks` test data from `tdd-qr-code.md`.

**Verify:**
```bash
cd frontend && npx vitest run LinksTable
```
Expected: QR-related tests **fail** (no QR button or popover in `LinksTable` yet). Pre-existing non-QR tests that you write for context should pass or be skipped.

---

## Task 7 — Update `LinksTable` to add the QR feature (GREEN)

**Goal:** Make all 7 `LinksTable` QR tests pass without breaking the component's existing behaviour.

**Steps:**

Edit `frontend/src/components/LinksTable.jsx`:

1. Import `useState`, `useRef`, and `QrPopover`.
2. Add state: `const [openQr, setOpenQr] = useState(null);` — holds `{ shortCode, triggerRef }` or `null`.
3. In the action buttons column of each row, add a "QR" button:
   ```jsx
   <button
     ref={/* per-row ref stored in a map or created inline with useCallback */}
     onClick={() => setOpenQr({ shortCode: link.shortCode, triggerRef: /* the button ref */ })}
     className="text-xs text-gray-500 hover:underline"
   >
     QR
   </button>
   ```
4. Below the table (or inside the row), render conditionally:
   ```jsx
   {openQr && (
     <QrPopover
       url={`${window.location.origin}/api/r/${openQr.shortCode}`}
       onClose={() => setOpenQr(null)}
       triggerRef={openQr.triggerRef}
     />
   )}
   ```
   Only one `QrPopover` is rendered at a time — switching rows replaces it.

**Verify:**
```bash
cd frontend && npx vitest run LinksTable
```
Expected: all 7 QR-related `LinksTable` tests **pass**.

---

## Task 8 — Full suite green check

**Goal:** Confirm nothing regressed and the combined suite is clean.

**Steps:**
```bash
cd frontend && npx vitest run
```

Expected: **all 29 tests pass**, 0 failures, 0 skipped.

If any test fails, fix the root cause before closing this task — do not skip or comment out tests.
