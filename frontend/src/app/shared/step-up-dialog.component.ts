import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { apiMessage } from '../core/api-error';

export type StepUpPurpose = 'PASSWORD_CHANGE' | 'MFA_ROTATE' | 'MFA_DISABLE' | 'RECOVERY_CODES' | 'PROFILE_SENSITIVE';

/** Reautenticação (senha atual + código do Google Authenticator). Fecha com o token de uso único. */
@Component({
  selector: 'app-step-up-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Confirme sua identidade</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content>
        <p>{{ data.reason }}</p>
        @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
        <mat-form-field class="full"><mat-label>Senha atual</mat-label>
          <input matInput type="password" formControlName="currentPassword" autocomplete="current-password" required></mat-form-field>
        <mat-form-field class="full"><mat-label>Código do Google Authenticator</mat-label>
          <input matInput formControlName="totpCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code" required></mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="form.invalid || loading()">Confirmar</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class StepUpDialogComponent {
  readonly data = inject<{ purpose: StepUpPurpose; reason: string }>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<StepUpDialogComponent, string>);
  private http = inject(HttpClient);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).nonNullable.group({
    currentPassword: ['', Validators.required],
    totpCode: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.post<{ stepUpToken: string }>('/api/me/step-up', { ...this.form.getRawValue(), purpose: this.data.purpose }).subscribe({
      next: r => this.ref.close(r.stepUpToken),
      error: e => { this.loading.set(false); this.error.set(apiMessage(e)); this.form.controls.totpCode.reset(); },
    });
  }
}
