# Prompt 1 of 2 — QR Code Feature: Setup + Tests (RED)

You are working in the `frontend/` directory of a React + Vite + Tailwind project (React 18, Vite 5). There is currently no test framework. Your job is to install the test infrastructure, create a QR code mock, and write two test files. Do NOT implement any production components — the tests must end in a failing (RED) state.

---

## Step 1 — Install test dependencies

From `frontend/`:

```bash
npm install -D vitest @vitest/ui jsdom @testing-library/react @testing-library/user-event @testing-library/jest-dom
npm install qrcode
```

---

## Step 2 — Configure Vitest

Edit `frontend/vite.config.js`. Add a `test` key inside `defineConfig({...})`, alongside the existing `plugins` and `server` keys:

```js
test: {
  environment: 'jsdom',
  globals: true,
  setupFiles: './src/test-setup.js',
},
```

---

## Step 3 — Create test setup file

Create `frontend/src/test-setup.js`:

```js
import '@testing-library/jest-dom';
```

---

## Step 4 — Create QRCode mock

Create `frontend/src/__mocks__/qrcode.js`:

```js
export default {
  toString: vi.fn(() => Promise.resolve('<svg data-testid="mock-qr"></svg>')),
};
```

Vitest will automatically use this mock when any test imports `qrcode`.

---

## Step 5 — Write `QrPopover` tests

Create `frontend/src/components/QrPopover.test.jsx` with the 22 tests below.

`QrPopover` is a component that does not exist yet — imports will fail and all tests will fail. That is the correct RED state.

Props contract (for writing tests):
- `url` — string, the URL to encode as a QR code
- `onClose` — function, called when the popover should close
- `triggerRef` — React ref pointing to the button that opened the popover (used to return focus on close)

```jsx
import { render, screen, fireEvent, act, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createRef } from 'react';
import QRCode from 'qrcode';
import QrPopover from './QrPopover';

const TEST_URL = 'https://example.com/abc123';

function makeTrigger() {
  const btn = document.createElement('button');
  document.body.appendChild(btn);
  btn.focus();
  const ref = { current: btn };
  return ref;
}

beforeEach(() => {
  Object.assign(navigator, {
    clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
  });
  QRCode.toString.mockClear();
  QRCode.toString.mockImplementation(() =>
    Promise.resolve('<svg data-testid="mock-qr"></svg>')
  );
});

afterEach(() => {
  vi.useRealTimers();
  document.body.innerHTML = '';
});

// --- Rendering ---

test('1: renders popover container with a valid URL', async () => {
  render(<QrPopover url={TEST_URL} onClose={() => {}} triggerRef={createRef()} />);
  expect(document.body.querySelector('[data-testid="qr-popover"]')).toBeInTheDocument();
});

test('2: calls QRCode.toString with the provided URL and svg type', async () => {
  render(<QrPopover url={TEST_URL} onClose={() => {}} triggerRef={createRef()} />);
  await waitFor(() => expect(QRCode.toString).toHaveBeenCalledWith(TEST_URL, { type: 'svg' }));
});

test('3: renders the SVG returned by QRCode.toString', async () => {
  render(<QrPopover url={TEST_URL} onClose={() => {}} triggerRef={createRef()} />);
  await waitFor(() => expect(screen.getByTestId('mock-qr')).toBeInTheDocument());
});

test('4: renders a "Copy SVG" button', async () => {
  render(<QrPopover url={TEST_URL} onClose={() => {}} triggerRef={createRef()} />);
  await waitFor(() => expect(screen.getByRole('button', { name: /copy svg/i })).toBeInTheDocument());
});

// --- Copy behavior ---

test('5: clicking "Copy SVG" calls navigator.clipboard.writeText with the SVG string', async () => {
  render(<QrPopover url={TEST_URL} onClose={() => {}} triggerRef={createRef()} />);
  await waitFor(() => screen.getByRole('button', { name: /copy svg/i }));
  await userEvent.click(screen.getByRole('button', { name: /copy svg/i }));
  expect(navigator.clipboard.writeText).toHaveBeenCalledWith('<svg data-testid="mock-qr"></svg>');
});

test('6: clicking "Copy SVG" changes button label to "Copied!"', async () => {
  render(<QrPopover url={TEST_URL} onClose={() => {}} triggerRef={createRef()} />);
  await waitFor(() => screen.getByRole('button', { name: /copy svg/i }));
  await userEvent.click(screen.getByRole('button', { name: /copy svg/i }));
  expect(screen.getByRole('button', { name: /copied!/i })).toBeInTheDocument();
});

// --- Close behavior ---

test('7: pressing Escape calls onClose', async () => {
  const onClose = vi.fn();
  render(<QrPopover url={TEST_URL} onClose={onClose} triggerRef={createRef()} />);
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(onClose).toHaveBeenCalledTimes(1);
});

test('8: mousedown outside the popover calls onClose', async () => {
  const onClose = vi.fn();
  render(<QrPopover url={TEST_URL} onClose={onClose} triggerRef={createRef()} />);
  fireEvent.mouseDown(document.body);
  expect(onClose).toHaveBeenCalledTimes(1);
});

test('9: mousedown inside the popover does NOT call onClose', async () => {
  const onClose = vi.fn();
  render(<QrPopover url={TEST_URL} onClose={onClose} triggerRef={createRef()} />);
  const popover = document.body.querySelector('[data-testid="qr-popover"]');
  fireEvent.mouseDown(popover);
  expect(onClose).not.toHaveBeenCalled();
});

test('10: onClose is not called on initial render', () => {
  const onClose = vi.fn();
  render(<QrPopover url={TEST_URL} onClose={onClose} triggerRef={createRef()} />);
  expect(onClose).toHaveBeenCalledTimes(0);
});

// --- Loading state ---

test('11: shows a loading indicator while QRCode.toString is pending', () => {
  // Never resolve so the component stays in loading state
  QRCode.toString.mockImplementation(() => new Promise(() => {}));
  render(<QrPopover url={TEST_URL} onClose={() => {}} triggerRef={createRef()} />);
  expect(document.body.querySelector('[data-testid="qr-loading"]')).toBeInTheDocument();
});

test('12: loading indicator disappears and SVG appears after promise resolves', async () => {
  render(<QrPopover url={TEST_URL} onClose={() => {}} triggerRef={createRef()} />);
  await waitFor(() => expect(screen.getByTestId('mock-qr')).toBeInTheDocument());
  expect(document.body.querySelector('[data-testid="qr-loading"]')).not.toBeInTheDocument();
});

// --- Error state ---

test('13: shows error message when QRCode.toString rejects; hides SVG and copy button', async () => {
  QRCode.toString.mockRejectedValueOnce(new Error('QR generation failed'));
  render(<QrPopover url={TEST_URL} onClose={() => {}} triggerRef={createRef()} />);
  await waitFor(() => expect(screen.getByText(/failed to generate/i)).toBeInTheDocument());
  expect(document.body.querySelector('[data-testid="mock-qr"]')).not.toBeInTheDocument();
  expect(document.body.querySelector('[aria-label*="Copy"]')).not.toBeInTheDocument();
});

// --- Confirmation reset ---

test('14: "Copied!" label resets to "Copy SVG" after timeout', async () => {
  vi.useFakeTimers();
  render(<QrPopover url={TEST_URL} onClose={() => {}} triggerRef={createRef()} />);
  await act(async () => {
    await vi.runAllMicrotasksAsync();
  });
  await userEvent.click(screen.getByRole('button', { name: /copy svg/i }));
  expect(screen.getByRole('button', { name: /copied!/i })).toBeInTheDocument();
  act(() => { vi.advanceTimersByTime(2000); });
  expect(screen.getByRole('button', { name: /copy svg/i })).toBeInTheDocument();
});

// --- Accessibility: focus management ---

test('15: focus moves into the popover when it opens', async () => {
  const triggerRef = makeTrigger();
  render(<QrPopover url={TEST_URL} onClose={() => {}} triggerRef={triggerRef} />);
  const popover = document.body.querySelector('[data-testid="qr-popover"]');
  expect(popover.contains(document.activeElement)).toBe(true);
});

test('16: focus returns to the trigger button when onClose is called', async () => {
  const triggerRef = makeTrigger();
  const { unmount } = render(
    <QrPopover url={TEST_URL} onClose={() => {}} triggerRef={triggerRef} />
  );
  unmount();
  expect(document.activeElement).toBe(triggerRef.current);
});

// --- Cleanup ---

test('21: unmounting removes the mousedown listener', () => {
  const removeSpy = vi.spyOn(document, 'removeEventListener');
  const { unmount } = render(
    <QrPopover url={TEST_URL} onClose={() => {}} triggerRef={createRef()} />
  );
  unmount();
  expect(removeSpy).toHaveBeenCalledWith('mousedown', expect.any(Function));
});

test('22: unmounting removes the keydown listener', () => {
  const removeSpy = vi.spyOn(document, 'removeEventListener');
  const { unmount } = render(
    <QrPopover url={TEST_URL} onClose={() => {}} triggerRef={createRef()} />
  );
  unmount();
  expect(removeSpy).toHaveBeenCalledWith('keydown', expect.any(Function));
});
```

---

## Step 6 — Write `LinksTable` tests

Create `frontend/src/components/LinksTable.test.jsx`.

Mock `QrPopover` so these tests don't depend on QR rendering. The real `LinksTable` does not have a QR button yet — all QR-related tests will fail.

```jsx
import { render, screen, fireEvent } from '@testing-library/react';
import LinksTable from './LinksTable';

vi.mock('./QrPopover', () => ({
  default: ({ url, onClose }) => (
    <div data-testid="qr-popover" data-url={url}>
      <button onClick={onClose}>close</button>
    </div>
  ),
}));

const mockLinks = [
  { id: 1, shortCode: 'abc123', originalUrl: 'https://example.com', tags: 'foo,bar', totalClicks: 5 },
  { id: 2, shortCode: 'xyz789', originalUrl: 'https://another.com', tags: '', totalClicks: 0 },
];

const noop = () => {};
const defaultProps = {
  links: mockLinks,
  onEdit: noop,
  onDelete: noop,
  onViewStats: noop,
  tagFilter: null,
  onTagFilter: noop,
};

// --- QR button presence ---

test('23: each row renders a "QR" button', () => {
  render(<LinksTable {...defaultProps} />);
  expect(screen.getAllByRole('button', { name: /^qr$/i })).toHaveLength(2);
});

test('24: renders a "QR" button when links list has a single item', () => {
  render(<LinksTable {...defaultProps} links={[mockLinks[0]]} />);
  expect(screen.getAllByRole('button', { name: /^qr$/i })).toHaveLength(1);
});

test('25: no "QR" button when links is empty', () => {
  render(<LinksTable {...defaultProps} links={[]} />);
  expect(screen.queryByRole('button', { name: /^qr$/i })).not.toBeInTheDocument();
});

// --- Popover open/close ---

test('26: clicking "QR" on a row opens QrPopover with the correct URL', () => {
  render(<LinksTable {...defaultProps} />);
  fireEvent.click(screen.getAllByRole('button', { name: /^qr$/i })[0]);
  const popover = screen.getByTestId('qr-popover');
  expect(popover).toBeInTheDocument();
  expect(popover.dataset.url).toBe(`${window.location.origin}/api/r/abc123`);
});

test('27: clicking QR on a second row replaces the first popover (single-popover rule)', () => {
  render(<LinksTable {...defaultProps} />);
  const [btn1, btn2] = screen.getAllByRole('button', { name: /^qr$/i });
  fireEvent.click(btn1);
  fireEvent.click(btn2);
  expect(screen.getAllByTestId('qr-popover')).toHaveLength(1);
  expect(screen.getByTestId('qr-popover').dataset.url).toContain('xyz789');
});

test('28: calling onClose prop unmounts QrPopover', () => {
  render(<LinksTable {...defaultProps} />);
  fireEvent.click(screen.getAllByRole('button', { name: /^qr$/i })[0]);
  expect(screen.getByTestId('qr-popover')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /close/i }));
  expect(screen.queryByTestId('qr-popover')).not.toBeInTheDocument();
});

// --- URL construction ---

test('29: URL passed to QrPopover uses window.location.origin + /api/r/ + shortCode', () => {
  render(<LinksTable {...defaultProps} />);
  fireEvent.click(screen.getAllByRole('button', { name: /^qr$/i })[1]);
  expect(screen.getByTestId('qr-popover').dataset.url).toBe(
    `${window.location.origin}/api/r/xyz789`
  );
});
```

---

## Verify — confirm RED state

```bash
cd frontend && npx vitest run
```

Expected outcome:
- `QrPopover.test.jsx` — all tests fail (component does not exist)
- `LinksTable.test.jsx` — QR-related tests fail (no QR button in component yet)
- Exit code non-zero

This is the correct RED state. Do not fix any failures — stop here.
