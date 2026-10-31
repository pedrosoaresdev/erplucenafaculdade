export const EVENT_LABELS: Record<string, string> = {
  LOGIN_PASSWORD: 'Senha (etapa 1)', LOGIN_MFA: 'Login com autenticador', LOGIN_MFA_SETUP: 'Configuração do autenticador',
  LOGIN_RECOVERY_CODE: 'Login com código de recuperação', MFA_ACTIVATE: 'Ativação do autenticador', MFA_ROTATION_CONFIRM: 'Troca de autenticador',
};
export function eventLabel(e: string): string {
  if (e.startsWith('STEP_UP_')) return 'Confirmação de identidade';
  return EVENT_LABELS[e] ?? e;
}

export const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Ativo', INACTIVE: 'Inativo', BLOCKED: 'Bloqueado', SUSPENDED: 'Suspensa', ARCHIVED: 'Arquivada',
};

export const AUDIT_LABELS: Record<string, string> = {
  FACULTY_CREATED: 'Faculdade criada', FACULTY_UPDATED: 'Faculdade alterada', FACULTY_STATUS_CHANGED: 'Situação da faculdade alterada',
  FACULTY_DELETED: 'Faculdade excluída', FACULTY_SETTINGS_UPDATED: 'Configurações alteradas', USER_CREATED: 'Usuário criado',
  USER_UPDATED: 'Usuário alterado', USER_ROLES_CHANGED: 'Cargos alterados', USER_STATUS_CHANGED: 'Situação do usuário alterada',
  USER_UNLOCKED: 'Usuário desbloqueado', PASSWORD_RESET_BY_ADMIN: 'Senha redefinida (gestor)', MFA_RESET_BY_ADMIN: 'Autenticador redefinido (gestor)',
  USER_SESSIONS_REVOKED: 'Sessões revogadas (gestor)', ROLE_CREATED: 'Cargo criado', ROLE_PERMISSIONS_CHANGED: 'Permissões do cargo alteradas',
  ROLE_DELETED: 'Cargo excluído', MFA_ACTIVATED: 'Autenticador ativado', MFA_ROTATED: 'Autenticador trocado', MFA_ROTATION_STARTED: 'Troca de autenticador iniciada',
  MFA_DISABLED: 'Autenticador desativado', RECOVERY_CODE_USED: 'Código de recuperação usado', RECOVERY_CODES_REGENERATED: 'Códigos de recuperação gerados',
  PASSWORD_CHANGED: 'Senha alterada', PROFILE_UPDATED: 'Perfil alterado', EMAIL_CHANGED: 'E-mail alterado', SESSION_REVOKED: 'Sessão encerrada',
  OTHER_SESSIONS_REVOKED: 'Outras sessões encerradas', PLATFORM_SUPPORT_ACCESS: 'Acesso de suporte da plataforma',
  PLATFORM_SETUP_COMPLETED: 'Instalação da plataforma concluída',
  COURSE_CREATED: 'Curso criado', COURSE_UPDATED: 'Curso alterado', COURSE_DELETED: 'Curso excluído', CURRICULUM_CHANGED: 'Matriz curricular alterada',
  SUBJECT_CREATED: 'Disciplina criada', SUBJECT_UPDATED: 'Disciplina alterada', SUBJECT_DELETED: 'Disciplina excluída',
  TERM_CREATED: 'Período letivo criado', TERM_UPDATED: 'Período letivo alterado', TERM_STATUS_CHANGED: 'Situação do período alterada', TERM_DELETED: 'Período letivo excluído',
  CLASS_CREATED: 'Turma criada', CLASS_UPDATED: 'Turma alterada', CLASS_DELETED: 'Turma excluída',
  CLASS_SUBJECT_ADDED: 'Disciplina ofertada na turma', CLASS_SUBJECT_UPDATED: 'Oferta de disciplina alterada', CLASS_SUBJECT_REMOVED: 'Oferta de disciplina removida',
  CLASS_STUDENT_ADDED: 'Aluno incluído na turma', CLASS_STUDENT_REMOVED: 'Aluno removido da turma',
  STUDENT_CREATED: 'Aluno cadastrado', STUDENT_UPDATED: 'Cadastro de aluno alterado', STUDENT_STATUS_CHANGED: 'Situação do aluno alterada', STUDENT_ACCESS_CREATED: 'Acesso do aluno gerado',
  TEACHER_CREATED: 'Professor cadastrado', TEACHER_UPDATED: 'Professor alterado', TEACHER_ACCESS_CREATED: 'Acesso do professor gerado', TEACHER_DELETED: 'Professor excluído',
  EMPLOYEE_CREATED: 'Funcionário cadastrado', EMPLOYEE_UPDATED: 'Funcionário alterado', EMPLOYEE_ACCESS_CREATED: 'Acesso do funcionário gerado', EMPLOYEE_DELETED: 'Funcionário excluído',
  ASSESSMENT_CREATED: 'Avaliação criada', ASSESSMENT_UPDATED: 'Avaliação alterada', ASSESSMENT_DELETED: 'Avaliação excluída', GRADE_CHANGED: 'Nota alterada',
  ATTENDANCE_RECORDED: 'Frequência registrada', ATTENDANCE_CHANGED: 'Frequência alterada', ATTENDANCE_DELETED: 'Aula excluída do diário',
  TEACHING_PLAN_UPDATED: 'Plano de ensino alterado',
};
