# Agent Prompt: Write Frontend Auth Form Tests (AUTH-19)

## Project Context
You are adding JWT-based authentication to an analytics-driven URL shortener (React + Vite).
Working directory: project root. AUTH-17 (LoginForm, RegisterForm) and AUTH-18 (App, LinksTable) are done.
Test framework: Vitest + @testing-library/react. Test setup file: `frontend/src/test-setup.js`.
Existing test for reference: `frontend/src/components/LinksTable.test.jsx`.
Run tests with: `cd frontend && npx vitest run`

## Your Task
Create two new test files.

## Files to Create

### `frontend/src/components/LoginForm.test.jsx`

```jsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginForm from './LoginForm';
import * as api from '../api';

vi.mock('../api', () => ({
  login: vi.fn(),
}));

const renderForm = () =>
  render(
    <MemoryRouter>
      <LoginForm />
    </MemoryRouter>
  );

test('renders email and password inputs', () => {
  renderForm();
  expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
});

test('successful login stores token and navigates to /', async () => {
  api.login.mockResolvedValue({ data: { token: 'tok123', email: 'a@b.com' } });
  renderForm();

  fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'a@b.com' } });
  fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });
  fireEvent.click(screen.getByRole('button', { name: /log in/i }));

  await waitFor(() => {
    expect(localStorage.getItem('token')).toBe('tok123');
  });
});

test('401 response shows error message', async () => {
  api.login.mockRejectedValue({ response: { status: 401 } });
  renderForm();

  fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'a@b.com' } });
  fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'wrongpass' } });
  fireEvent.click(screen.getByRole('button', { name: /log in/i }));

  await waitFor(() => {
    expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument();
  });
});
```

### `frontend/src/components/RegisterForm.test.jsx`

```jsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import RegisterForm from './RegisterForm';
import * as api from '../api';

vi.mock('../api', () => ({
  register: vi.fn(),
}));

const renderForm = () =>
  render(
    <MemoryRouter>
      <RegisterForm />
    </MemoryRouter>
  );

test('renders email, password, and confirm-password inputs', () => {
  renderForm();
  expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
});

test('password mismatch shows error without calling API', async () => {
  renderForm();

  fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'x@y.com' } });
  fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'password123' } });
  fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'different' } });
  fireEvent.click(screen.getByRole('button', { name: /register/i }));

  expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
  expect(api.register).not.toHaveBeenCalled();
});

test('successful register stores token', async () => {
  api.register.mockResolvedValue({ data: { token: 'tok456', email: 'x@y.com' } });
  renderForm();

  fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'x@y.com' } });
  fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'password123' } });
  fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'password123' } });
  fireEvent.click(screen.getByRole('button', { name: /register/i }));

  await waitFor(() => {
    expect(localStorage.getItem('token')).toBe('tok456');
  });
});

test('409 response shows email-taken error', async () => {
  api.register.mockRejectedValue({ response: { status: 409 } });
  renderForm();

  fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'dup@y.com' } });
  fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'password123' } });
  fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'password123' } });
  fireEvent.click(screen.getByRole('button', { name: /register/i }));

  await waitFor(() => {
    expect(screen.getByText(/email is already taken/i)).toBeInTheDocument();
  });
});
```

## Notes
- Tests use `MemoryRouter` (not `BrowserRouter`) so navigation doesn't cause jsdom issues
- Adjust label text matchers if the actual label text in your components differs slightly
- Clear localStorage between tests if needed (`beforeEach(() => localStorage.clear())`)

## Acceptance Criteria
- `cd frontend && npx vitest run` — all tests pass (including pre-existing ones)
- 3 tests in `LoginForm.test.jsx`, 4 tests in `RegisterForm.test.jsx`
