import axios from 'axios';

const api = axios.create({
  baseURL: '/api/admin',
  headers: { 'Content-Type': 'application/json' },
});

// Intercept responses to auto-refresh token from X-Refreshed-Token header
api.interceptors.response.use(response => {
  const refreshed = response.headers['x-refreshed-token'];
  if (refreshed) {
    localStorage.setItem('admin_token', refreshed);
  }
  return response;
});

// Attach Bearer token to every request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('admin_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export interface LoginResponse {
  token: string;
  requiresTwoFactor: boolean;
  message: string;
}

export interface SettingsResponse {
  settings: Record<string, string>;
  currentProvider: string;
  availableProviders: string[];
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/auth/login', { email, password });
  if (!data.requiresTwoFactor) {
    localStorage.setItem('admin_token', data.token);
  }
  return data;
}

export async function verifyTwoFactor(
  email: string,
  code: string,
  tempToken: string
): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/auth/2fa/verify', {
    email,
    code,
    temp_token: tempToken,
  });
  localStorage.setItem('admin_token', data.token);
  return data;
}

export async function refreshToken(): Promise<string> {
  const { data } = await api.post<{ token: string }>('/auth/refresh');
  localStorage.setItem('admin_token', data.token);
  return data.token;
}

export async function getTwoFactorStatus(): Promise<{ enabled: boolean }> {
  const { data } = await api.get('/auth/2fa/status');
  return data;
}

export async function setupTwoFactor(): Promise<{ qrCodeUri: string }> {
  const { data } = await api.post('/auth/2fa/setup');
  return data;
}

export async function verifyTwoFactorSetup(code: string): Promise<void> {
  await api.post('/auth/2fa/verify-setup', { code });
}

export async function disableTwoFactor(): Promise<void> {
  await api.post('/auth/2fa/disable');
}

export async function getSettings(): Promise<SettingsResponse> {
  const { data } = await api.get('/settings');
  return data;
}

export async function setAiProvider(provider: string): Promise<void> {
  await api.put('/settings/ai-provider', { provider });
}
