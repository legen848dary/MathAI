export interface Question {
  number: number;
  text: string;
  hint?: string;
  diagram?: string;   // inline SVG string, present only when a visual aid was generated
}

export interface WorksheetResponse {
  title: string;
  grade: string;
  topic: string;
  difficulty: string;
  instructions: string;
  questions: Question[];
  answerKey: string[];
}

export interface WorksheetRequest {
  grade: number;
  topic: string;
  difficulty: string;
  questionCount: number;
}

export type Difficulty = 'Easy' | 'Medium' | 'Hard';

