import { useState } from 'react';
import AdminLogin from './AdminLogin';
import AdminTwoFactor from './AdminTwoFactor';
import AdminOverview from './AdminOverview';
import AdminSettings from './AdminSettings';
import AdminAnalytics from './AdminAnalytics';
import { useAdminAuth } from './hooks/useAdminAuth';
import type { LoginResponse } from './api/adminApi';

type Screen =
  | { name: 'login' }
  | { name: 'two-factor'; email: string; tempToken: string }
  | { name: 'overview' }
  | { name: 'settings' }
  | { name: 'analytics' };

export default function AdminApp() {
  const { isAuthenticated, setToken, logout } = useAdminAuth();
  const [screen, setScreen] = useState<Screen>(
    isAuthenticated ? { name: 'overview' } : { name: 'login' }
  );

  const handleLoginSuccess = (response: LoginResponse, email: string) => {
    if (response.requiresTwoFactor) {
      setScreen({ name: 'two-factor', email, tempToken: response.token });
    } else {
      setToken(response.token);
      setScreen({ name: 'overview' });
    }
  };

  const handleTwoFactorSuccess = (token: string) => {
    setToken(token);
    setScreen({ name: 'overview' });
  };

  const handleLogout = () => {
    logout();
    setScreen({ name: 'login' });
  };

  switch (screen.name) {
    case 'login':
      return <AdminLogin onLoginSuccess={handleLoginSuccess} />;

    case 'two-factor':
      return (
        <AdminTwoFactor
          email={screen.email}
          tempToken={screen.tempToken}
          onSuccess={handleTwoFactorSuccess}
          onBack={() => setScreen({ name: 'login' })}
        />
      );

    case 'overview':
      return (
        <AdminOverview
          onGoToSettings={() => setScreen({ name: 'settings' })}
          onGoToAnalytics={() => setScreen({ name: 'analytics' })}
          onLogout={handleLogout}
        />
      );

    case 'settings':
      return (
        <AdminSettings
          onGoToOverview={() => setScreen({ name: 'overview' })}
        />
      );

    case 'analytics':
      return (
        <AdminAnalytics
          onGoToOverview={() => setScreen({ name: 'overview' })}
        />
      );
  }
}
