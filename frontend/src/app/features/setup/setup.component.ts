import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { apiCode, apiMessage } from '../../core/api-error';
import { TokenResponse } from '../../core/models';
import { PasswordStrengthComponent } from '../../shared/password-strength.component';
import { RecoveryCodesComponent } from '../../shared/recovery-codes.component';

interface Enrollment { setupToken: string; expiresIn: number; secret: string; otpauthUri: string; qrCodeDataUrl: string; accountLabel: string; }
type Step = 'code' | 'totp' | 'account' | 'codes' | 'done';

function match(g: AbstractControl): ValidationErrors | null {
  const a = g.get('password')?.value, b = g.get('confirmPassword')?.value;
  return a && b && a !== b ? { mismatch: true } : null;
}

/** Instalação inicial: somente o dono da plataforma (e-mail configurado + código exibido no console do servidor). */
@Component({
  selector: 'app-setup',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressBarModule,
    PasswordStrengthComponent, RecoveryCodesComponent],
  template: `
    <main class="login-page" id="conteudo">
      <section class="login-brand" aria-hidden="true"><div class="brand-inner">
        <mat-icon class="brand-icon">rocket_launch</mat-icon><h2>Instalação do ERP</h2>
        <p>Configure o administrador Master da plataforma. Esta etapa é feita uma única vez, apenas pelo dono do sistema.</p>
      </div></section>
      <section class="login-card" aria-labelledby="setup-title">
        @if (loading()) { <mat-progress-bar mode="indeterminate" aria-label="Carregando" /> }
        <header class="login-header">
          <div class="faculty-logo placeholder" aria-hidden="true"><mat-icon>admin_panel_settings</mat-icon></div>
          <h1 id="setup-title">Instalação inicial</h1>
          <p class="muted">Etapa {{ stepNumber() }} de 3</p>
        </header>
        @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }

        @switch (step()) {
          @case ('code') {
            <form [formGroup]="codeForm" (ngSubmit)="start()" novalidate>
              <p>Informe o e-mail do dono da plataforma e o <strong>código de instalação</strong> exibido no console do servidor
                (janela do <code>docker compose</code>).</p>
              <mat-form-field class="full"><mat-label>Seu e-mail</mat-label>
                <input matInput type="email" formControlName="email" autocomplete="email" required></mat-form-field>
              <mat-form-field class="full"><mat-label>Código de instalação</mat-label>
                <input matInput formControlName="setupCode" placeholder="XXXX-XXXX-XXXX" autocomplete="off" autocapitalize="characters" required></mat-form-field>
              <button mat-flat-button class="full big" type="submit" [disabled]="loading()">Continuar</button>
            </form>
          }
          @case ('totp') {
            @if (enrollment(); as e) {
              <section>
                <h2 class="step-title"><mat-icon aria-hidden="true">verified_user</mat-icon> Configure o Google Authenticator</h2>
                <ol class="setup-steps">
                  <li>Abra o <strong>Google Authenticator</strong> no celular e toque em <strong>+</strong>.</li>
                  <li>Escolha <strong>Ler código QR</strong> e leia a imagem abaixo (ou digite a chave).</li>
                  <li>Informe o código de 6 dígitos exibido no aplicativo.</li>
                </ol>
                <div class="qr-box">
                  <img [src]="e.qrCodeDataUrl" width="220" height="220" [alt]="'QR Code para adicionar ' + e.accountLabel + ' no Google Authenticator'">
                  <div class="secret"><span class="muted">Chave secreta:</span><code class="secret-value">{{ e.secret }}</code>
                    <div class="row-actions">
                      <button mat-stroked-button type="button" (click)="copy(e.secret)"><mat-icon>content_copy</mat-icon> {{ copied() ? 'Copiada!' : 'Copiar chave' }}</button>
                      <button mat-button type="button" (click)="regenerate()"><mat-icon>autorenew</mat-icon> Gerar novo segredo</button>
                    </div></div>
                </div>
                <form [formGroup]="totpForm" (ngSubmit)="verify()" novalidate>
                  <mat-form-field class="full code-field"><mat-label>Código de 6 dígitos exibido no aplicativo</mat-label>
                    <input matInput formControlName="code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" required></mat-form-field>
                  <button mat-flat-button class="full big" type="submit" [disabled]="loading() || totpForm.invalid">Validar e continuar</button>
                </form>
              </section>
            }
          }
          @case ('account') {
            <form [formGroup]="accountForm" (ngSubmit)="complete()" novalidate>
              <h2 class="step-title"><mat-icon aria-hidden="true">person_add</mat-icon> Crie seu usuário e senha</h2>
              <mat-form-field class="full"><mat-label>Seu nome completo</mat-label><input matInput formControlName="fullName" autocomplete="name" required></mat-form-field>
              <mat-form-field class="full"><mat-label>Nome de usuário</mat-label><input matInput formControlName="username" autocomplete="username" autocapitalize="none" required>
                <mat-hint>3 a 60 caracteres: letras, números, ponto, hífen ou sublinhado</mat-hint></mat-form-field>
              <mat-form-field class="full"><mat-label>Senha</mat-label><input matInput type="password" formControlName="password" autocomplete="new-password" required></mat-form-field>
              <app-password-strength [password]="pwd()" />
              <mat-form-field class="full"><mat-label>Repetir senha</mat-label><input matInput type="password" formControlName="confirmPassword" autocomplete="new-password" required></mat-form-field>
              @if (accountForm.hasError('mismatch') && accountForm.controls.confirmPassword.touched) { <p class="field-error" role="alert">As senhas não coincidem.</p> }
              <button mat-flat-button class="full big" type="submit" [disabled]="loading() || accountForm.invalid">Concluir instalação</button>
            </form>
          }
          @case ('codes') {
            <h2 class="step-title"><mat-icon aria-hidden="true">key</mat-icon> Instalação concluída — códigos de recuperação</h2>
            <app-recovery-codes [codes]="codes()" [account]="accountForm.getRawValue().username" (done)="finish()" />
          }
          @case ('done') {
            <section><p class="alert info">A plataforma já está instalada. Use a tela de login.</p>
              <button mat-flat-button class="full" type="button" (click)="router.navigate(['/login'])">Ir para o login</button></section>
          }
        }
      </section>
    </main>
  `,
})
export class SetupComponent implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);
  readonly router = inject(Router);

  readonly step = signal<Step>('code');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly enrollment = signal<Enrollment | null>(null);
  readonly codes = signal<string[]>([]);
  readonly copied = signal(false);
  readonly pwd = signal('');
  private tokens: TokenResponse | null = null;

  readonly codeForm = this.fb.nonNullable.group({ email: ['', [Validators.required, Validators.email]], setupCode: ['', Validators.required] });
  readonly totpForm = this.fb.nonNullable.group({ code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]] });
  readonly accountForm = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(200)]],
    username: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{3,60}$/)]],
    password: ['', [Validators.required, Validators.minLength(8)]], confirmPassword: ['', Validators.required],
  }, { validators: match });

  ngOnInit(): void {
    this.http.get<{ setupRequired: boolean }>('/api/setup/status').subscribe({
      next: s => { if (!s.setupRequired) this.step.set('done'); },
      error: () => this.step.set('done'),
    });
    this.accountForm.controls.password.valueChanges.subscribe(v => this.pwd.set(v));
  }

  stepNumber(): number { return { code: 1, totp: 2, account: 3, codes: 3, done: 3 }[this.step()]; }

  start(): void {
    if (this.codeForm.invalid) { this.codeForm.markAllAsTouched(); return; }
    this.run(this.http.post<Enrollment>('/api/setup/start', this.codeForm.getRawValue()), e => { this.enrollment.set(e); this.step.set('totp'); });
  }

  regenerate(): void {
    this.run(this.http.post<Enrollment>('/api/setup/totp/regenerate', { setupToken: this.enrollment()!.setupToken }), e => this.enrollment.set(e));
  }

  verify(): void {
    this.run(this.http.post('/api/setup/totp/verify', { setupToken: this.enrollment()!.setupToken, code: this.totpForm.getRawValue().code }),
      () => this.step.set('account'));
  }

  complete(): void {
    this.run(this.http.post<TokenResponse>('/api/setup/complete', { setupToken: this.enrollment()!.setupToken, ...this.accountForm.getRawValue() },
      { withCredentials: true }), t => { this.tokens = t; this.codes.set(t.recoveryCodes ?? []); this.step.set('codes'); });
  }

  finish(): void {
    if (!this.tokens) return;
    this.loading.set(true);
    this.auth.establish(this.tokens).subscribe({ next: () => this.router.navigate(['/plataforma']), error: e => { this.loading.set(false); this.error.set(apiMessage(e)); } });
  }

  async copy(text: string): Promise<void> {
    try { await navigator.clipboard.writeText(text.replace(/\s/g, '')); this.copied.set(true); } catch { this.copied.set(false); }
  }

  private run<T>(obs: Observable<T>, ok: (v: T) => void): void {
    this.loading.set(true);
    this.error.set(null);
    obs.subscribe({
      next: v => { this.loading.set(false); ok(v); },
      error: e => {
        this.loading.set(false);
        this.error.set(apiMessage(e));
        this.totpForm.reset();
        if (apiCode(e) === 'SETUP_SESSION_EXPIRED') this.step.set('code');
      },
    });
  }
}
