import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { STUDENT_STATUS, StudentView } from '../../core/academic.models';
import { apiMessage } from '../../core/api-error';
import { AcademicApi } from './academic-api.service';

@Component({
  selector: 'app-student-status-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>Situação de {{ data.socialName || data.fullName }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content>
        @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
        <mat-form-field class="full"><mat-label>Nova situação</mat-label><mat-select formControlName="status" required>
          @for (s of statuses; track s[0]) { <mat-option [value]="s[0]" [disabled]="s[0] === data.status">{{ s[1] }}</mat-option> }</mat-select></mat-form-field>
        <mat-form-field class="full"><mat-label>Motivo</mat-label><textarea matInput rows="3" formControlName="reason" required></textarea>
          <mat-hint>Registrado na auditoria</mat-hint></mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="form.invalid || loading()">Confirmar</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class StudentStatusDialogComponent {
  readonly data = inject<StudentView>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<StudentStatusDialogComponent>);
  private api = inject(AcademicApi);
  readonly statuses = Object.entries(STUDENT_STATUS);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).nonNullable.group({ status: ['', Validators.required], reason: ['', [Validators.required, Validators.minLength(3)]] });

  save(): void {
    this.loading.set(true);
    this.api.post(`/students/${this.data.id}/status`, this.form.getRawValue())
      .subscribe({ next: r => this.ref.close(r), error: e => { this.loading.set(false); this.error.set(apiMessage(e)); } });
  }
}
