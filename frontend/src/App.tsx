import { useState } from 'react';
import WorksheetForm from './components/WorksheetForm';
import WorksheetViewer from './components/WorksheetViewer';
import { generateWorksheet, downloadPdf } from './api/worksheetApi';
import type { WorksheetRequest, WorksheetResponse } from './types/worksheet';

export default function App() {
  const [worksheet, setWorksheet] = useState<WorksheetResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [pdfLoading, setPdfLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleGenerate = async (req: WorksheetRequest) => {
    setError(null);
    setLoading(true);
    try {
      const result = await generateWorksheet(req);
      setWorksheet(result);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Failed to generate worksheet. Please try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleDownloadPdf = async (req: WorksheetRequest) => {
    setError(null);
    setPdfLoading(true);
    try {
      await downloadPdf(req);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Failed to generate PDF. Please try again.';
      setError(msg);
    } finally {
      setPdfLoading(false);
    }
  };

  const handlePrint = () => window.print();
  const handleReset = () => setWorksheet(null);

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="no-print bg-white border-b border-slate-200 shadow-sm">
        <div className="max-w-4xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-blue-600 rounded-xl flex items-center justify-center text-white text-base font-bold">
              M
            </div>
            <div>
              <span className="font-bold text-slate-800 text-lg">MathAI</span>
              <span className="ml-2 text-xs text-blue-600 bg-blue-50 px-2 py-0.5 rounded-full font-medium">IB Curriculum</span>
            </div>
          </div>
          <span className="text-sm text-slate-400 hidden sm:block">
            AI-powered Math Worksheets for IB MYP &amp; DP
          </span>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-6 py-10">
        {!worksheet ? (
          <div className="space-y-8">
            <div className="no-print text-center space-y-3">
              <h1 className="text-3xl font-bold text-slate-800">
                Generate IB Math Worksheets
                <span className="text-blue-600"> Instantly</span>
              </h1>
              <p className="text-slate-500 max-w-xl mx-auto">
                AI-generated, curriculum-aligned practice worksheets for IB MYP (Grades 6-10)
                and IB DP (Grades 11-12). Print or download as PDF in seconds.
              </p>
            </div>

            {error && (
              <div className="bg-red-50 border border-red-200 rounded-xl px-5 py-4 text-sm text-red-700">
                {error}
              </div>
            )}

            <WorksheetForm
              onGenerate={handleGenerate}
              onDownloadPdf={handleDownloadPdf}
              loading={loading}
              pdfLoading={pdfLoading}
            />

            <div className="no-print flex flex-wrap justify-center gap-3 pt-2">
              {[
                'IB MYP and DP aligned',
                'Powered by Gemini AI',
                'Print-ready PDF',
                'Hints included',
                'Answer key included',
              ].map(f => (
                <span key={f} className="text-xs text-slate-500 bg-white border border-slate-200 px-3 py-1.5 rounded-full shadow-sm">
                  {f}
                </span>
              ))}
            </div>
          </div>
        ) : (
          <>
            {error && (
              <div className="no-print mb-4 bg-red-50 border border-red-200 rounded-xl px-5 py-4 text-sm text-red-700">
                {error}
              </div>
            )}
            <WorksheetViewer
              worksheet={worksheet}
              onPrint={handlePrint}
              onReset={handleReset}
            />
          </>
        )}
      </main>

      <footer className="no-print text-center py-8 text-xs text-slate-400">
        MathAI &mdash; AI-powered IB Math Worksheets
      </footer>
    </div>
  );
}
