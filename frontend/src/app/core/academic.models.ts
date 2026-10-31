export type Degree = 'BACHARELADO' | 'LICENCIATURA' | 'TECNOLOGO' | 'POS_GRADUACAO' | 'EXTENSAO';
export type Modality = 'PRESENCIAL' | 'EAD' | 'HIBRIDO';
export type Shift = 'MANHA' | 'TARDE' | 'NOITE' | 'INTEGRAL' | 'EAD';
export type TermStatus = 'PLANNED' | 'OPEN' | 'CLOSED';
export type StudentStatus = 'ACTIVE' | 'LOCKED' | 'CANCELLED' | 'TRANSFERRED' | 'GRADUATED' | 'DROPPED_OUT';

export interface CourseView {
  id: string; code: string; name: string; description?: string; degree: Degree; modality: Modality; durationSemesters: number;
  totalHours?: number; monthlyFee: number; coordinatorId?: string; coordinatorName?: string; active: boolean;
  curriculumSubjects: number; classes: number; createdAt: string;
}
export interface SubjectView { id: string; code: string; name: string; workloadHours: number; credits: number; syllabus?: string; active: boolean; }
export interface CurriculumView {
  id: string; subjectId: string; subjectCode: string; subjectName: string; workloadHours: number; credits: number; semester: number;
  mandatory: boolean; prerequisiteSubjectId?: string; prerequisiteName?: string;
}
export interface CurriculumSummary { courseId: string; courseName: string; durationSemesters: number; totalHours: number; totalCredits: number; items: CurriculumView[]; }
export interface TermView { id: string; code: string; startDate: string; endDate: string; status: TermStatus; classes: number; }
export interface ClassView {
  id: string; courseId: string; courseName: string; termId: string; termCode: string; code: string; semester: number; shift: Shift;
  capacity: number; room?: string; active: boolean; students: number; subjects: number;
}
export interface ClassSubjectView {
  id: string; subjectId: string; subjectCode: string; subjectName: string; workloadHours: number; teacherId?: string; teacherName?: string;
  weekday?: number; startTime?: string; endTime?: string; teachingPlan?: string;
}
export interface ClassStudentView { studentId: string; registration: string; name: string; status: StudentStatus; joinedAt: string; }
export interface ClassDetail { turma: ClassView; subjects: ClassSubjectView[]; students: ClassStudentView[]; }
export interface StudentView {
  id: string; registration: string; fullName: string; socialName?: string; cpfMasked: string; cpf?: string; rg?: string; birthDate?: string;
  email?: string; phone?: string; addressStreet?: string; addressNumber?: string; addressDistrict?: string; addressCity?: string;
  addressState?: string; addressZip?: string; status: StudentStatus; hasAccess: boolean; currentClass?: string; createdAt: string;
}
export interface TeacherView {
  id: string; fullName: string; cpfMasked: string; cpf?: string; email?: string; phone?: string; academicDegree?: string;
  status: 'ACTIVE' | 'INACTIVE'; hasAccess: boolean; username?: string; classSubjects: number;
}
export interface EmployeeView {
  id: string; fullName: string; cpfMasked: string; cpf?: string; email?: string; phone?: string; department?: string; jobTitle?: string;
  status: 'ACTIVE' | 'INACTIVE'; hasAccess: boolean; username?: string;
}
export interface Created<T> { record: T; username?: string; temporaryPassword?: string; }

export const DEGREES: Record<Degree, string> = {
  BACHARELADO: 'Bacharelado', LICENCIATURA: 'Licenciatura', TECNOLOGO: 'Tecnólogo', POS_GRADUACAO: 'Pós-graduação', EXTENSAO: 'Extensão',
};
export const MODALITIES: Record<Modality, string> = { PRESENCIAL: 'Presencial', EAD: 'EAD', HIBRIDO: 'Híbrido' };
export const SHIFTS: Record<Shift, string> = { MANHA: 'Manhã', TARDE: 'Tarde', NOITE: 'Noite', INTEGRAL: 'Integral', EAD: 'EAD' };
export const TERM_STATUS: Record<TermStatus, string> = { PLANNED: 'Planejado', OPEN: 'Aberto', CLOSED: 'Encerrado' };
export const STUDENT_STATUS: Record<StudentStatus, string> = {
  ACTIVE: 'Ativo', LOCKED: 'Trancado', CANCELLED: 'Cancelado', TRANSFERRED: 'Transferido', GRADUATED: 'Formado', DROPPED_OUT: 'Evadido',
};
export const WEEKDAYS = ['', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'];

export function formatCpf(v?: string): string {
  return v ? v.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, '$1.$2.$3-$4') : '';
}
