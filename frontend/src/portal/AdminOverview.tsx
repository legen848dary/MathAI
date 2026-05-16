import { useState, useEffect } from 'react';
import {
  getSettings,
  setAiProvider,
  getTwoFactorStatus,
  disableTwoFactor,
} from './api/adminApi';
import type { SettingsResponse } from './api/adminApi';

interface Props {
  onSetupTwoFactor: () => void;
  onLogout: () => void;
}

export default function AdminOverview({ onSetupTwoFactor, onLogout }: Props) {
  const [settings, setSettings] = useState<SettingsResponse | null>(null);
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [s, tf] = await Promise.all([getSettings(), getTwoFactorStatus()]);
      setSettings(s);
      setTwoFactorEnabled(tf.enabled);
    } catch (err: unknown) {
      setError('Failed to load settings');
    }
  };

  const handleProviderChange = async (provider: string) => {
    setSaving(true);
    setError('');
    setSuccess('');
    try {
      await setAiProvider(provider);
      setSettings((prev: SettingsResponse | null) => prev ? { ...prev, currentProvider: provider } : null);
      setSuccess(`AI provider switched to ${provider}`);
    } catch (err: unknown) {
      setError('Failed to switch provider');
    } finally {
      setSaving(false);
    }
  };

  const handleDisableTwoFactor = async () => {
    try {
      await disableTwoFactor();
      setTwoFactorEnabled(false);
      setSuccess('2FA disabled');
    } catch {
      setError('Failed to disable 2FA');
    }
  };

  if (!settings) {
    return (
      <div className="min-h-screen bg-slate-900 flex items-center justify-center">
        <p className="text-slate-400">Loading...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-900 text-white">
      <header className="bg-slate-800 border-b border-slate-700 px-6 py-4">
        <div className="max-w-2xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-blue-600 rounded-xl flex items-center justify-center text-white font-bold">
              M
            </div>
            <div>
              <span className="font-bold">MathAI Admin</span>
              <span className="ml-2 text-xs text-slate-400">Overview</span>
            </div>
          </div>
          <button
            onClick={onLogout}
            className="text-sm text-slate-400 hover:text-white transition-colors px-3 py-1.5 border border-slate-600 rounded-lg hover:border-slate-500"
          >
            Sign Out
          </button>
        </div>
      </header>

      <main className="max-w-2xl mx-auto px-6 py-8 space-y-6">
        {error && (
          <div className="bg-red-900/40 border border-red-700 rounded-lg px-4 py-3 text-sm text-red-300">
            {error}
          </div>
        )}
        {success && (
          <div className="bg-green-900/40 border border-green-700 rounded-lg px-4 py-3 text-sm text-green-300">
            {success}
          </div>
        )}

        {/* AI Provider Section */}
        <section className="bg-slate-800 rounded-xl border border-slate-700 p-6">
          <h2 className="font-semibold text-lg mb-1">AI Provider</h2>
          <p className="text-sm text-slate-400 mb-4">
            Select which AI service to use for worksheet generation. Changes take effect immediately.
          </p>

          <div className="flex items-center gap-3">
            <select
              value={settings.currentProvider}
              onChange={e => handleProviderChange(e.target.value)}
              disabled={saving}
              className="flex-1 px-4 py-2.5 bg-slate-700 border border-slate-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50"
            >
              {settings.availableProviders.map((p: string) => (
                <option key={p} value={p}>
                  {p.charAt(0).toUpperCase() + p.slice(1)}
                </option>
              ))}
            </select>
            {saving && <span className="text-sm text-slate-400 animate-pulse">Saving...</span>}
          </div>
        </section>

        {/* 2FA Section */}
        <section className="bg-slate-800 rounded-xl border border-slate-700 p-6">
          <h2 className="font-semibold text-lg mb-1">Two-Factor Authentication</h2>
          <p className="text-sm text-slate-400 mb-4">
            {twoFactorEnabled
              ? '2FA is currently enabled. Use your authenticator app to sign in.'
              : 'Add an extra layer of security to your admin account.'}
          </p>

          <div className="flex items-center gap-3">
            <span
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium ${
                twoFactorEnabled
                  ? 'bg-green-900/40 text-green-300 border border-green-700'
                  : 'bg-slate-700 text-slate-400 border border-slate-600'
              }`}
            >
              <span className={`w-2 h-2 rounded-full ${twoFactorEnabled ? 'bg-green-400' : 'bg-slate-500'}`} />
              {twoFactorEnabled ? 'Enabled' : 'Disabled'}
            </span>

            {twoFactorEnabled ? (
              <button
                onClick={handleDisableTwoFactor}
                className="text-sm text-red-400 hover:text-red-300 transition-colors"
              >
                Disable 2FA
              </button>
            ) : (
              <button
                onClick={onSetupTwoFactor}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors"
              >
                Set Up 2FA
              </button>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}
