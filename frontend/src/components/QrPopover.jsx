import { useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';

export default function QrPopover({ url, onClose, triggerRef }) {
  const popoverRef = useRef(null);
  const [svg, setSvg] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    QRCode.toString(url, { type: 'svg' })
      .then((result) => {
        setSvg(result);
        setLoading(false);
      })
      .catch(() => {
        setError(true);
        setLoading(false);
      });
  }, [url]);

  useEffect(() => {
    const firstFocusable = popoverRef.current?.querySelector(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );
    (firstFocusable || popoverRef.current)?.focus();

    return () => {
      triggerRef.current?.focus();
    };
  }, [triggerRef]);

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    const handleMouseDown = (e) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target)) {
        onClose();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    document.addEventListener('mousedown', handleMouseDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('mousedown', handleMouseDown);
    };
  }, [onClose]);

  const handleCopy = async () => {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(svg);
      } else {
        const ta = document.createElement('textarea');
        ta.value = svg;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
      }
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // copy failed silently
    }
  };

  return (
    <div data-testid="qr-popover" ref={popoverRef} tabIndex={-1}>
      {loading && <div data-testid="qr-loading" />}
      {error && <p>Failed to generate QR code.</p>}
      {!loading && !error && svg && (
        <>
          <div dangerouslySetInnerHTML={{ __html: svg }} />
          <button onClick={handleCopy}>
            {copied ? 'Copied!' : 'Copy SVG'}
          </button>
        </>
      )}
    </div>
  );
}
