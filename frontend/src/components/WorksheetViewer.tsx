import { useState, useRef, useCallback } from 'react';
import type { WorksheetResponse } from '../types/worksheet';

interface Props {
  worksheet: WorksheetResponse;
  onReset: () => void;
}

const difficultyBadge = (d: string) => {
  const map: Record<string, string> = {
    Easy: 'bg-green-100 text-green-700',
    Medium: 'bg-amber-100 text-amber-700',
    Hard: 'bg-red-100 text-red-700',
  };
  return map[d] ?? 'bg-slate-100 text-slate-600';
};

export default function WorksheetViewer({ worksheet, onReset }: Props) {
  const [printAnswers, setPrintAnswers] = useState(true);
  const printContainerRef = useRef<HTMLDivElement>(null);

  const printQuestionsOnly = useCallback(() => {
    setPrintAnswers(false);
    // Use requestAnimationFrame to let React re-render before printing
    requestAnimationFrame(() => {
      window.print();
    });
  }, []);

  const printWithAnswers = useCallback(() => {
    setPrintAnswers(true);
    requestAnimationFrame(() => {
      window.print();
    });
  }, []);

  return (
    <div className="space-y-6">
      {/* Print CSS — hide answers section when printAnswers is false */}
      <style>{`
        @media print {
          .print-hide-answers .answers-section { display: none !important; }
        }
      `}</style>

      {/* Toolbar */}
      <div className="no-print flex items-center justify-between bg-white dark:bg-slate-800 rounded-2xl shadow-sm border border-slate-100 dark:border-slate-700 px-6 py-4 flex-wrap gap-3">
        <button
          onClick={onReset}
          className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-400 font-medium transition-colors"
        >
          ← Back
        </button>
        <div className="flex gap-2 flex-wrap">
          <button
            onClick={printQuestionsOnly}
            className="bg-slate-700 hover:bg-slate-800 text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors flex items-center gap-2"
          >
            🖨 Print Worksheet
          </button>
          <button
            onClick={printWithAnswers}
            className="bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors flex items-center gap-2"
          >
            🖨 Print with Answers
          </button>
        </div>
      </div>

      <div
        ref={printContainerRef}
        className={printAnswers ? '' : 'print-hide-answers'}
      >
        {/* Worksheet — always white for print readability */}
        <div className="print-area bg-white dark:bg-slate-800 rounded-2xl shadow-lg border border-slate-100 dark:border-slate-700 overflow-hidden">

          {/* Header — keep blue regardless of theme */}
          <div className="bg-blue-700 px-8 py-6">
            <h1 className="text-2xl font-bold text-white">{worksheet.title}</h1>
            <div className="flex flex-wrap gap-2 mt-3">
              <span className="bg-blue-600 text-blue-100 text-xs font-medium px-3 py-1 rounded-full">
                {worksheet.grade}
              </span>
              <span className="bg-blue-600 text-blue-100 text-xs font-medium px-3 py-1 rounded-full">
                {worksheet.topic}
              </span>
              <span className={`text-xs font-medium px-3 py-1 rounded-full ${difficultyBadge(worksheet.difficulty)}`}>
                {worksheet.difficulty}
              </span>
            </div>
          </div>

          {/* Instructions */}
          <div className="mx-8 mt-6 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl px-5 py-4">
            <p className="text-xs font-semibold text-blue-700 dark:text-blue-400 uppercase tracking-wide mb-1">Instructions</p>
            <p className="text-sm text-slate-700 dark:text-slate-300">{worksheet.instructions}</p>
          </div>

          {/* Questions */}
          <div className="px-8 py-6 space-y-6">
            <h2 className="text-sm font-bold text-blue-700 dark:text-blue-400 uppercase tracking-wider">Questions</h2>

            {worksheet.questions.map(q => (
              <div key={q.number} className="flex gap-4 pb-6 border-b border-slate-100 dark:border-slate-700 last:border-0">
                {/* Number badge */}
                <div className="flex-shrink-0 w-8 h-8 rounded-full bg-blue-600 text-white text-sm font-bold flex items-center justify-center">
                  {q.number}
                </div>

                <div className="flex-1">
                  <p className="text-slate-800 dark:text-slate-100 font-medium leading-relaxed">{q.text}</p>

                  {/* SVG diagram — rendered when Gemini provides one */}
                  {q.diagram && (
                    <div className="mt-4 rounded-xl border border-slate-200 dark:border-slate-500 bg-white dark:bg-slate-800 p-3 overflow-x-auto">
                      <p className="text-[10px] font-semibold text-slate-400 dark:text-slate-400 uppercase tracking-wide mb-2">Diagram</p>
                      <div
                        className="
                          [&_svg]:max-w-full
                          [&_svg_text]:fill-slate-800          dark:[&_svg_text]:fill-slate-100
                          [&_svg_tspan]:fill-slate-800         dark:[&_svg_tspan]:fill-slate-100
                          [&_svg_line]:stroke-slate-700        dark:[&_svg_line]:stroke-slate-200
                          [&_svg_polyline]:stroke-slate-700    dark:[&_svg_polyline]:stroke-slate-200
                          [&_svg_polygon]:stroke-slate-700     dark:[&_svg_polygon]:stroke-slate-200
                          [&_svg_polygon]:fill-slate-200       dark:[&_svg_polygon]:fill-slate-600
                          [&_svg_path]:stroke-slate-700        dark:[&_svg_path]:stroke-slate-200
                          [&_svg_circle]:stroke-slate-700      dark:[&_svg_circle]:stroke-slate-200
                          [&_svg_rect]:stroke-slate-700        dark:[&_svg_rect]:stroke-slate-200
                          [&_svg_ellipse]:stroke-slate-700     dark:[&_svg_ellipse]:stroke-slate-200
                          [&_svg]:text-slate-800               dark:[&_svg]:text-slate-100
                        "
                        dangerouslySetInnerHTML={{ __html: q.diagram }}
                      />
                    </div>
                  )}

                  {q.hint && (
                    <p className="text-xs text-slate-400 dark:text-slate-500 italic mt-1.5">💡 Hint: {q.hint}</p>
                  )}
                  {/* Answer lines */}
                  <div className="mt-4 space-y-2">
                    {[0, 1, 2].map(i => (
                      <div key={i} className="h-px bg-slate-200 dark:bg-slate-600 w-full" />
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Answer Key */}
        <div className="answers-section bg-white dark:bg-slate-800 rounded-2xl shadow-lg border border-slate-100 dark:border-slate-700 overflow-hidden mt-6">
          <div className="bg-emerald-700 px-8 py-5">
            <h2 className="text-lg font-bold text-white">Answer Key</h2>
            <p className="text-emerald-200 text-sm">{worksheet.title}</p>
          </div>
          <div className="px-8 py-6 space-y-3">
            {worksheet.answerKey.map((answer, i) => (
              <div key={i} className="bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800 rounded-xl px-5 py-3">
                <p className="text-sm text-slate-700 dark:text-slate-300 whitespace-pre-wrap">{answer}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
