import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap } from 'rxjs';
import { LoginResponse, MfaSetup, Profile, TokenResponse } from './models';

export const CLIENT_HEADER = { 'X-ERP-Client': 'web' };

/**
 * Sessão do usuário. O access token fica somente em memória; a sessão persiste via cookie
 * HttpOnly (refresh) gerenciado pelo servidor — nada sensível é gravado no navegador.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private token = signal<string | null>(null);
  readonly profile = signal<Profile | null>(null);
  readonly isAuthenticated = computed(() => !!this.token() && !!this.profile());
  private refreshing$: Observable<boolean> | null = null;
  /** Slug da faculdade usado no último login (para voltar à tela de login correta). */
  lastFacultySlug: string | null = null;

  accessToken(): string | null { return this.token(); }

  hasPermission(...perms: string[]): boolean {
    const p = this.profile();
    if (!p) return false;
    if (p.platformAdmin) return true;
    return perms.some(x => p.permissions.includes(x));
  }

  login(faculty: string | null, identifier: string, password: string) {
    this.lastFacultySlug = faculty;
    return this.http.post<LoginResponse>('/api/auth/login', { faculty, identifier, password });
  }

  setupMfa(challengeToken: string) {
    return this.http.post<MfaSetup>('/api/auth/mfa/setup', { challengeToken });
  }

  activateMfa(challengeToken: string, code: string) {
    return this.http.post<TokenResponse>('/api/auth/mfa/activate', { challengeToken, code }, { withCredentials: true });
  }

  verifyMfa(challengeToken: string, code: string) {
    return this.http.post<TokenResponse>('/api/auth/mfa/verify', { challengeToken, code }, { withCredentials: true });
  }

  recoveryLogin(challengeToken: string, recoveryCode: string) {
    return this.http.post<TokenResponse>('/api/auth/mfa/recovery', { challengeToken, recoveryCode }, { withCredentials: true });
  }

  /** Conclui o login: guarda o token em memória e carrega o perfil. */
  establish(t: TokenResponse): Observable<Profile> {
    this.token.set(t.accessToken);
    return this.loadProfile();
  }

  loadProfile(): Observable<Profile> {
    return this.http.get<Profile>('/api/me').pipe(tap(p => this.profile.set(p)));
  }

  /** Tenta renovar a sessão (cookie HttpOnly). Requisições concorrentes compartilham a mesma renovação. */
  refresh(): Observable<boolean> {
    if (!this.refreshing$) {
      this.refreshing$ = this.http.post<TokenResponse>('/api/auth/refresh', {}, { withCredentials: true, headers: CLIENT_HEADER }).pipe(
        tap(t => this.token.set(t.accessToken)),
        switchMap(() => this.loadProfile()),
        map(() => true),
        catchError(() => { this.clear(); return of(false); }),
        finalize(() => (this.refreshing$ = null)),
        shareReplay(1)
      );
    }
    return this.refreshing$;
  }

  /** Garante sessão ao abrir/recarregar a página. */
  ensureSession(): Observable<boolean> {
    return this.isAuthenticated() ? of(true) : this.refresh();
  }

  logout(redirect = true): void {
    const slug = this.loginSlug();
    this.http.post('/api/auth/logout', {}, { withCredentials: true }).pipe(catchError(() => of(null))).subscribe();
    this.clear();
    if (redirect) this.goToLogin(slug);
  }

  /** Encerra a sessão local após o servidor ter revogado as sessões (ex.: troca de senha). */
  endLocalSession(message?: string): void {
    const slug = this.loginSlug();
    this.clear();
    this.goToLogin(slug, message);
  }

  loginSlug(): string | null {
    return this.profile()?.faculty?.slug ?? this.lastFacultySlug;
  }

  goToLogin(slug: string | null, message?: string): void {
    const extras = message ? { queryParams: { msg: message } } : {};
    this.router.navigate(slug ? ['/f', slug, 'login'] : ['/login'], extras);
  }

  private clear(): void {
    this.token.set(null);
    this.profile.set(null);
  }
}
