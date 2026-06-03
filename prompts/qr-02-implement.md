# Prompt 2 of 2 — QR Code Feature: Implementation (GREEN)

You are working in the `frontend/` directory of a React 18 + Vite 5 + Tailwind project.

The test infrastructure and all test files are already in place (written in Prompt 1). Your job is to implement the production code until all 29 tests pass. Do not modify any test files.

**Before starting**, run the suite to confirm the RED state:
```bash
cd frontend && npx vitest run
```
If tests are not failing, stop and investigate before continuing.

---

## Step 1 — Implement `QrPopover`

Create `frontend/src/components/QrPopover.jsx`.

### Contract (derived from the tests)

**Props:**
- `url` (string) — URL to encode
- `onClose` (function) — called when the popover should close
- `triggerRef` (React ref) — points to the button that opened the popover; restore focus here on unmount

**Behavior:**

1. **QR generation** — on mount, call `QRCode.toString(url, { type: 'svg' })`.
   - While pending: render `<div data-testid="qr-loading">` (spinner/skeleton).
   - On resolve: remove the loading indicator; render the SVG via `dangerouslySetInnerHTML`.
   - On reject: show a message matching `/failed to generate/i`; do not render the SVG or "Copy SVG" button.

2. **Popover root** — the outermost element must have `data-testid="qr-popover"`.

3. **Copy button** — label "Copy SVG".
   - On click: call `navigator.clipboard.writeText(svgString)`, then change label to "Copied!" for exactly **2000 ms**, then reset to "Copy SVG".

4. **Close on Escape** — attach a `keydown` listener to `document`; call `onClose` when `key === 'Escape'`.

5. **Close on outside click** — attach a `mousedown` listener to `document`; call `onClose` when `event.target` is not inside the popover element.

6. **Cleanup** — remove both listeners (`mousedown`, `keydown`) in the `useEffect` cleanup.

7. **Focus management** — on mount, move focus to the first focusable element inside the popover (or the popover root itself). On unmount, call `triggerRef.current?.focus()`.

### Verify
```bash
cd frontend && npx vitest run QrPopover
```
Expected: all 22 tests pass, 0 failures.

---

## Step 2 — Update `LinksTable`

Edit `frontend/src/components/LinksTable.jsx` to add the QR button and popover.

### Changes required

1. Add imports at the top:
   ```js
   import { useState, useRef } from 'react';
   import QrPopover from './QrPopover';
   ```

2. Inside the component, add state and a ref map:
   ```js
   const [openQr, setOpenQr] = useState(null);
   // openQr shape: { shortCode: string, triggerRef: React ref } | null
   const qrRefs = useRef({});
   ```

3. For each row, create a per-row ref and add a "QR" button in the actions column:
   ```jsx
   // inside the map, before the return
   if (!qrRefs.current[link.id]) {
     qrRefs.current[link.id] = { current: null };
   }
   const qrRef = qrRefs.current[link.id];
   ```

   Add inside the `<div className="flex gap-2 justify-end">` alongside the existing buttons:
   ```jsx
   <button
     ref={(el) => { qrRef.current = el; }}
     onClick={() => setOpenQr({ shortCode: link.shortCode, triggerRef: qrRef })}
     className="text-xs text-gray-500 hover:underline"
   >
     QR
   </button>
   ```

4. After the closing `</table>` tag (still inside the outer `<div>`), render the popover:
   ```jsx
   {openQr && (
     <QrPopover
       url={`${window.location.origin}/api/r/${openQr.shortCode}`}
       onClose={() => setOpenQr(null)}
       triggerRef={openQr.triggerRef}
     />
   )}
   ```

### Verify
```bash
cd frontend && npx vitest run LinksTable
```
Expected: all 7 QR-related tests pass, 0 failures.

---

## Step 3 — Full suite

```bash
cd frontend && npx vitest run
```

Expected: **29 tests pass, 0 failures, 0 skipped.**

If any test fails, fix the root cause. Do not skip, comment out, or modify tests.
