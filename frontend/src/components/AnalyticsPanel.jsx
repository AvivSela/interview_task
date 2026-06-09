import { useState, useEffect } from 'react';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from 'recharts';
import { getAnalytics } from '../api';

export default function AnalyticsPanel({ shortCode, onClose }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    setData(null);
    setError('');
    let cancelled = false;
    getAnalytics(shortCode)
      .then((res) => { if (!cancelled) setData(res.data); })
      .catch(() => { if (!cancelled) setError('Failed to load analytics.'); });
    return () => { cancelled = true; };
  }, [shortCode]);

  const geoEmpty =
    !data?.topCountries?.length && !data?.topCities?.length;

  const cityLabel = (entry) =>
    entry.city && entry.country ? `${entry.city}, ${entry.country}` : (entry.city ?? '');

  return (
    <div className="bg-white rounded-xl shadow p-6">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-lg font-semibold">
          Analytics — <span className="font-mono text-blue-600">{shortCode}</span>
        </h2>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">
          &times;
        </button>
      </div>

      {error && <p className="text-red-500 text-sm">{error}</p>}

      {data && (
        <div className="flex flex-col gap-6">
          <p className="text-sm text-gray-500">
            Total clicks: <span className="font-semibold text-gray-800">{data.totalClicks}</span>
          </p>

          {data.clicksOverTime?.length > 0 && (
            <>
              <h3 className="text-sm font-medium text-gray-600">Clicks over time</h3>
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={data.clicksOverTime}>
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Bar dataKey="count" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </>
          )}

          {data.topCountries?.length > 0 && (
            <>
              <h3 className="text-sm font-medium text-gray-600">Top countries</h3>
              <ResponsiveContainer width="100%" height={Math.max(120, data.topCountries.length * 32)}>
                <BarChart data={data.topCountries} layout="vertical">
                  <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
                  <YAxis type="category" dataKey="country" tick={{ fontSize: 11 }} width={90} />
                  <Tooltip />
                  <Bar dataKey="clicks" fill="#3b82f6" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </>
          )}

          {data.topCities?.length > 0 && (
            <>
              <h3 className="text-sm font-medium text-gray-600">Top cities</h3>
              <ResponsiveContainer width="100%" height={Math.max(120, data.topCities.length * 32)}>
                <BarChart
                  data={data.topCities.map((e) => ({ ...e, label: cityLabel(e) }))}
                  layout="vertical"
                >
                  <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
                  <YAxis type="category" dataKey="label" tick={{ fontSize: 11 }} width={130} />
                  <Tooltip />
                  <Bar dataKey="clicks" fill="#10b981" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </>
          )}

          {geoEmpty && (
            <p className="text-sm text-gray-400 italic">
              Geographic data not yet available for this link.
            </p>
          )}
        </div>
      )}

      {!data && !error && <p className="text-sm text-gray-400">Loading...</p>}
    </div>
  );
}
