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
  OTHER_SESSIONS_REVOKED: 'Outras sessões encerradas',
};
