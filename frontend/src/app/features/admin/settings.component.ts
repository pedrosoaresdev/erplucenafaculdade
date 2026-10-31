import { Component, effect, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { apiMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { SelectedFacultyService } from '../../core/selected-faculty.service';
import { FacultyPickerComponent, facultyParam } from '../../shared/faculty-picker.component';

@Component({
  selector: 'app-settings',
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSnackBarModule, FacultyPickerComponent],
  template: `
    <header class="page-header"><div><h1>Configurações da instituição</h1><p class="muted">Parâmetros acadêmicos, financeiros e de segurança. Alterações são auditadas.</p></div></header>
    <section class="filters"><app-faculty-picker [(facultyId)]="facultyId" /></section>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <mat-card class="narrow-card"><mat-card-content>
      <form [formGroup]="form" (ngSubmit)="save()" class="form-grid">
        <mat-form-field><mat-label>Cor institucional</mat-label><input matInput formControlName="primaryColor"><mat-hint>Formato #RRGGBB</mat-hint></mat-form-field>
        <mat-form-field><mat-label>Nota mínima para aprovação</mat-label><input matInput type="number" step="0.1" min="0" max="10" formControlName="academicMinGrade"></mat-form-field>
        <mat-form-field><mat-label>Frequência mínima (%)</mat-label><input matInput type="number" min="0" max="100" formControlName="academicMinAttendancePct"></mat-form-field>
        <mat-form-field><mat-label>Multa por atraso (%)</mat-label><input matInput type="number" step="0.01" min="0" max="100" formControlName="lateFeePct">
          <mat-hint>Confirme o percentual com o jurídico/contábil</mat-hint></mat-form-field>
        <mat-form-field><mat-label>Juros ao mês (%)</mat-label><input matInput type="number" step="0.01" min="0" max="100" formControlName="monthlyInterestPct"></mat-form-field>
        <mat-form-field><mat-label>Tempo de sessão inativa (min)</mat-label><input matInput type="number" min="5" max="1440" formControlName="sessionTimeoutMinutes"></mat-form-field>
        <mat-form-field><mat-label>Retenção de dados (anos)</mat-label><input matInput type="number" min="1" max="100" formControlName="dataRetentionYears"></mat-form-field>
        <div class="span-2"><button mat-flat-button type="submit" [disabled]="form.invalid || !loaded()">Salvar configurações</button></div>
      </form>
    </mat-card-content></mat-card>
  `,
})
export class SettingsComponent {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);
  private auth = inject(AuthService);
  readonly facultyId = inject(SelectedFacultyService).id;
  readonly error = signal<string | null>(null);
  readonly loaded = signal(false);
  readonly form = inject(FormBuilder).nonNullable.group({
    primaryColor: ['#0B2545', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    academicMinGrade: [6, [Validators.required, Validators.min(0), Validators.max(10)]],
    academicMinAttendancePct: [75, [Validators.required, Validators.min(0), Validators.max(100)]],
    lateFeePct: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
    monthlyInterestPct: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
    sessionTimeoutMinutes: [30, [Validators.required, Validators.min(5), Validators.max(1440)]],
    dataRetentionYears: [20, [Validators.required, Validators.min(1), Validators.max(100)]],
  });

  constructor() {
    effect(() => {
      const id = this.facultyId();
      if (this.auth.profile()?.platformAdmin && !id) return;
      this.http.get<typeof this.form.value>('/api/admin/faculty/settings', { params: facultyParam(id) })
        .subscribe({ next: s => { this.form.patchValue(s); this.loaded.set(true); }, error: e => this.error.set(apiMessage(e)) });
    });
  }

  save(): void {
    this.http.put('/api/admin/faculty/settings', this.form.getRawValue(), { params: facultyParam(this.facultyId()) })
      .subscribe({ next: () => this.snack.open('Configurações salvas.', 'OK', { duration: 3000 }), error: e => this.error.set(apiMessage(e)) });
  }
}
