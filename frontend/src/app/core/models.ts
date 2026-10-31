export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }

export interface FacultyInfo { id: string; slug: string; name: string; logoUrl?: string; }

export interface Profile {
  id: string; username: string; fullName: string; socialName?: string; email: string; phone?: string; photoUrl?: string;
  status: string; createdAt: string; lastLoginAt?: string; platformAdmin: boolean; mustChangePassword: boolean;
  mfaEnabled: boolean; faculty?: FacultyInfo; roles: string[]; permissions: string[]; registration?: string;
}

export type NextStep = 'MFA_SETUP_REQUIRED' | 'MFA_REQUIRED';
export interface LoginResponse { nextStep: NextStep; challengeToken: string; expiresIn: number; displayName: string; }
export interface MfaSetup { secret: string; otpauthUri: string; qrCodeDataUrl: string; accountLabel: string; }
export interface TokenResponse {
  accessToken: string; tokenType: string; expiresIn: number; mustChangePassword: boolean;
  recoveryCodes?: string[]; remainingRecoveryCodes?: number;
}
export interface PublicFaculty { slug: string; name: string; logoUrl?: string; primaryColor: string; }

export interface SessionView { id: string; deviceLabel: string; ipAddress: string; userAgent: string; createdAt: string; lastSeenAt: string; expiresAt: string; current: boolean; }
export interface AccessView { event: string; success: boolean; ipAddress: string; userAgent?: string; detail?: string; createdAt: string; }
export interface NotificationView { id: string; category: string; title: string; body?: string; link?: string; readAt?: string; createdAt: string; }
export interface MfaStatus { enabled: boolean; activatedAt?: string; recoveryCodesRemaining: number; rotationPending: boolean; }

export interface FacultyView {
  id: string; slug: string; name: string; legalName: string; tradeName?: string; cnpj: string; email: string; phone?: string;
  addressStreet?: string; addressNumber?: string; addressComplement?: string; addressDistrict?: string; addressCity?: string;
  addressState?: string; addressZip?: string; logoUrl?: string; status: 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED'; statusReason?: string;
  planId?: string; planName?: string; createdAt: string; users: number; students: number; courses: number;
}
export interface PlanView { id: string; code: string; name: string; maxStudents?: number; maxUsers?: number; monthlyPrice: number; active: boolean; }

export interface RoleRef { id: string; code: string; name: string; }
export interface UserView {
  id: string; facultyId: string; username: string; fullName: string; email: string; phone?: string; status: 'ACTIVE' | 'INACTIVE' | 'BLOCKED';
  mfaEnabled: boolean; mustChangePassword: boolean; locked: boolean; lastLoginAt?: string; createdAt: string; roles: RoleRef[];
}
export interface RoleView { id: string; code: string; name: string; description?: string; systemRole: boolean; permissions: string[]; users: number; }
export interface PermissionView { code: string; module: string; description: string; }
export interface AuditView { id: number; facultyId?: string; actorName?: string; action: string; entityType: string; entityId?: string; details?: Record<string, unknown>; ipAddress?: string; createdAt: string; }
