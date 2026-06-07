# Agent Prompt: Update App.jsx and LinksTable.jsx (AUTH-18)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (React + Vite + react-router-dom v6).
Working directory: project root. AUTH-16 (api.js) and AUTH-17 (LoginForm, RegisterForm) are done.
Files to modify:
- `frontend/src/App.jsx`
- `frontend/src/components/LinksTable.jsx`

Current `App.jsx` has: BrowserRouter, Routes with `/`, `/link-expired`, `/*` routes. No auth routes.
Current `LinksTable.jsx` props: `{ links, onEdit, onDelete, onViewStats, tagFilter, onTagFilter }`. Edit/Delete buttons are always visible.

## Your Task
Two file changes.

## Changes to `frontend/src/App.jsx`

### 1. Add imports
```js
import LoginForm from './components/LoginForm';
import RegisterForm from './components/RegisterForm';
import { logout, currentUserId } from './api';
```

### 2. Add Log out button in the header
Inside the `<header>` element, add a logout button shown only when a token exists:
```jsx
{localStorage.getItem('token') && (
  <button
    onClick={logout}
    className="ml-auto text-sm bg-white text-blue-600 px-3 py-1 rounded hover:bg-blue-50"
  >
    Log out
  </button>
)}
```

### 3. Pass `currentUserId` to `LinksTable`
```jsx
<LinksTable
  links={links}
  onEdit={setEditTarget}
  onDelete={handleDelete}
  onViewStats={setAnalyticsShortCode}
  tagFilter={tagFilter}
  onTagFilter={setTagFilter}
  currentUserId={currentUserId()}
/>
```

### 4. Add new routes (inside `<Routes>`)
```jsx
<Route path="/login"    element={<LoginForm />} />
<Route path="/register" element={<RegisterForm />} />
```

---

## Changes to `frontend/src/components/LinksTable.jsx`

### 1. Add `currentUserId` to props destructuring
```jsx
export default function LinksTable({ links, onEdit, onDelete, onViewStats,
                                     tagFilter, onTagFilter, currentUserId }) {
```

### 2. Gate Edit and Delete buttons on ownership
Find the row where Edit and Delete buttons are rendered. Replace unconditional rendering with:
```jsx
{link.ownerId === currentUserId && (
  <>
    <button onClick={() => onEdit(link)} className="text-xs text-yellow-600 hover:underline">
      Edit
    </button>
    <button onClick={() => onDelete(link.id)} className="text-xs text-red-600 hover:underline">
      Delete
    </button>
  </>
)}
```

Keep the Stats and QR buttons unconditional — they are always visible.

## Acceptance Criteria
- `/login` and `/register` routes render the new forms
- Log out button appears in header when `localStorage` has a token
- Edit/Delete buttons only appear for links where `link.ownerId === currentUserId`
- Stats and QR buttons are unaffected
