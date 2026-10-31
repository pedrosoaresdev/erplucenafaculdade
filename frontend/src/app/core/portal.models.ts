export type AssessmentKind = 'PROVA' | 'TRABALHO' | 'SEMINARIO' | 'RECUPERACAO' | 'OUTRO';
export type Situation = 'EM_CURSO' | 'APROVADO' | 'REPROVADO_NOTA' | 'REPROVADO_FREQUENCIA';

export interface Result {
  studentId: string; average?: number; recovery?: number; finalGrade?: number; totalLessons: number; absences: number;
  attendancePct: number; situation: Situation; riskGrade: boolean; riskAttendance: boolean;
}
export interface TeachingView {
  classSubjectId: string; classId: string; classCode: string; courseName: string; termCode: string; termStatus: string;
  subjectId: string; subjectCode: string; subjectName: string; workloadHours: number; weekday?: number; startTime?: string;
  endTime?: string; room?: string; students: number; sessions: number; assessments: number;
}
export interface AssessmentView { id: string; title: string; kind: AssessmentKind; weight: number; maxScore: number; dueDate?: string; graded: number; }
export interface StudentRow { studentId: string; registration: string; name: string; status: string; scores: Record<string, number>; result: Result; }
export interface DiaryView {
  teaching: TeachingView; teachingPlan?: string; syllabus?: string; minGrade: number; minAttendancePct: number;
  assessments: AssessmentView[]; students: StudentRow[]; editable: boolean;
}
export interface AbsenceEntry { studentId: string; absences: number; justified: boolean; }
export interface SessionView { id: string; date: string; lessons: number; content?: string; absentStudents: number; absences: AbsenceEntry[]; }

export interface ScheduleItem { classSubjectId: string; subjectName: string; teacherName?: string; weekday: number; startTime: string; endTime: string; room?: string; }
export interface SubjectSummary { classSubjectId: string; subjectCode: string; subjectName: string; teacherName?: string; workloadHours: number; result: Result; }
export interface StudentOverview {
  studentId: string; registration: string; name: string; status: string; courseName?: string; classCode?: string; termCode?: string;
  termStatus?: string; semester?: number; minGrade: number; minAttendancePct: number; schedule: ScheduleItem[]; subjects: SubjectSummary[];
}
export interface MyAssessment { title: string; kind: AssessmentKind; weight: number; maxScore: number; dueDate?: string; score?: number; }
export interface MyAttendance { date: string; lessons: number; content?: string; absences: number; justified: boolean; }
export interface MySubjectDetail { summary: SubjectSummary; teachingPlan?: string; syllabus?: string; assessments: MyAssessment[]; attendance: MyAttendance[]; }
export interface HistoryTerm { termCode: string; termStatus: string; classCode: string; courseName: string; subjects: SubjectSummary[]; }
export interface CurriculumProgressItem { subjectCode: string; subjectName: string; semester: number; mandatory: boolean; workloadHours: number; credits: number; status: string; }
export interface CurriculumProgress {
  courseName?: string; totalCredits: number; completedCredits: number; totalHours: number; completedHours: number; completionPct: number;
  items: CurriculumProgressItem[];
}

export const KINDS: Record<AssessmentKind, string> = { PROVA: 'Prova', TRABALHO: 'Trabalho', SEMINARIO: 'Seminário', RECUPERACAO: 'Recuperação', OUTRO: 'Outro' };
export const SITUATIONS: Record<Situation, string> = {
  EM_CURSO: 'Em curso', APROVADO: 'Aprovado', REPROVADO_NOTA: 'Reprovado por nota', REPROVADO_FREQUENCIA: 'Reprovado por frequência',
};
export const CURRICULUM_STATUS: Record<string, string> = { APROVADA: 'Aprovada', CURSANDO: 'Cursando', REPROVADA: 'Reprovada', A_CURSAR: 'A cursar' };

export function situationBadge(r?: Result): string {
  if (!r) return 'inactive';
  if (r.situation === 'APROVADO') return 'ok';
  if (r.situation !== 'EM_CURSO') return 'error';
  return r.riskGrade || r.riskAttendance ? 'warn' : 'pending';
}

/** Baixa um Blob como arquivo. */
export function saveBlob(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = name; a.click();
  URL.revokeObjectURL(url);
}
