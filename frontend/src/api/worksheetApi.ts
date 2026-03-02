import axios from 'axios';
import type { WorksheetRequest, WorksheetResponse } from '../types/worksheet';

const BASE = '/api';

export async function fetchTopics(grade: number): Promise<string[]> {
  const res = await axios.get<{ topics: string[] }>(`${BASE}/topics`, { params: { grade } });
  return res.data.topics;
}

export async function generateWorksheet(req: WorksheetRequest): Promise<WorksheetResponse> {
  const res = await axios.post<WorksheetResponse>(`${BASE}/worksheet/generate`, req);
  return res.data;
}

export async function downloadPdf(req: WorksheetRequest): Promise<void> {
  const res = await axios.post(`${BASE}/worksheet/pdf`, req, { responseType: 'blob' });
  const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
  const link = document.createElement('a');
  link.href = url;
  const topic = req.topic.replace(/[^a-zA-Z0-9]/g, '_');
  link.setAttribute('download', `IB_Math_Grade${req.grade}_${topic}.pdf`);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

