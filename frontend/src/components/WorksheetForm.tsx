import { useEffect, useState, useCallback } from 'react';
import type { Difficulty, WorksheetRequest } from '../types/worksheet';
import { fetchTopics } from '../api/worksheetApi';

interface Props {
  onGenerate: (req: WorksheetRequest) => void;
  onDownloadPdf: (req: WorksheetRequest) => void;
  loading: boolean;
  pdfLoading: boolean;
}

const GRADES = [6, 7, 8, 9, 10, 11, 12];
const DIFFICULTIES: Difficulty[] = ['Easy', 'Medium', 'Hard'];
const QUESTION_COUNTS = [5, 10, 15, 20];
const STORAGE_KEY = 'mathai-last-form';

interface SavedForm {
  grade: number;
  topic: string;
  difficulty: string;
  questionCount: number;
  context: string;
}

function getSavedForm(): SavedForm | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function saveForm(values: SavedForm) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(values));
  } catch { /* ignore quota errors */ }
}

export default function WorksheetForm({ onGenerate, onDownloadPdf, loading, pdfLoading }: Props) {
  const [grade, setGrade] = useState<number>(6);
  const [topic, setTopic] = useState<string>('');
  const [difficulty, setDifficulty] = useState<Difficulty>('Medium');
  const [questionCount, setQuestionCount] = useState<number>(10);
  const [context, setContext] = useState<string>('');
  const [topics, setTopics] = useState<string[]>([]);
  const [topicsLoading, setTopicsLoading] = useState(false);
  const [restorePending, setRestorePending] = useState(false);

  // Persist form state to localStorage whenever it changes (debounced by value).
  // We save on-demand via wrapSubmit, but also keep saved form available for restore detection.
  const hasSavedForm = getSavedForm() !== null;

  useEffect(() => {
    setTopicsLoading(true);
    fetchTopics(grade)
      .then(t => {
        setTopics(t);
        // Don't override topic during a restore operation
        if (!restorePending) {
          setTopic(t[0] ?? '');
        }
      })
      .finally(() => setTopicsLoading(false));
  }, [grade, restorePending]);

  // When topics arrive after a restore, set the saved topic
  useEffect(() => {
    if (restorePending && topics.length > 0) {
      const saved = getSavedForm();
      if (saved && topics.includes(saved.topic)) {
        setTopic(saved.topic);
      }
      setRestorePending(false);
    }
  }, [topics, restorePending]);

  const buildRequest = (): WorksheetRequest => ({ grade, topic, difficulty, questionCount, context: context.trim() || undefined });

  /** Save current form to localStorage, then invoke the given action. */
  const wrapSubmit = useCallback((action: (req: WorksheetRequest) => void) => {
    const req = buildRequest();
    saveForm({ grade, topic, difficulty, questionCount, context });
    action(req);
  }, [grade, topic, difficulty, questionCount, context]);

  const handleRestore = () => {
    const saved = getSavedForm();
    if (!saved) return;
    setRestorePending(true);
    setGrade(saved.grade);
    setDifficulty(saved.difficulty as Difficulty);
    setQuestionCount(saved.questionCount);
    setContext(saved.context || '');
    // grade change triggers topic fetch; the second useEffect above will set the topic
  };

  const anyLoading = loading || pdfLoading || topicsLoading;

  return (
    <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-lg border border-slate-100 dark:border-slate-700 p-8">
      <div className="flex items-center gap-3 mb-8">
        <div className="w-10 h-10 bg-blue-600 rounded-xl flex items-center justify-center">
          <span className="text-white text-lg">📐</span>
        </div>
        <div>
          <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Generate Worksheet</h2>
          <p className="text-sm text-slate-500 dark:text-slate-400">IB MYP / DP curriculum aligned</p>
        </div>
        {hasSavedForm && (
          <button
            onClick={handleRestore}
            disabled={anyLoading || restorePending}
            className="ml-auto text-xs font-medium text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 bg-blue-50 dark:bg-blue-900/30 px-3 py-1.5 rounded-lg transition-colors disabled:opacity-40"
            title="Fill the form with your last submitted values"
          >
            ↻ Restore last inputs
          </button>
        )}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
        {/* Grade */}
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Grade</label>
          <select
            value={grade}
            onChange={e => setGrade(Number(e.target.value))}
            disabled={anyLoading}
            className="w-full rounded-lg border border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-700 px-3 py-2.5 text-sm text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50"
          >
            {GRADES.map(g => (
              <option key={g} value={g}>Grade {g}{g >= 11 ? ' (IB DP)' : ' (IB MYP)'}</option>
            ))}
          </select>
        </div>

        {/* Topic */}
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Topic</label>
          <select
            value={topic}
            onChange={e => setTopic(e.target.value)}
            disabled={anyLoading || topics.length === 0}
            className="w-full rounded-lg border border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-700 px-3 py-2.5 text-sm text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50"
          >
            {topicsLoading
              ? <option>Loading topics…</option>
              : topics.map(t => <option key={t} value={t}>{t}</option>)
            }
          </select>
        </div>

        {/* Difficulty */}
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Difficulty</label>
          <div className="flex gap-2">
            {DIFFICULTIES.map(d => (
              <button
                key={d}
                type="button"
                onClick={() => setDifficulty(d)}
                disabled={anyLoading}
                className={`flex-1 py-2.5 rounded-lg text-sm font-medium transition-all disabled:opacity-50 ${
                  difficulty === d
                    ? d === 'Easy'
                      ? 'bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 border-2 border-green-400'
                      : d === 'Medium'
                      ? 'bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-400 border-2 border-amber-400'
                      : 'bg-red-100 dark:bg-red-900/40 text-red-700 dark:text-red-400 border-2 border-red-400'
                    : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 border-2 border-transparent hover:bg-slate-200 dark:hover:bg-slate-600'
                }`}
              >
                {d}
              </button>
            ))}
          </div>
        </div>

        {/* Question Count */}
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            Number of Questions
          </label>
          <div className="flex gap-2">
            {QUESTION_COUNTS.map(n => (
              <button
                key={n}
                type="button"
                onClick={() => setQuestionCount(n)}
                disabled={anyLoading}
                className={`flex-1 py-2.5 rounded-lg text-sm font-medium transition-all disabled:opacity-50 ${
                  questionCount === n
                    ? 'bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-400 border-2 border-blue-400'
                    : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 border-2 border-transparent hover:bg-slate-200 dark:hover:bg-slate-600'
                }`}
              >
                {n}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Optional context / focus keywords */}
      <div className="mt-5">
        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
          Focus Keywords&nbsp;
          <span className="text-xs font-normal text-slate-400 dark:text-slate-500">(optional)</span>
        </label>
        <textarea
          value={context}
          onChange={e => setContext(e.target.value)}
          disabled={anyLoading}
          rows={2}
          placeholder="e.g. real-world problems, Pythagoras theorem, right-angled triangles, word problems…"
          className="w-full rounded-lg border border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-700 px-3 py-2.5 text-sm text-slate-800 dark:text-slate-100 placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50 resize-none"
        />
        <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">
          Add keywords to guide the AI — specific concepts, real-world scenarios, or topics to emphasise.
        </p>
      </div>

      {/* Action Buttons */}
      <div className="flex gap-3 mt-8">
        <button
          onClick={() => wrapSubmit(onGenerate)}
          disabled={anyLoading || !topic}
          className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 dark:disabled:bg-blue-900/50 text-white font-semibold py-3 px-6 rounded-xl transition-colors flex items-center justify-center gap-2 text-sm"
        >
          {loading ? (
            <>
              <span className="animate-spin inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full" />
              Generating with AI…
            </>
          ) : (
            <>✨ Generate Worksheet</>
          )}
        </button>

        <button
          onClick={() => wrapSubmit(onDownloadPdf)}
          disabled={anyLoading || !topic}
          className="bg-slate-700 hover:bg-slate-800 dark:bg-slate-600 dark:hover:bg-slate-500 disabled:bg-slate-300 dark:disabled:bg-slate-700/50 text-white font-semibold py-3 px-5 rounded-xl transition-colors flex items-center justify-center gap-2 text-sm"
        >
          {pdfLoading ? (
            <span className="animate-spin inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full" />
          ) : (
            '⬇ PDF'
          )}
        </button>
      </div>
    </div>
  );
}
