# PRD: QR Code Generation for Short Links

## Problem

Users who create short links often need to share them in physical or offline contexts — printed materials, presentations, event signage, business cards. Copying and pasting a URL is not possible in those settings. There is currently no way to get a scannable representation of a short link from the dashboard.

## Goal

Allow users to generate and use a QR code for any short link directly from the links table, with no extra steps and no file downloads required.

## Non-Goals

- QR codes for arbitrary URLs not managed by the system
- Server-side QR generation or storage
- Bulk QR export
- Customization of QR appearance (color, logo, size beyond basic)

## User Stories

**As a user**, I want to view the QR code for a short link so I can scan it with my phone to verify it works.

**As a user**, I want to copy or save the QR code so I can embed it in a document, slide, or printed material.

## Proposed Solution

Add a **QR** action button to each row in the links table. Clicking it opens a small popover anchored to the button, showing the QR code as an inline SVG. The popover closes when the user clicks outside it or presses Escape.

### UX Flow

1. User sees the links table with per-row actions: Stats / Edit / Delete
2. A new **QR** button is added to the action group
3. Clicking **QR** opens a popover containing:
   - The QR code SVG (encodes the full short link URL)
   - A "Copy SVG" button that copies the raw `<svg>` markup to the clipboard
4. Clicking outside the popover or pressing Escape closes it
5. Only one popover is open at a time

### URL Encoded

The QR code encodes the full redirect URL: `{origin}/api/r/{shortCode}` — the same URL already linked in the table.

## Technical Design

### Library

**`qrcode`** (npm) — generates an SVG string client-side via `QRCode.toString(url, { type: 'svg' })`. No binary files, no server round-trip, no additional backend changes needed.

### Frontend Changes

| File | Change |
|---|---|
| `frontend/package.json` | Add `qrcode` dependency |
| `frontend/src/components/QrPopover.jsx` | New component — renders popover with SVG and copy button |
| `frontend/src/components/LinksTable.jsx` | Add QR button to actions cell; manage `openQr` state (shortCode or null) |

### `QrPopover` Component

- Accepts `url` and `onClose` props
- Generates SVG in `useEffect` on mount via `QRCode.toString`
- Renders SVG via `dangerouslySetInnerHTML` (safe — content is locally generated, not from server)
- Positioned absolutely relative to the action button; `z-index` above table
- `document` mousedown listener closes it on outside click
- Keyboard: `Escape` closes it

### No Backend Changes

QR generation is entirely client-side. The backend already serves the redirect endpoint; no new API is needed.

## Acceptance Criteria

- [ ] A "QR" button appears for every row in the links table
- [ ] Clicking the button shows a popover with the correct QR code for that link's short URL
- [ ] Scanning the QR code with a phone navigates to the correct destination
- [ ] "Copy SVG" copies valid SVG markup to the clipboard
- [ ] Clicking outside the popover or pressing Escape closes it
- [ ] Opening a second QR popover closes the first
- [ ] No layout shift or overflow issues in the table

## Out of Scope / Future Ideas

- Download as PNG button
- Embed QR code in the link-creation confirmation screen
- QR code in share emails / notifications
