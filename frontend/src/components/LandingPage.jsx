import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { createGuestLink, login, verifyToken, clearSession } from '../api';

export default function LandingPage() {
  const navigate = useNavigate();
  const [checking, setChecking] = useState(true);

  // Anon shortener state
  const [url, setUrl] = useState('');
  const [result, setResult] = useState(null);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState('');
  const [copied, setCopied] = useState(false);

  // Login state
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loginError, setLoginError] = useState('');
  const [loginLoading, setLoginLoading] = useState(false);

  const copyTimeoutRef = useRef(null);
  useEffect(() => () => clearTimeout(copyTimeoutRef.current), []);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) {
      setChecking(false);
      return;
    }
    verifyToken()
      .then(() => navigate('/dashboard'))
      .catch(() => {
        clearSession();
        setChecking(false);
      });
  }, [navigate]);

  if (checking) return null;

  const handleShorten = async (e) => {
    e.preventDefault();
    setCreateError('');
    setResult(null);
    setCreating(true);
    try {
      const res = await createGuestLink({ originalUrl: url });
      const shortUrl = `${window.location.origin}/${res.data.shortCode}`;
      setResult(shortUrl);
      setUrl('');
    } catch (err) {
      setCreateError(err.response?.data?.message || 'Something went wrong. Please try again.');
    } finally {
      setCreating(false);
    }
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(result)
      .then(() => setCopied(true))
      .catch(() => {});
    copyTimeoutRef.current = setTimeout(() => setCopied(false), 2000);
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoginError('');
    setLoginLoading(true);
    try {
      const res = await login({ email, password });
      localStorage.setItem('token', res.data.token);
      localStorage.setItem('email', email);
      navigate('/dashboard');
    } catch (err) {
      if (err.response?.status === 401) {
        setLoginError('Invalid email or password');
      } else {
        setLoginError('Something went wrong.');
      }
    } finally {
      setLoginLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 flex flex-col">
      <header className="px-6 py-4 flex items-center">
        <span className="text-2xl font-bold text-blue-700">Avivly</span>
        <span className="ml-2 text-gray-500 text-sm hidden sm:inline">URL Shortener</span>
      </header>

      <main className="flex-1 flex items-center justify-center px-4 py-10">
        <div className="w-full max-w-4xl grid grid-cols-1 md:grid-cols-2 gap-6">

          {/* Anon shortener */}
          <div className="bg-white rounded-2xl shadow-md p-8 flex flex-col">
            <h1 className="text-2xl font-bold text-gray-800 mb-1">Shorten a URL</h1>
            <p className="text-gray-500 text-sm mb-6">No account needed. Paste your link and go.</p>

            {result ? (
              <div className="flex flex-col gap-4">
                <div className="bg-blue-50 border border-blue-200 rounded-xl p-4">
                  <p className="text-xs text-blue-600 font-medium mb-1">Your short link</p>
                  <p className="text-blue-800 font-mono text-sm break-all">{result}</p>
                </div>
                <button
                  onClick={handleCopy}
                  className="bg-blue-600 text-white rounded-lg px-4 py-2 text-sm font-medium hover:bg-blue-700 transition-colors"
                >
                  {copied ? 'Copied!' : 'Copy link'}
                </button>
                <button
                  onClick={() => { setResult(null); setCopied(false); }}
                  className="text-sm text-blue-600 hover:underline text-center"
                >
                  Shorten another URL
                </button>
                <p className="text-xs text-gray-400 text-center mt-2">
                  Log in to track clicks, set expiry, and manage your links.
                </p>
              </div>
            ) : (
              <form onSubmit={handleShorten} className="flex flex-col gap-3 flex-1">
                <input
                  type="url"
                  placeholder="https://example.com/your-long-url"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  required
                  className="border rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
                />
                {createError && <p className="text-red-500 text-sm">{createError}</p>}
                <button
                  type="submit"
                  disabled={creating}
                  className="bg-blue-600 text-white rounded-lg px-4 py-2.5 text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors"
                >
                  {creating ? 'Shortening...' : 'Shorten'}
                </button>
              </form>
            )}
          </div>

          {/* Login panel */}
          <div className="bg-white rounded-2xl shadow-md p-8 flex flex-col">
            <h2 className="text-2xl font-bold text-gray-800 mb-1">Log in</h2>
            <p className="text-gray-500 text-sm mb-6">Access your dashboard, analytics, and link management.</p>

            <form onSubmit={handleLogin} className="flex flex-col gap-3">
              <input
                type="email"
                placeholder="Email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                className="border rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
              />
              <input
                type="password"
                placeholder="Password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                className="border rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
              />
              {loginError && <p className="text-red-500 text-sm">{loginError}</p>}
              <button
                type="submit"
                disabled={loginLoading}
                className="bg-blue-600 text-white rounded-lg px-4 py-2.5 text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors"
              >
                {loginLoading ? 'Logging in...' : 'Log in'}
              </button>
            </form>

            <p className="text-sm text-center text-gray-500 mt-5">
              Don&apos;t have an account?{' '}
              <button
                onClick={() => navigate('/register')}
                className="text-blue-600 hover:underline"
              >
                Create one
              </button>
            </p>
          </div>

        </div>
      </main>
    </div>
  );
}
