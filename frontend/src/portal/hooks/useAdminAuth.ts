import { useState, useEffect, useCallback } from 'react';
import { refreshToken } from '../api/adminApi';

interface AdminAuthState {
  token: string | null;
  isLoading: boolean;
  lastActivity: number;
}

export function useAdminAuth() {
  const [auth, setAuth] = useState<AdminAuthState>(() => {
    const token = localStorage.getItem('admin_token');
    return {
      token,
      isLoading: false,
      lastActivity: Date.now(),
    };
  });

  // Auto-refresh timer: refresh every 5 minutes if there's a token
  useEffect(() => {
    if (!auth.token) return;

    const interval = setInterval(async () => {
      try {
        await refreshToken();
        setAuth(prev => ({ ...prev, lastActivity: Date.now() }));
      } catch {
        logout();
      }
    }, 5 * 60 * 1000); // 5 min refresh interval

    return () => clearInterval(interval);
  }, [auth.token]);

  // Track user activity to prevent logout during use
  useEffect(() => {
    const onActivity = () => {
      setAuth(prev => ({ ...prev, lastActivity: Date.now() }));
    };
    window.addEventListener('click', onActivity);
    window.addEventListener('keydown', onActivity);
    return () => {
      window.removeEventListener('click', onActivity);
      window.removeEventListener('keydown', onActivity);
    };
  }, []);

  const setToken = useCallback((token: string) => {
    localStorage.setItem('admin_token', token);
    setAuth({ token, isLoading: false, lastActivity: Date.now() });
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('admin_token');
    setAuth({ token: null, isLoading: false, lastActivity: 0 });
  }, []);

  return {
    token: auth.token,
    isAuthenticated: !!auth.token,
    setToken,
    logout,
  };
}
