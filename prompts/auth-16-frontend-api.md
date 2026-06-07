# Agent Prompt: Update frontend/src/api.js with Auth Functions (AUTH-16)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (React + Vite).
Working directory: project root. All backend tasks (AUTH-01 through AUTH-15) are done.
File to modify: `frontend/src/api.js`.

The file currently creates an axios instance pointing at `/api` and exports individual API functions
(`getLinks`, `createLink`, `updateLink`, `deleteLink`). No auth logic exists yet.

## Your Task
Add request/response interceptors and export four new auth-related functions.
Append everything **after** the existing axios instance creation, before any existing exports.

## Changes to `frontend/src/api.js`

### Add after the axios instance creation (keep all existing exports)

```js
// Attach token on every outgoing request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Redirect to /login on 401
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export const register = (data) => api.post('/auth/register', data);
export const login    = (data) => api.post('/auth/login', data);

export const logout = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('email');
  window.location.href = '/login';
};

// Decode userId from JWT sub claim without a library
export const currentUserId = () => {
  const token = localStorage.getItem('token');
  if (!token) return null;
  try {
    return Number(JSON.parse(atob(token.split('.')[1])).sub);
  } catch {
    return null;
  }
};
```

## Notes
- Do NOT remove or change any existing exports (`getLinks`, `createLink`, etc.)
- The 401 interceptor clears the token and hard-redirects, which handles expired tokens globally

## Acceptance Criteria
- `api.js` has the two interceptors
- `register`, `login`, `logout`, `currentUserId` are exported
- All pre-existing exports still work
