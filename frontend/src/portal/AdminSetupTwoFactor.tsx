import { useState } from 'react';
import { setupTwoFactor, verifyTwoFactorSetup } from './api/adminApi';

interface Props {
  onComplete: () => void;
  onCancel: () => void;
}

export default function AdminSetupTwoFactor({ onComplete, onCancel }: Props) {
  const [step, setStep] = useState<'loading' | 'show-qr' | 'verify'>('loading');
  const [qrCodeUri, setQrCodeUri] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Start setup on mount
  useState(() => {
    setupTwoFactor()
      .then((res: { qrCodeUri: string }) => {
        setQrCodeUri(res.qrCodeUri);
        setStep('show-qr');
      })
      .catch((err: Error) => {
        setError('Failed to setup 2FA: ' + (err.message || 'Unknown error'));
      });
  });

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await verifyTwoFactorSetup(code);
      onComplete();
    } catch (err: unknown) {
      if (err instanceof Error && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message || 'Verification failed');
      } else {
        setError(err instanceof Error ? err.message : 'Verification failed');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="bg-slate-800 rounded-2xl shadow-xl p-8 border border-slate-700">
          <div className="text-center mb-6">
            <h1 className="text-xl font-bold text-white">Set Up Two-Factor Auth</h1>
            <p className="text-sm text-slate-400 mt-1">
              {step === 'show-qr'
                ? 'Scan this QR code with your authenticator app'
                : 'Enter the code to verify setup'}
            </p>
          </div>

          {error && (
            <div className="bg-red-900/40 border border-red-700 rounded-lg px-4 py-3 text-sm text-red-300 mb-4">
              {error}
            </div>
          )}

          {step === 'loading' && (
            <div className="text-center py-8 text-slate-400">Generating QR code...</div>
          )}

          {step === 'show-qr' && (
            <div className="space-y-4">
              <div className="bg-white rounded-xl p-4 flex justify-center">
                <img src={qrCodeUri} alt="QR Code for 2FA" className="w-48 h-48" />
              </div>
              <button
                onClick={() => setStep('verify')}
                className="w-full py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition-colors"
              >
                I've Scanned the QR Code
              </button>
              <button
                onClick={onCancel}
                className="w-full py-2 text-sm text-slate-400 hover:text-white transition-colors"
              >
                Cancel
              </button>
            </div>
          )}

          {step === 'verify' && (
            <form onSubmit={handleVerify} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Verification Code</label>
                <input
                  type="text"
                  inputMode="numeric"
                  maxLength={6}
                  required
                  value={code}
                  onChange={e => setCode(e.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                  className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white text-center text-2xl tracking-[0.5em] placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono"
                  placeholder="000000"
                />
              </div>
              <button
                type="submit"
                disabled={loading || code.length !== 6}
                className="w-full py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white font-medium rounded-lg transition-colors"
              >
                {loading ? 'Verifying...' : 'Enable 2FA'}
              </button>
              <button
                type="button"
                onClick={() => setStep('show-qr')}
                className="w-full py-2 text-sm text-slate-400 hover:text-white transition-colors"
              >
                ← Show QR Code Again
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
