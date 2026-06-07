# Agent Prompt: Create LoginForm.jsx and RegisterForm.jsx (AUTH-17)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (React + Vite + Tailwind).
Working directory: project root. AUTH-16 (api.js auth exports) is done.
Existing components for style reference: `frontend/src/components/LinkForm.jsx`.
Components directory: `frontend/src/components/`.

## Your Task
Create two new form components used for authentication.

## Files to Create

### `frontend/src/components/LoginForm.jsx`

Requirements:
- Controlled form with `email` and `password` inputs
- On submit: call `login({ email, password })` from `api.js`
  - On success: store `token` and `email` in `localStorage`, navigate to `/` using `useNavigate`
  - On 401: show inline error "Invalid email or password" (do not navigate)
- Footer link: "Don't have an account? Register" → navigates to `/register`
- If already logged in (token in localStorage), redirect to `/` immediately

### `frontend/src/components/RegisterForm.jsx`

Requirements:
- Controlled form with `email`, `password`, and `confirmPassword` inputs
- Client-side validation: if `password !== confirmPassword`, show "Passwords do not match" without calling the API
- On submit: call `register({ email, password })` from `api.js`
  - On success: store `token` and `email` in `localStorage`, navigate to `/`
  - On 409: show inline error "Email is already taken"
- Footer link: "Already have an account? Log in" → navigates to `/login`

## Notes
- Match the Tailwind styling used in existing components (white card, rounded, shadow, blue primary)
- Use `react-router-dom`'s `useNavigate` for navigation — it's already a dependency
- Keep the components simple; no complex state management needed

## Acceptance Criteria
- Both files exist and render without errors
- Login with wrong password shows error without navigating
- Register with mismatched passwords shows error without calling the API
- Successful login/register stores token and redirects to `/`
