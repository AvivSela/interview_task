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
