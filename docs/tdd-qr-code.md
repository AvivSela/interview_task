# TDD: QR Code Generation for Short Links

Related PRD: [prd-qr-code.md](./prd-qr-code.md)

## Overview

This document defines the test plan for the QR code popover feature. All changes are frontend-only — no backend tests are needed.

---

## Test Infrastructure Setup

The frontend currently has no test framework. Add the following before writing tests:

```bash
npm install -D vitest @vitest/ui jsdom @testing-library/react @testing-library/user-event @testing-library/jest-dom
```

`vite.config.js` — add test block:

```js
test: {
  environment: 'jsdom',
  globals: true,
  setupFiles: './src/test-setup.js',
}
```

`src/test-setup.js`:

```js
import '@testing-library/jest-dom';
```

---

## Mocks

### `qrcode` library

The `qrcode` library makes no network calls, but its async `toString` complicates rendering assertions. Mock it globally:

```js
// src/__mocks__/qrcode.js
export default {
  toString: vi.fn(() => Promise.resolve('<svg data-testid="mock-qr"></svg>')),
};
```

### `qrcode` — rejection mock (error state tests)

```js
QRCode.toString.mockRejectedValueOnce(new Error('QR generation failed'));
```

### `navigator.clipboard`

```js
beforeEach(() => {
  Object.assign(navigator, {
    clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
  });
});
```

### Fake timers (copy confirmation reset)

Use `vi.useFakeTimers()` / `vi.advanceTimersByTime(ms)` in tests that assert the "Copied!" → "Copy SVG" reset, then restore with `vi.useRealTimers()` in `afterEach`.

---

## Test Files

### `QrPopover.test.jsx`

**File under test:** `src/components/QrPopover.jsx`

#### Rendering

| # | Test | Expectation |
|---|---|---|
| 1 | Renders with a valid URL | Popover container is in the DOM |
| 2 | Calls `QRCode.toString` with the provided URL | Mock called with `url` and `{ type: 'svg' }` |
| 3 | Renders the SVG returned by `QRCode.toString` | SVG markup appears inside the popover |
| 4 | Renders a "Copy SVG" button | Button with accessible label is present |

#### Copy behavior

| # | Test | Expectation |
|---|---|---|
| 5 | Clicking "Copy SVG" calls `navigator.clipboard.writeText` with the SVG string | Clipboard mock called with the exact SVG value |
| 6 | Clicking "Copy SVG" shows a brief confirmation (e.g., button label changes to "Copied!") | Label updates after click |

#### Close behavior

| # | Test | Expectation |
|---|---|---|
| 7 | Pressing Escape calls `onClose` | `onClose` spy is invoked |
| 8 | Mousedown outside the popover calls `onClose` | `onClose` spy is invoked |
| 9 | Mousedown inside the popover does NOT call `onClose` | `onClose` spy is not invoked |
| 10 | `onClose` is not called on initial render | `onClose` spy call count is 0 |

#### Loading state

| # | Test | Expectation |
|---|---|---|
| 11 | Shows a loading indicator while `QRCode.toString` is pending | Spinner/skeleton is in the DOM before promise resolves |
| 12 | Loading indicator disappears after SVG resolves | Spinner is removed; SVG is present |

#### Error state

| # | Test | Expectation |
|---|---|---|
| 13 | Shows a human-readable error message when `QRCode.toString` rejects | Error text is visible; SVG and "Copy SVG" button are not rendered |

#### Copy behavior — confirmation reset

| # | Test | Expectation |
|---|---|---|
| 14 | "Copied!" label resets to "Copy SVG" after a timeout | After `vi.advanceTimersByTime(timeout)`, label is back to "Copy SVG" |

#### Accessibility — focus management

| # | Test | Expectation |
|---|---|---|
| 15 | Focus moves into the popover when it opens | `document.activeElement` is inside the popover after mount |
| 16 | Focus returns to the trigger button when the popover closes | `document.activeElement` is the "QR" button that opened the popover |

#### Close behavior

| # | Test | Expectation |
|---|---|---|
| 17 | Pressing Escape calls `onClose` | `onClose` spy is invoked |
| 18 | Mousedown outside the popover ref calls `onClose` | `onClose` spy is invoked |
| 19 | Mousedown inside the popover ref does NOT call `onClose` | `onClose` spy is not invoked |
| 20 | `onClose` is not called on initial render | `onClose` spy call count is 0 |

#### Cleanup

| # | Test | Expectation |
|---|---|---|
| 21 | Unmounting removes the `document` mousedown listener | `removeEventListener` called on unmount (spy on `document`) |
| 22 | Unmounting removes the `keydown` listener | `removeEventListener` called on unmount |

---

### `LinksTable.test.jsx`

**File under test:** `src/components/LinksTable.jsx`

#### QR button presence

| # | Test | Expectation |
|---|---|---|
| 23 | Each row in the table renders a "QR" button | Button count equals number of links |
| 24 | Renders "QR" button when the links list has a single item | Button is present |
| 25 | No "QR" button when `links` is empty | Zero buttons rendered |

#### Popover open/close

| # | Test | Expectation |
|---|---|---|
| 26 | Clicking "QR" on a row opens `QrPopover` for that row's short link URL | Popover mounts; URL prop matches `{origin}/api/r/{shortCode}` |
| 27 | `LinksTable` enforces single-popover rule: clicking "QR" on a second row unmounts the first and mounts a new one | Only one popover in the DOM at a time; `LinksTable` owns this via `openQr` state |
| 28 | `QrPopover` receives `onClose`; calling it unmounts the popover | Popover is removed from DOM after `onClose()` |

#### URL construction

| # | Test | Expectation |
|---|---|---|
| 29 | URL passed to `QrPopover` uses `window.location.origin` + `/api/r/` + `shortCode` | Exact string match |

---

## Test Data

```js
const mockLinks = [
  { id: 1, shortCode: 'abc123', originalUrl: 'https://example.com', tags: 'foo,bar', totalClicks: 5 },
  { id: 2, shortCode: 'xyz789', originalUrl: 'https://another.com', tags: '',        totalClicks: 0 },
];
```

---

## Running Tests

```bash
# run once
npx vitest run

# watch mode
npx vitest

# with UI
npx vitest --ui
```

---

## Coverage Targets

| Component | Target |
|---|---|
| `QrPopover` | 100% — it is a self-contained leaf component |
| `LinksTable` (QR-related paths only) | 100% of new branches |

No coverage requirement is added to pre-existing `LinksTable` logic.
