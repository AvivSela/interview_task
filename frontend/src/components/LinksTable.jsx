import { useState, useRef } from 'react';
import QrPopover from './QrPopover';

function TagBadge({ tag, active, onClick }) {
  return (
    <button
      onClick={() => onClick(tag)}
      className={`inline-block text-xs px-2 py-0.5 rounded-full mr-1 ${
        active
          ? 'bg-blue-600 text-white'
          : 'bg-blue-100 text-blue-700 hover:bg-blue-200'
      }`}
    >
      {tag}
    </button>
  );
}

export default function LinksTable({ links, onEdit, onDelete, onViewStats, tagFilter, onTagFilter, currentUserId }) {
  const [openQr, setOpenQr] = useState(null);
  const qrRefs = useRef({});

  const parseTags = (raw) =>
    raw ? raw.split(',').map((t) => t.trim()).filter(Boolean) : [];

  const filtered = tagFilter
    ? links.filter((l) => parseTags(l.tags).includes(tagFilter))
    : links;

  return (
    <div className="bg-white rounded-xl shadow p-6 overflow-auto">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold">Your Links</h2>
        {tagFilter && (
          <button
            onClick={() => onTagFilter(null)}
            className="text-xs text-gray-500 hover:text-gray-700 border rounded px-2 py-0.5"
          >
            Clear filter: <span className="font-medium text-blue-600">{tagFilter}</span> ✕
          </button>
        )}
      </div>

      {filtered.length === 0 ? (
        <div className="flex items-center justify-center text-gray-400 text-sm py-4">
          {tagFilter ? `No links tagged "${tagFilter}".` : 'No links yet. Create one!'}
        </div>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-500 border-b">
              <th className="pb-2">Short Code</th>
              <th className="pb-2">Original URL</th>
              <th className="pb-2">Tags</th>
              <th className="pb-2">Clicks</th>
              <th className="pb-2"></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((link) => {
              if (!qrRefs.current[link.id]) {
                qrRefs.current[link.id] = { current: null };
              }
              const qrRef = qrRefs.current[link.id];
              return (
              <tr key={link.id} className="border-b last:border-0 hover:bg-gray-50">
                <td className="py-2 font-mono text-blue-600">
                  <a href={`/api/r/${link.shortCode}`} target="_blank" rel="noreferrer">
                    {link.shortCode}
                  </a>
                </td>
                <td className="py-2 max-w-[140px] truncate text-gray-600" title={link.originalUrl}>
                  {link.originalUrl}
                </td>
                <td className="py-2">
                  {parseTags(link.tags).map((tag) => (
                    <TagBadge
                      key={tag}
                      tag={tag}
                      active={tagFilter === tag}
                      onClick={onTagFilter}
                    />
                  ))}
                </td>
                <td className="py-2 text-gray-500">{link.totalClicks ?? 0}</td>
                <td className="py-2">
                  <div className="flex gap-2 justify-end">
                    <button
                      onClick={() => onViewStats(link.shortCode)}
                      className="text-xs text-indigo-600 hover:underline"
                    >
                      Stats
                    </button>
                    {link.ownerId === currentUserId && (
                      <>
                        <button
                          onClick={() => onEdit(link)}
                          className="text-xs text-yellow-600 hover:underline"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => onDelete(link.id)}
                          className="text-xs text-red-600 hover:underline"
                        >
                          Delete
                        </button>
                      </>
                    )}
                    <button
                      ref={(el) => { qrRef.current = el; }}
                      onClick={() => setOpenQr({ shortCode: link.shortCode, triggerRef: qrRef })}
                      className="text-xs text-gray-500 hover:underline"
                    >
                      QR
                    </button>
                  </div>
                </td>
              </tr>
              );
            })}
          </tbody>
        </table>
      )}
      {openQr && (
        <QrPopover
          url={`${window.location.origin}/api/r/${openQr.shortCode}`}
          onClose={() => setOpenQr(null)}
          triggerRef={openQr.triggerRef}
        />
      )}
    </div>
  );
}
