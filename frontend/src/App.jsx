import { useState, useEffect, useCallback } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import LinkForm from './components/LinkForm';
import LinksTable from './components/LinksTable';
import AnalyticsPanel from './components/AnalyticsPanel';
import LinkExpired from './components/LinkExpired';
import { getLinks, deleteLink } from './api';

export default function App() {
  const [links, setLinks] = useState([]);
  const [editTarget, setEditTarget] = useState(null);
  const [analyticsShortCode, setAnalyticsShortCode] = useState(null);
  const [tagFilter, setTagFilter] = useState(null);
  const [loadError, setLoadError] = useState('');

  const fetchLinks = useCallback(async () => {
    try {
      const res = await getLinks();
      setLinks(res.data);
      setLoadError('');
    } catch {
      setLoadError('Failed to load links.');
    }
  }, []);

  useEffect(() => {
    fetchLinks();
  }, [fetchLinks]);

  const handleDelete = async (id) => {
    try {
      await deleteLink(id);
      fetchLinks();
    } catch {
      alert('Failed to delete link.');
    }
  };

  const dashboard = (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-blue-600 text-white px-6 py-4 shadow">
        <h1 className="text-2xl font-bold">URL Shortener</h1>
      </header>

      <main className="max-w-6xl mx-auto px-4 py-6">
        {loadError && <p className="text-red-500 mb-4">{loadError}</p>}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
          <LinkForm
            onCreated={fetchLinks}
            editTarget={editTarget}
            onUpdated={() => { setEditTarget(null); fetchLinks(); }}
            onCancel={() => setEditTarget(null)}
          />
          <LinksTable
            links={links}
            onEdit={setEditTarget}
            onDelete={handleDelete}
            onViewStats={setAnalyticsShortCode}
            tagFilter={tagFilter}
            onTagFilter={setTagFilter}
          />
        </div>

        {analyticsShortCode && (
          <AnalyticsPanel
            shortCode={analyticsShortCode}
            onClose={() => setAnalyticsShortCode(null)}
          />
        )}
      </main>
    </div>
  );

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={dashboard} />
        <Route path="/link-expired" element={<LinkExpired />} />
      </Routes>
    </BrowserRouter>
  );
}
