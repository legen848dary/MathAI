import { useState, useEffect } from 'react';
import { getAnalytics } from './api/adminApi';
import type { AnalyticsOverview } from './api/adminApi';

interface Props {
  onGoToOverview: () => void;
}

function statCard(label: string, value: string | number, sub?: string) {
  return (
    <div className="bg-slate-800 rounded-xl border border-slate-700 p-5">
      <p className="text-xs text-slate-500 uppercase tracking-wider mb-1">{label}</p>
      <p className="text-2xl font-bold">{value}</p>
      {sub && <p className="text-xs text-slate-500 mt-0.5">{sub}</p>}
    </div>
  );
}

function barRow(label: string, count: number, max: number, color: string) {
  const pct = max > 0 ? (count / max) * 100 : 0;
  return (
    <div className="flex items-center gap-2 text-sm">
      <span className="w-28 text-slate-400 truncate">{label}</span>
      <div className="flex-1 h-5 bg-slate-700 rounded-full overflow-hidden">
        <div className={`h-full rounded-full transition-all duration-500 ${color}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="w-8 text-right text-slate-300 tabular-nums">{count}</span>
    </div>
  );
}

export default function AdminAnalytics({ onGoToOverview }: Props) {
  const [data, setData] = useState<AnalyticsOverview | null>(null);
  const [error, setError] = useState('');

  useEffect(() => { loadData(); }, []);

  const loadData = async () => {
    try {
      setData(await getAnalytics());
    } catch {
      setError('Failed to load analytics');
    }
  };

  if (error) {
    return (
      <div className="min-h-screen bg-slate-900 flex items-center justify-center">
        <p className="text-red-400">{error}</p>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="min-h-screen bg-slate-900 flex items-center justify-center">
        <p className="text-slate-400">Loading analytics…</p>
      </div>
    );
  }

  const maxGrade = Math.max(...data.popularGrades.map(g => g.count), 1);
  const maxTopic = Math.max(...data.popularTopics.map(t => t.count), 1);

  return (
    <div className="min-h-screen bg-slate-900 text-white">
      <header className="bg-slate-800 border-b border-slate-700 px-6 py-4">
        <div className="max-w-3xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-blue-600 rounded-xl flex items-center justify-center text-white font-bold">
              M
            </div>
            <span className="font-bold">MathAI Admin</span>
          </div>
          <nav className="flex items-center gap-1">
            <button
              onClick={onGoToOverview}
              className="px-3 py-1.5 text-sm text-slate-400 hover:text-white hover:bg-slate-700 rounded-lg transition-colors"
            >
              Overview
            </button>
            <span className="px-3 py-1.5 text-sm font-medium bg-blue-600 rounded-lg">Analytics</span>
          </nav>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-6 py-8 space-y-6">
        <h1 className="text-xl font-semibold">Analytics</h1>

        {/* Usage volume + success */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {statCard('Requests Today', data.totalToday)}
          {statCard('All-Time', data.totalAllTime)}
          {statCard('Success Rate', `${data.successRate}%`)}
          {statCard('Failed Today', data.failedToday, data.failedToday > 0 ? 'review logs' : undefined)}
        </div>

        {/* Generation speed */}
        <section className="bg-slate-800 rounded-xl border border-slate-700 p-6">
          <h3 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-4">Generation Speed (last 7 days)</h3>
          <div className="grid grid-cols-3 gap-4">
            {statCard('Avg', `${data.avgTimeSec}s`)}
            {statCard('Median (p50)', `${data.p50Sec}s`)}
            {statCard('p95', `${data.p95Sec}s`)}
          </div>
        </section>

        {/* Popular grades */}
        <section className="bg-slate-800 rounded-xl border border-slate-700 p-6">
          <h3 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-4">Popular Grades</h3>
          <div className="space-y-2">
            {data.popularGrades.length === 0 && <p className="text-sm text-slate-500">No data yet</p>}
            {data.popularGrades.map(g => barRow(`Grade ${g.grade}`, g.count, maxGrade, 'bg-blue-500'))}
          </div>
        </section>

        {/* Popular topics */}
        <section className="bg-slate-800 rounded-xl border border-slate-700 p-6">
          <h3 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-4">Popular Topics</h3>
          <div className="space-y-2">
            {data.popularTopics.length === 0 && <p className="text-sm text-slate-500">No data yet</p>}
            {data.popularTopics.map(t => barRow(t.topic, t.count, maxTopic, 'bg-emerald-500'))}
          </div>
        </section>

        {/* Provider split */}
        <section className="bg-slate-800 rounded-xl border border-slate-700 p-6">
          <h3 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-4">Provider Split</h3>
          <div className="space-y-2">
            {data.providerSplit.length === 0 && <p className="text-sm text-slate-500">No data yet</p>}
            {data.providerSplit.map(p => (
              <div key={p.provider} className="flex items-center justify-between text-sm">
                <span className="text-slate-300 capitalize">{p.provider}</span>
                <span className="text-slate-400 tabular-nums">{p.count} requests</span>
              </div>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}
