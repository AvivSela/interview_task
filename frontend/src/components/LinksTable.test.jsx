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
