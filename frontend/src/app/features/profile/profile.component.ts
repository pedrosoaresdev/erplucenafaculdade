import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { Observable, filter, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { apiMessage } from '../../core/api-error';
import { AccessView, MfaStatus, Page, SessionView } from '../../core/models';
import { PasswordStrengthComponent } from '../../shared/password-strength.component';
import { RecoveryCodesComponent } from '../../shared/recovery-codes.component';
import { StepUpDialogComponent, StepUpPurpose } from '../../shared/step-up-dialog.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { eventLabel } from '../../shared/labels';

const TABS = ['dados', 'senha', 'autenticador', 'sessoes', 'acessos'];

function matchPasswords(g: AbstractControl): ValidationErrors | null {
  const a = g.get('newPassword')?.value, b = g.get('confirmPassword')?.value;
  return a && b && a !== b ? { mismatch: true } : null;
}

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, DatePipe, MatTabsModule, MatCardModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatDialogModule, MatSnackBarModule, MatPaginatorModule, PasswordStrengthComponent, RecoveryCodesComponent],
  templateUrl: './profile.component.html',
})
export class ProfileComponent implements OnInit {
  readonly auth = inject(AuthService);
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  readonly tab = signal(0);
  readonly mandatory = signal(false);
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);
  readonly eventLabel = eventLabel;

  // ---- Dados
  readonly dataForm = this.fb.nonNullable.group({
    socialName: ['', Validators.maxLength(200)], phone: ['', Validators.maxLength(30)],
    photoUrl: ['', [Validators.maxLength(500), Validators.pattern(/^(https:\/\/.+)?$/)]],
  });
  readonly emailForm = this.fb.nonNullable.group({ email: ['', [Validators.required, Validators.email]] });

  // ---- Senha (2 etapas)
  readonly pwdStage = signal<1 | 2>(1);
  private pwdToken = '';
  readonly authForm = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required], totpCode: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });
  readonly newPwdForm = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
    confirmPassword: ['', Validators.required],
  }, { validators: matchPasswords });
  readonly newPwdValue = signal('');

  // ---- Autenticador
  readonly mfa = signal<MfaStatus | null>(null);
  readonly rotation = signal<{ secret: string; qrCodeDataUrl: string; accountLabel: string } | null>(null);
  readonly rotationCode = this.fb.nonNullable.control('', [Validators.required, Validators.pattern(/^\d{6}$/)]);
  readonly newCodes = signal<string[] | null>(null);

  // ---- Sessões / acessos
  readonly sessions = signal<SessionView[]>([]);
  readonly access = signal<Page<AccessView> | null>(null);

  readonly profile = computed(() => this.auth.profile());

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    const idx = TABS.indexOf(q.get('aba') ?? 'dados');
    this.tab.set(idx < 0 ? 0 : idx);
    this.mandatory.set(!!q.get('obrigatorio') || !!this.profile()?.mustChangePassword);
    const p = this.profile();
    if (p) {
      this.dataForm.setValue({ socialName: p.socialName ?? '', phone: p.phone ?? '', photoUrl: p.photoUrl ?? '' });
      this.emailForm.setValue({ email: p.email });
    }
    this.newPwdForm.controls.newPassword.valueChanges.subscribe(v => this.newPwdValue.set(v));
    this.onTab(this.tab());
  }

  onTab(i: number): void {
    this.tab.set(i);
    this.error.set(null);
    this.router.navigate([], { queryParams: { aba: TABS[i] }, queryParamsHandling: 'merge', replaceUrl: true });
    if (TABS[i] === 'autenticador') this.loadMfa();
    if (TABS[i] === 'sessoes') this.loadSessions();
    if (TABS[i] === 'acessos') this.loadAccess(0);
  }

  // ---------------- Dados
  saveData(): void {
    if (this.dataForm.invalid) return;
    this.exec(this.http.patch('/api/me', this.dataForm.getRawValue()), () => {
      this.auth.loadProfile().subscribe();
      this.snack.open('Dados atualizados.', 'OK', { duration: 3000 });
    });
  }

  saveEmail(): void {
    if (this.emailForm.invalid) return;
    this.stepUp('PROFILE_SENSITIVE', 'Alterar o e-mail exige confirmação de identidade.').pipe(
      switchMap(token => this.http.put('/api/me/email', { stepUpToken: token, email: this.emailForm.getRawValue().email }))
    ).subscribe({
      next: () => { this.auth.loadProfile().subscribe(); this.snack.open('E-mail alterado.', 'OK', { duration: 3000 }); },
      error: e => this.error.set(apiMessage(e)),
    });
  }

  // ---------------- Senha
  authenticate(): void {
    if (this.authForm.invalid) { this.authForm.markAllAsTouched(); return; }
    this.exec(this.http.post<{ stepUpToken: string }>('/api/me/step-up', { ...this.authForm.getRawValue(), purpose: 'PASSWORD_CHANGE' }), r => {
      this.pwdToken = r.stepUpToken;
      this.pwdStage.set(2);
      this.authForm.reset();
    }, () => this.authForm.controls.totpCode.reset());
  }

  confirmPassword(): void {
    if (this.newPwdForm.invalid) { this.newPwdForm.markAllAsTouched(); return; }
    this.exec(this.http.post('/api/me/password', { stepUpToken: this.pwdToken, ...this.newPwdForm.getRawValue() }), () => {
      this.newPwdForm.reset();
      this.auth.endLocalSession('Senha alterada com sucesso. Por segurança, todas as sessões foram encerradas. Entre com a nova senha.');
    }, () => this.pwdStage.set(1)); // o token de step-up é de uso único: nova confirmação é necessária
  }

  // ---------------- Autenticador
  loadMfa(): void {
    this.http.get<MfaStatus>('/api/me/mfa').subscribe(s => this.mfa.set(s));
  }

  startRotation(): void {
    this.stepUp('MFA_ROTATE', 'Para trocar o dispositivo, confirme com a senha e o código do autenticador ATUAL.').pipe(
      switchMap(token => this.http.post<{ secret: string; qrCodeDataUrl: string; accountLabel: string }>('/api/me/mfa/rotation/start', { stepUpToken: token }))
    ).subscribe({ next: r => { this.rotation.set(r); this.newCodes.set(null); }, error: e => this.error.set(apiMessage(e)) });
  }

  confirmRotation(): void {
    if (this.rotationCode.invalid) return;
    this.exec(this.http.post<{ codes: string[] }>('/api/me/mfa/rotation/confirm', { code: this.rotationCode.value }), r => {
      this.rotation.set(null);
      this.rotationCode.reset();
      this.newCodes.set(r.codes);
      this.loadMfa();
      this.snack.open('Novo autenticador ativado. O anterior foi revogado.', 'OK', { duration: 4000 });
    }, () => this.rotationCode.reset());
  }

  regenerateCodes(): void {
    this.stepUp('RECOVERY_CODES', 'Os códigos atuais deixarão de funcionar.').pipe(
      switchMap(token => this.http.post<{ codes: string[] }>('/api/me/mfa/recovery-codes', { stepUpToken: token }))
    ).subscribe({ next: r => { this.newCodes.set(r.codes); this.loadMfa(); }, error: e => this.error.set(apiMessage(e)) });
  }

  disableMfa(): void {
    this.dialog.open(ConfirmDialogComponent, { data: {
      title: 'Desativar autenticador', danger: true, confirmLabel: 'Continuar',
      message: 'A verificação em duas etapas é obrigatória: todas as sessões serão encerradas e você deverá configurar um novo autenticador no próximo acesso.',
    } }).afterClosed().pipe(
      filter(Boolean),
      switchMap(() => this.stepUp('MFA_DISABLE', 'Confirme para desativar o autenticador atual.')),
      switchMap(token => this.http.post('/api/me/mfa/disable', { stepUpToken: token }))
    ).subscribe({
      next: () => this.auth.endLocalSession('Autenticador desativado. Entre novamente para configurar um novo dispositivo.'),
      error: e => this.error.set(apiMessage(e)),
    });
  }

  copy(text: string): void {
    navigator.clipboard.writeText(text.replace(/\s/g, '')).then(() => this.snack.open('Chave copiada.', 'OK', { duration: 2000 }), () => {});
  }

  // ---------------- Sessões / acessos
  loadSessions(): void {
    this.http.get<SessionView[]>('/api/me/sessions').subscribe(s => this.sessions.set(s));
  }

  revoke(s: SessionView): void {
    this.exec(this.http.delete(`/api/me/sessions/${s.id}`), () => s.current ? this.auth.endLocalSession('Sessão encerrada.') : this.loadSessions());
  }

  revokeOthers(): void {
    this.exec(this.http.post<{ revoked: number }>('/api/me/sessions/revoke-others', {}), r => {
      this.snack.open(`${r.revoked} sessão(ões) encerrada(s).`, 'OK', { duration: 3000 });
      this.loadSessions();
    });
  }

  loadAccess(page: number): void {
    this.http.get<Page<AccessView>>('/api/me/access-history', { params: { page, size: 15 } }).subscribe(p => this.access.set(p));
  }

  pageAccess(e: PageEvent): void { this.loadAccess(e.pageIndex); }

  private stepUp(purpose: StepUpPurpose, reason: string): Observable<string> {
    return this.dialog.open(StepUpDialogComponent, { data: { purpose, reason }, width: '420px' }).afterClosed().pipe(filter((t): t is string => !!t));
  }

  private exec<T>(obs: Observable<T>, ok: (v: T) => void, fail?: (e: unknown) => void): void {
    this.loading.set(true);
    this.error.set(null);
    obs.subscribe({
      next: v => { this.loading.set(false); ok(v); },
      error: e => { this.loading.set(false); this.error.set(apiMessage(e)); fail?.(e); },
    });
  }
}
