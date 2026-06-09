import { useState, useEffect } from 'react';
import { createLink, updateLink, getStrategies } from '../api';

export default function LinkForm({ onCreated, editTarget, onUpdated, onCancel }) {
  const [originalUrl, setOriginalUrl]       = useState('');
  const [customCode, setCustomCode]         = useState('');
  const [tags, setTags]                     = useState('');
  const [maxClicks, setMaxClicks]           = useState('');
  const [expiresAt, setExpiresAt]           = useState('');
  const [error, setError]                   = useState('');
  const [loading, setLoading]               = useState(false);

  const [strategy, setStrategy]             = useState('RANDOM_BASE62');
  const [strategySchemas, setStrategySchemas] = useState({});
  const [allStrategyParams, setAllStrategyParams] = useState({});
  const [schemaLoading, setSchemaLoading]   = useState(true);
  const [schemaError, setSchemaError]       = useState('');

  const strategyParams = allStrategyParams[strategy] ?? {};
  const setStrategyParam = (name, value) =>
    setAllStrategyParams(prev => ({
      ...prev,
      [strategy]: { ...(prev[strategy] ?? {}), [name]: value }
    }));

  useEffect(() => {
    setSchemaLoading(true);
    setSchemaError('');
    getStrategies()
      .then(res => setStrategySchemas(res.data))
      .catch(() => setSchemaError('Could not load strategy options. Please refresh.'))
      .finally(() => setSchemaLoading(false));
  }, []);

  useEffect(() => {
    if (editTarget) {
      setOriginalUrl(editTarget.originalUrl);
      setCustomCode(editTarget.shortCode);
      setTags(editTarget.tags ?? '');
      setMaxClicks(editTarget.maxClicks ?? '');
      setExpiresAt(editTarget.expiresAt ? editTarget.expiresAt.slice(0, 16) : '');
      setStrategy(editTarget.strategy ?? 'RANDOM_BASE62');
      setAllStrategyParams(
        editTarget.strategyParams
          ? { [editTarget.strategy]: editTarget.strategyParams }
          : {}
      );
    } else {
      setOriginalUrl('');
      setCustomCode('');
      setTags('');
      setMaxClicks('');
      setExpiresAt('');
      setStrategy('RANDOM_BASE62');
      setAllStrategyParams({});
    }
    setError('');
  }, [editTarget]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const cleanParams = Object.fromEntries(
        Object.entries(strategyParams).filter(([, v]) => v !== undefined)
      );
      // Strip invisible Unicode chars (BOM, RTL/LTR marks, zero-width spaces) that
      // cause URI.create() to throw on the backend when copying from RTL sites.
      const sanitizedUrl = originalUrl.replace(/^[\s\u200B-\u200F\uFEFF]+|[\s\u200B-\u200F\uFEFF]+$/g, '');
      const payload = {
        originalUrl: sanitizedUrl,
        tags: tags.trim() || undefined,
        maxClicks: maxClicks !== '' ? Number(maxClicks) : undefined,
        expiresAt: expiresAt ? new Date(expiresAt).toISOString().slice(0, 19) : undefined,
        strategy,
        strategyParams: Object.keys(cleanParams).length > 0 ? cleanParams : undefined,
      };
      if (editTarget) {
        await updateLink(editTarget.id, { ...payload, shortCode: customCode });
        onUpdated();
      } else {
        const res = await createLink({ ...payload, customAlias: customCode || undefined });
        setOriginalUrl('');
        setCustomCode('');
        setTags('');
        setMaxClicks('');
        setExpiresAt('');
        setStrategy('RANDOM_BASE62');
        setAllStrategyParams({});
        onCreated(res.data);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Something went wrong.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-white rounded-xl shadow p-6">
      <h2 className="text-lg font-semibold mb-4">{editTarget ? 'Edit Link' : 'Create Short Link'}</h2>
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <input
          type="url"
          placeholder="https://example.com/long-url"
          value={originalUrl}
          onChange={(e) => setOriginalUrl(e.target.value)}
          required
          className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
        />
        <input
          type="text"
          placeholder="Custom code (optional)"
          value={customCode}
          onChange={(e) => setCustomCode(e.target.value)}
          className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
        />

        {schemaError && <p className="text-red-500 text-sm">{schemaError}</p>}

        <select
          value={strategy}
          onChange={(e) => setStrategy(e.target.value)}
          disabled={schemaLoading || !!editTarget}
          className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
        >
          {schemaLoading
            ? <option>Loading strategies…</option>
            : Object.keys(strategySchemas).map(name => (
                <option key={name} value={name}>{name}</option>
              ))
          }
        </select>

        {editTarget && (
          <p className="text-xs text-gray-400">Strategy is fixed at creation and cannot be changed.</p>
        )}

        {(strategySchemas[strategy] ?? []).length > 0 && (
          <fieldset className="border border-blue-200 rounded-lg px-3 pt-2 pb-3 bg-blue-50">
            <legend className="text-xs font-semibold text-blue-600 px-1">Strategy options</legend>
            <div className="flex flex-col gap-3 mt-1">
              {(strategySchemas[strategy] ?? []).map(param => {
                const inputId = `strategy-param-${param.name}`;
                return (
                  <div key={param.name}>
                    <label htmlFor={inputId} className="text-xs text-gray-500 block mb-1">
                      {param.description}
                      {param.required && <span className="text-red-500 ml-1" aria-hidden="true">*</span>}
                    </label>

                    {param.type === 'integer' && (
                      <input
                        id={inputId}
                        type="number"
                        placeholder={`default: ${param.defaultValue ?? 'none'}`}
                        value={strategyParams[param.name] ?? ''}
                        aria-required={param.required}
                        onChange={e => setStrategyParam(
                          param.name,
                          e.target.value === '' ? undefined : Number(e.target.value)
                        )}
                        className="border rounded-lg px-3 py-2 text-sm w-full focus:outline-none focus:ring-2 focus:ring-blue-400 bg-white"
                      />
                    )}

                    {param.type === 'string' && (
                      <input
                        id={inputId}
                        type="text"
                        placeholder={param.defaultValue !== '' ? `default: ${param.defaultValue}` : 'optional'}
                        value={strategyParams[param.name] ?? ''}
                        aria-required={param.required}
                        onChange={e => setStrategyParam(
                          param.name,
                          e.target.value === '' ? undefined : e.target.value
                        )}
                        className="border rounded-lg px-3 py-2 text-sm w-full focus:outline-none focus:ring-2 focus:ring-blue-400 bg-white"
                      />
                    )}

                    {param.type === 'boolean' && (
                      <input
                        id={inputId}
                        type="checkbox"
                        checked={strategyParams[param.name] ?? false}
                        aria-required={param.required}
                        onChange={e => setStrategyParam(param.name, e.target.checked)}
                      />
                    )}

                    {!['integer', 'string', 'boolean'].includes(param.type) && (
                      <p className="text-xs text-yellow-600">Unsupported param type: {param.type}</p>
                    )}
                  </div>
                );
              })}
            </div>
          </fieldset>
        )}

        <input
          type="text"
          placeholder="Tags (comma-separated, e.g. marketing,promo)"
          value={tags}
          onChange={(e) => setTags(e.target.value)}
          className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
        />
        <div className="flex gap-2">
          <input
            type="number"
            placeholder="Max clicks (optional)"
            value={maxClicks}
            min={1}
            onChange={(e) => setMaxClicks(e.target.value)}
            className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400 w-1/2"
          />
          <input
            type="datetime-local"
            placeholder="Expires at (optional)"
            value={expiresAt}
            onChange={(e) => setExpiresAt(e.target.value)}
            className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400 w-1/2"
          />
        </div>

        {error && <p className="text-red-500 text-sm">{error}</p>}

        <div className="flex gap-2">
          <button
            type="submit"
            disabled={loading || schemaLoading}
            className="bg-blue-600 text-white rounded-lg px-4 py-2 text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Saving...' : editTarget ? 'Update' : 'Shorten'}
          </button>
          {editTarget && (
            <button
              type="button"
              onClick={onCancel}
              className="border rounded-lg px-4 py-2 text-sm hover:bg-gray-50"
            >
              Cancel
            </button>
          )}
        </div>
      </form>
    </div>
  );
}
