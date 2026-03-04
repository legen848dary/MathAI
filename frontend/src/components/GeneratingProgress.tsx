import { useEffect, useState } from 'react';

const STAGES = [
  { emoji: '📚', label: 'Loading IB curriculum context…', duration: 2500 },
  { emoji: '🧠', label: 'Selecting questions for your grade & topic…', duration: 3500 },
  { emoji: '✏️',  label: 'Writing question text & hints…', duration: 4000 },
  { emoji: '📐', label: 'Generating diagrams & visuals…', duration: 3500 },
  { emoji: '🔑', label: 'Preparing answer key…', duration: 2500 },
  { emoji: '✅', label: 'Finalising your worksheet…', duration: 9999 }, // stays here until done
];

export default function GeneratingProgress() {
  const [stageIndex, setStageIndex] = useState(0);
  const [dots, setDots] = useState('');
  const [barWidth, setBarWidth] = useState(4);

  // Advance through stages
  useEffect(() => {
    if (stageIndex >= STAGES.length - 1) return;
    const t = setTimeout(() => {
      setStageIndex(s => s + 1);
    }, STAGES[stageIndex].duration);
    return () => clearTimeout(t);
  }, [stageIndex]);

  // Animated dots
  useEffect(() => {
    const t = setInterval(() => {
      setDots(d => (d.length >= 3 ? '' : d + '.'));
    }, 450);
    return () => clearInterval(t);
  }, []);

  // Smooth progress bar — grows over ~20s, capped at 95%
  useEffect(() => {
    const totalMs = STAGES.slice(0, -1).reduce((s, st) => s + st.duration, 0);
    const tickMs = 120;
    const increment = (95 / (totalMs / tickMs));
    const t = setInterval(() => {
      setBarWidth(w => Math.min(w + increment, 95));
    }, tickMs);
    return () => clearInterval(t);
  }, []);

  const completedStages = stageIndex;

  return (
    <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-lg border border-slate-100 dark:border-slate-700 p-8 space-y-7">

      {/* Title */}
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 bg-blue-600 rounded-xl flex items-center justify-center animate-pulse">
          <span className="text-white text-lg">✨</span>
        </div>
        <div>
          <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Generating your worksheet{dots}</h2>
          <p className="text-sm text-slate-500 dark:text-slate-400">Gemini AI is at work — this takes about 10–20 seconds</p>
        </div>
      </div>

      {/* Progress bar */}
      <div className="space-y-1.5">
        <div className="h-2.5 w-full bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden">
          <div
            className="h-full bg-gradient-to-r from-blue-500 to-indigo-500 rounded-full transition-all duration-300 ease-out"
            style={{ width: `${barWidth}%` }}
          />
        </div>
        <p className="text-right text-xs text-slate-400 dark:text-slate-500">{Math.round(barWidth)}%</p>
      </div>

      {/* Stage list */}
      <ul className="space-y-3">
        {STAGES.map((stage, i) => {
          const isDone = i < completedStages;
          const isActive = i === completedStages;
          const isPending = i > completedStages;
          return (
            <li
              key={i}
              className={`flex items-center gap-3 text-sm transition-all duration-300 ${
                isDone
                  ? 'text-slate-400 dark:text-slate-500'
                  : isActive
                  ? 'text-slate-800 dark:text-slate-100 font-medium'
                  : isPending
                  ? 'text-slate-300 dark:text-slate-600'
                  : ''
              }`}
            >
              {/* Status icon */}
              <span className="flex-shrink-0 w-5 h-5 flex items-center justify-center">
                {isDone ? (
                  <svg className="w-5 h-5 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                ) : isActive ? (
                  <span className="inline-block w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
                ) : (
                  <span className="inline-block w-2 h-2 bg-slate-200 dark:bg-slate-600 rounded-full mx-auto" />
                )}
              </span>

              {/* Emoji + label */}
              <span className={isActive ? '' : isDone ? 'line-through decoration-slate-300 dark:decoration-slate-600' : ''}>
                {stage.emoji} {stage.label}
              </span>
            </li>
          );
        })}
      </ul>

      {/* Fun fact footer */}
      <p className="text-xs text-center text-slate-400 dark:text-slate-500 italic border-t border-slate-100 dark:border-slate-700 pt-4">
        💡 Did you know? IB MYP Mathematics encourages real-world problem solving — your worksheet will reflect that!
      </p>
    </div>
  );
}

