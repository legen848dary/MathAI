import { useState, useEffect } from 'react';
import { getTwoFactorStatus, disableTwoFactor, setupTwoFactor, verifyTwoFactorSetup } from './api/adminApi';

interface Props {
  onGoToOverview: () => void;
}

type ModalState = { show: false } | { show: true; action: 'disable-confirm' };

export default function AdminSettings({ onGoToOverview }: Props) {
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(true);

  // 2FA setup flow
  const [setupStep, setSetupStep] = useState<'none' | 'show-qr' | 'verify'>('none');
  const [qrCodeUri, setQrCodeUri] = useState('');
  const [verifyCode, setVerifyCode] = useState('');
  const [setupError, setSetupError] = useState('');

  // Modal state
  const [modal, setModal] = useState<ModalState>({ show: false });

  useEffect(() => {
    loadStatus();
  }, []);

  const loadStatus = async () => {
    try {
      const tf = await getTwoFactorStatus();
      setTwoFactorEnabled(tf.enabled);
    } catch {
      setError('Failed to load 2FA status');
    } finally {
      setLoading(false);
    }
  };

  const handleBeginSetup = async () => {
    setSetupError('');
    try {
      const res = await setupTwoFactor();
      setQrCodeUri(res.qrCodeUri);
      setSetupStep('show-qr');
    } catch (err: unknown) {
      setSetupError(err instanceof Error ? err.message : 'Failed to initiate 2FA setup');
    }
  };

  const handleVerifySetup = async (e: React.FormEvent) => {
    e.preventDefault();
    setSetupError('');
    try {
      await verifyTwoFactorSetup(verifyCode);
      setTwoFactorEnabled(true);
      setSetupStep('none');
      setSuccess('2FA enabled successfully');
    } catch (err: unknown) {
      const msg = err instanceof Error && 'response' in err
        ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
        : 'Verification failed';
      setSetupError(msg || 'Verification failed');
    }
  };

  const handleDisableTwoFactor = async () => {
    setModal({ show: false });
    setError('');
    setSuccess('');
    try {
      await disableTwoFactor();
      setTwoFactorEnabled(false);
      setSuccess('2FA disabled');
    } catch {
      setError('Failed to disable 2FA');
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 text-white">
      {/* Modal overlay */}
      {modal.show && modal.action === 'disable-confirm' && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="bg-slate-800 border border-slate-600 rounded-2xl p-6 w-full max-w-sm mx-4 shadow-2xl">
            <h3 className="text-lg font-semibold mb-2">Disable Two-Factor Auth?</h3>
            <p className="text-sm text-slate-400 mb-6">
              This removes the extra security layer. Anyone with your password will be able to sign in.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setModal({ show: false })}
                className="px-4 py-2 text-sm border border-slate-600 rounded-lg hover:bg-slate-700 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleDisableTwoFactor}
                className="px-4 py-2 text-sm bg-red-600 hover:bg-red-700 text-white font-medium rounded-lg transition-colors"
              >
                Yes, Disable 2FA
              </button>
            </div>
          </div>
        </div>
      )}

      <header className="bg-slate-800 border-b border-slate-700 px-6 py-4">
        <div className="max-w-2xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-blue-600 rounded-xl flex items-center justify-center text-white font-bold">
              M
            </div>
            <div>
              <span className="font-bold">MathAI Admin</span>
              <span className="ml-2 text-xs text-slate-400">Settings</span>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <button
              onClick={onGoToOverview}
              className="text-sm text-slate-400 hover:text-white transition-colors"
            >
              ← Overview
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-2xl mx-auto px-6 py-8 space-y-6">
        {error && (
          <div className="bg-red-900/40 border border-red-700 rounded-lg px-4 py-3 text-sm text-red-300">{error}</div>
        )}
        {success && (
          <div className="bg-green-900/40 border border-green-700 rounded-lg px-4 py-3 text-sm text-green-300">{success}</div>
        )}

        {loading ? (
          <p className="text-slate-400">Loading...</p>
        ) : (
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
                  onClick={() => setModal({ show: true, action: 'disable-confirm' })}
                  className="text-sm text-red-400 hover:text-red-300 transition-colors"
                >
                  Disable 2FA
                </button>
              ) : (
                <button
                  onClick={handleBeginSetup}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors"
                >
                  Set Up 2FA
                </button>
              )}
            </div>
          </section>
        )}

        {/* 2FA Setup flow */}
        {setupStep !== 'none' && (
          <section className="bg-slate-800 rounded-xl border border-slate-700 p-6">
            <h2 className="font-semibold text-lg mb-1">Set Up Two-Factor Auth</h2>

            {setupError && (
              <div className="bg-red-900/40 border border-red-700 rounded-lg px-4 py-3 text-sm text-red-300 mb-4">{setupError}</div>
            )}

            {setupStep === 'show-qr' && (
              <div className="space-y-4">
                <p className="text-sm text-slate-400">Scan this QR code with your authenticator app:</p>
                <div className="bg-white rounded-xl p-4 flex justify-center">
                  <img src={qrCodeUri} alt="QR Code for 2FA" className="w-48 h-48" />
                </div>
                <div className="flex gap-3">
                  <button
                    onClick={() => setSetupStep('verify')}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors"
                  >
                    I've Scanned the QR Code
                  </button>
                  <button
                    onClick={() => setSetupStep('none')}
                    className="px-4 py-2 text-sm border border-slate-600 rounded-lg hover:bg-slate-700 transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}

            {setupStep === 'verify' && (
              <form onSubmit={handleVerifySetup} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-1">Verification Code</label>
                  <input
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    required
                    value={verifyCode}
                    onChange={e => setVerifyCode(e.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                    className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white text-center text-2xl tracking-[0.5em] placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono"
                    placeholder="000000"
                  />
                </div>
                <div className="flex gap-3">
                  <button
                    type="submit"
                    disabled={verifyCode.length !== 6}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white text-sm font-medium rounded-lg transition-colors"
                  >
                    Enable 2FA
                  </button>
                  <button
                    type="button"
                    onClick={() => setSetupStep('show-qr')}
                    className="px-4 py-2 text-sm border border-slate-600 rounded-lg hover:bg-slate-700 transition-colors"
                  >
                    ← Show QR Code Again
                  </button>
                </div>
              </form>
            )}
          </section>
        )}
      </main>
    </div>
  );
}
