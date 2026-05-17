import { useState, useEffect } from 'react';
import {
  getSettings,
  setAiProvider,
  getTwoFactorStatus,
} from './api/adminApi';
import type { SettingsResponse } from './api/adminApi';

interface Props {
  onGoToSettings: () => void;
  onGoToAnalytics: () => void;
  onLogout: () => void;
}

export default function AdminOverview({ onGoToSettings, onGoToAnalytics, onLogout }: Props) {
  const [settings, setSettings] = useState<SettingsResponse | null>(null);
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(false);
  const [selectedProvider, setSelectedProvider] = useState('');
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
      setSelectedProvider(s.currentProvider);
      setTwoFactorEnabled(tf.enabled);
    } catch (err: unknown) {
      setError('Failed to load settings');
    }
  };

  const handleSaveProvider = async () => {
    if (!settings || selectedProvider === settings.currentProvider) return;
    setSaving(true);
    setError('');
    setSuccess('');
    try {
      await setAiProvider(selectedProvider);
      setSettings(prev => prev ? { ...prev, currentProvider: selectedProvider } : null);
      setSuccess(`AI provider switched to ${selectedProvider}`);
    } catch (err: unknown) {
      setError('Failed to switch provider');
    } finally {
      setSaving(false);
    }
  };

  const isProviderDirty = settings && selectedProvider !== settings.currentProvider;

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
            <span className="font-bold">MathAI Admin</span>
          </div>
          <nav className="flex items-center gap-1">
            <span className="px-3 py-1.5 text-sm font-medium bg-blue-600 rounded-lg">Overview</span>
            <button
              onClick={onGoToAnalytics}
              className="px-3 py-1.5 text-sm text-slate-400 hover:text-white hover:bg-slate-700 rounded-lg transition-colors"
            >
              Analytics
            </button>
            <button
              onClick={onGoToSettings}
              className="px-3 py-1.5 text-sm text-slate-400 hover:text-white hover:bg-slate-700 rounded-lg transition-colors"
            >
              Settings
            </button>
            <button
              onClick={onLogout}
              className="px-3 py-1.5 text-sm text-slate-400 hover:text-white hover:bg-slate-700 rounded-lg transition-colors"
            >
              Sign Out
            </button>
          </nav>
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

        {/* Status cards */}
        <div className="grid grid-cols-2 gap-4">
          <div className="bg-slate-800 rounded-xl border border-slate-700 p-5">
            <p className="text-xs text-slate-500 uppercase tracking-wider mb-1">Active AI Provider</p>
            <p className="text-lg font-semibold capitalize">{settings.currentProvider}</p>
          </div>
          <div className="bg-slate-800 rounded-xl border border-slate-700 p-5">
            <p className="text-xs text-slate-500 uppercase tracking-wider mb-1">Two-Factor Auth</p>
            <p className="text-lg font-semibold">
              <span className={twoFactorEnabled ? 'text-green-400' : 'text-slate-400'}>
                {twoFactorEnabled ? 'Enabled' : 'Disabled'}
              </span>
            </p>
          </div>
        </div>

        {/* AI Provider Section */}
        <section className="bg-slate-800 rounded-xl border border-slate-700 p-6">
          <h2 className="font-semibold text-lg mb-1">AI Provider</h2>
          <p className="text-sm text-slate-400 mb-4">
            Select which AI service to use for worksheet generation. Click Save to apply.
          </p>

          <div className="flex items-center gap-3">
            <select
              value={selectedProvider}
              onChange={e => setSelectedProvider(e.target.value)}
              disabled={saving}
              className="flex-1 px-4 py-2.5 bg-slate-700 border border-slate-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50"
            >
              {settings.availableProviders.map((p: string) => (
                <option key={p} value={p}>
                  {p.charAt(0).toUpperCase() + p.slice(1)}
                </option>
              ))}
            </select>
            <button
              onClick={handleSaveProvider}
              disabled={!isProviderDirty || saving}
              className="px-4 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-40 text-white font-medium rounded-lg transition-colors whitespace-nowrap"
            >
              {saving ? 'Saving...' : 'Save'}
            </button>
          </div>
        </section>
      </main>
    </div>
  );
}
