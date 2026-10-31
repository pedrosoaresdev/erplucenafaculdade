import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { apiCode, apiMessage } from '../../core/api-error';
import { MfaSetup, PublicFaculty, TokenResponse } from '../../core/models';
import { RecoveryCodesComponent } from '../../shared/recovery-codes.component';

type Step = 'credentials' | 'setup' | 'codes' | 'verify' | 'recovery' | 'help' | 'forgot';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressBarModule,
    MatTooltipModule, RecoveryCodesComponent],
  templateUrl: './login.component.html',
})
export class LoginComponent implements OnInit, OnDestroy {
  private auth = inject(AuthService);
  private http = inject(HttpClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);

  /** Slug da faculdade vindo da rota /f/:slug/login (ausente = login Master). */
  readonly slug = input<string>();

  readonly step = signal<Step>('credentials');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  readonly faculty = signal<PublicFaculty | null>(null);
  readonly facultyNotFound = signal(false);
  readonly showPassword = signal(false);
  readonly setup = signal<MfaSetup | null>(null);
  readonly recoveryCodes = signal<string[]>([]);
  readonly displayName = signal('');
  readonly copied = signal(false);
  readonly secondsLeft = signal(30);
  private challenge = '';
  private pendingTokens: TokenResponse | null = null;
  private timer?: ReturnType<typeof setInterval>;

  readonly credentials = this.fb.nonNullable.group({
    identifier: ['', [Validators.required, Validators.maxLength(120)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });
  readonly codeForm = this.fb.nonNullable.group({ code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]] });
  readonly recoveryForm = this.fb.nonNullable.group({ recoveryCode: ['', [Validators.required, Validators.minLength(10)]] });

  ngOnInit(): void {
    const msg = this.route.snapshot.queryParamMap.get('msg');
    if (msg) this.info.set(msg);
    const slug = this.slug();
    if (slug) {
      this.http.get<PublicFaculty>(`/api/public/faculties/${encodeURIComponent(slug)}`).subscribe({
        next: f => this.faculty.set(f),
        error: () => this.facultyNotFound.set(true),
      });
    }
    // Contador visual do ciclo de 30 s do Google Authenticator
    this.timer = setInterval(() => this.secondsLeft.set(30 - (Math.floor(Date.now() / 1000) % 30)), 1000);
  }

  ngOnDestroy(): void { clearInterval(this.timer); }

  get isStudentLogin(): boolean { return !!this.slug(); }

  submitCredentials(): void {
    if (this.credentials.invalid) { this.credentials.markAllAsTouched(); return; }
    const { identifier, password } = this.credentials.getRawValue();
    this.run(this.auth.login(this.slug() ?? null, identifier.trim(), password), r => {
      this.challenge = r.challengeToken;
      this.displayName.set(r.displayName);
      this.credentials.controls.password.reset();
      if (r.nextStep === 'MFA_SETUP_REQUIRED') {
        this.step.set('setup');
        this.generateSecret();
      } else {
        this.step.set('verify');
      }
    });
  }

  generateSecret(): void {
    this.codeForm.reset();
    this.run(this.auth.setupMfa(this.challenge), s => this.setup.set(s));
  }

  async copySecret(): Promise<void> {
    const s = this.setup();
    if (!s) return;
    try { await navigator.clipboard.writeText(s.secret.replace(/\s/g, '')); this.copied.set(true); setTimeout(() => this.copied.set(false), 2500); }
    catch { this.error.set('Não foi possível copiar. Selecione a chave e copie manualmente.'); }
  }

  activate(): void {
    if (this.codeForm.invalid) { this.codeForm.markAllAsTouched(); return; }
    this.run(this.auth.activateMfa(this.challenge, this.codeForm.getRawValue().code), t => {
      this.pendingTokens = t;
      this.recoveryCodes.set(t.recoveryCodes ?? []);
      this.step.set('codes');
    });
  }

  finishActivation(): void {
    if (this.pendingTokens) this.establish(this.pendingTokens);
  }

  verify(): void {
    if (this.codeForm.invalid) { this.codeForm.markAllAsTouched(); return; }
    this.run(this.auth.verifyMfa(this.challenge, this.codeForm.getRawValue().code), t => this.establish(t));
  }

  useRecovery(): void {
    if (this.recoveryForm.invalid) { this.recoveryForm.markAllAsTouched(); return; }
    this.run(this.auth.recoveryLogin(this.challenge, this.recoveryForm.getRawValue().recoveryCode), t => {
      if ((t.remainingRecoveryCodes ?? 0) <= 2) {
        this.info.set(`Restam ${t.remainingRecoveryCodes} códigos de recuperação. Gere novos em Perfil › Autenticador.`);
      }
      this.establish(t);
    });
  }

  restart(): void {
    this.step.set('credentials');
    this.challenge = '';
    this.setup.set(null);
    this.codeForm.reset();
    this.recoveryForm.reset();
    this.error.set(null);
  }

  private establish(t: TokenResponse): void {
    this.loading.set(true);
    this.auth.establish(t).subscribe({
      next: p => this.router.navigate(p.mustChangePassword ? ['/perfil'] : ['/inicio'],
        p.mustChangePassword ? { queryParams: { aba: 'senha', obrigatorio: 1 } } : {}),
      error: e => { this.loading.set(false); this.error.set(apiMessage(e)); },
    });
  }

  private run<T>(obs: Observable<T>, ok: (v: T) => void): void {
    this.loading.set(true);
    this.error.set(null);
    obs.subscribe({
      next: v => { this.loading.set(false); ok(v); },
      error: e => {
        this.loading.set(false);
        this.error.set(apiMessage(e));
        this.codeForm.controls.code.reset();
        if (apiCode(e) === 'CHALLENGE_INVALID' || apiCode(e) === 'ACCOUNT_LOCKED') this.step.set('credentials');
      },
    });
  }
}
