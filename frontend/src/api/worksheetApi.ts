import axios, { AxiosError } from 'axios';
import type { WorksheetRequest, WorksheetResponse } from '../types/worksheet';

const BASE = '/api';

function extractErrorMessage(e: unknown): string {
  if (e instanceof AxiosError && e.response?.data?.message) {
    return e.response.data.message;
  }
  if (e instanceof Error) return e.message;
  return 'An unexpected error occurred. Please try again.';
}

export async function fetchTopics(grade: number): Promise<string[]> {
  const res = await axios.get<{ topics: string[] }>(`${BASE}/topics`, { params: { grade } });
  return res.data.topics;
}

export async function generateWorksheet(req: WorksheetRequest): Promise<WorksheetResponse> {
  try {
    const res = await axios.post<WorksheetResponse>(`${BASE}/worksheet/generate`, req);
    return res.data;
  } catch (e) {
    throw new Error(extractErrorMessage(e));
  }
}

export async function downloadPdf(req: WorksheetRequest): Promise<void> {
  try {
    const res = await axios.post(`${BASE}/worksheet/pdf`, req, { responseType: 'blob' });

    // Prefer the filename the backend sets in Content-Disposition (includes timestamp).
    // Fall back to a client-generated name only if the header is absent.
    const disposition: string = res.headers['content-disposition'] ?? '';
    const match = disposition.match(/filename="([^"]+)"/);
    const filename = match
      ? match[1]
      : `IB_Math_Grade${req.grade}_${req.topic.replace(/[^a-zA-Z0-9]/g, '_')}_${
          new Date().toISOString().replace(/[-T:.Z]/g, '').slice(0, 14)
        }.pdf`;

    const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  } catch (e) {
    throw new Error(extractErrorMessage(e));
  }
}

