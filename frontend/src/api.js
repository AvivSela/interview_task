import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

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

export const currentUserId = () => {
  const token = localStorage.getItem('token');
  if (!token) return null;
  try {
    return Number(JSON.parse(atob(token.split('.')[1])).sub);
  } catch {
    return null;
  }
};

export const getLinks = () => api.get('/links');
export const createLink = (data) => api.post('/links', data);
export const updateLink = (id, data) => api.put(`/links/${id}`, data);
export const deleteLink = (id) => api.delete(`/links/${id}`);
export const getAnalytics = (shortCode) => api.get(`/links/${shortCode}/analytics`);
export const getStrategies = () => api.get('/strategies');
