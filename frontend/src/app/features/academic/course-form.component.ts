import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { CourseView, DEGREES, MODALITIES, TeacherView } from '../../core/academic.models';
import { Page } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { AcademicApi } from './academic-api.service';

@Component({
  selector: 'app-course-form',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Editar curso' : 'Novo curso' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content class="form-grid">
        @if (error()) { <p class="alert error span-2" role="alert">{{ error() }}</p> }
        <mat-form-field><mat-label>Código</mat-label><input matInput formControlName="code" required></mat-form-field>
        <mat-form-field><mat-label>Nome do curso</mat-label><input matInput formControlName="name" required></mat-form-field>
        <mat-form-field><mat-label>Grau</mat-label><mat-select formControlName="degree" required>
          @for (d of degrees; track d[0]) { <mat-option [value]="d[0]">{{ d[1] }}</mat-option> }</mat-select></mat-form-field>
        <mat-form-field><mat-label>Modalidade</mat-label><mat-select formControlName="modality" required>
          @for (m of modalities; track m[0]) { <mat-option [value]="m[0]">{{ m[1] }}</mat-option> }</mat-select></mat-form-field>
        <mat-form-field><mat-label>Duração (semestres)</mat-label><input matInput type="number" min="1" max="20" formControlName="durationSemesters" required></mat-form-field>
        <mat-form-field><mat-label>Carga horária total (h)</mat-label><input matInput type="number" min="1" formControlName="totalHours"></mat-form-field>
        <mat-form-field><mat-label>Mensalidade (R$)</mat-label><input matInput type="number" min="0" step="0.01" formControlName="monthlyFee" required></mat-form-field>
        <mat-form-field><mat-label>Coordenador</mat-label><mat-select formControlName="coordinatorId">
          <mat-option [value]="null">—</mat-option>
          @for (t of teachers(); track t.id) { <mat-option [value]="t.id">{{ t.fullName }}</mat-option> }</mat-select></mat-form-field>
        <mat-form-field class="span-2"><mat-label>Descrição</mat-label><textarea matInput rows="3" formControlName="description"></textarea></mat-form-field>
        <mat-checkbox formControlName="active">Curso ativo</mat-checkbox>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="loading()">Salvar</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class CourseFormComponent implements OnInit {
  readonly data = inject<CourseView | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<CourseFormComponent>);
  private api = inject(AcademicApi);
  readonly degrees = Object.entries(DEGREES);
  readonly modalities = Object.entries(MODALITIES);
  readonly teachers = signal<TeacherView[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{1,20}$/)]],
    name: ['', [Validators.required, Validators.maxLength(200)]],
    degree: ['BACHARELADO', Validators.required], modality: ['PRESENCIAL', Validators.required],
    durationSemesters: [8, [Validators.required, Validators.min(1), Validators.max(20)]],
    totalHours: [null as number | null, Validators.min(1)],
    monthlyFee: [0, [Validators.required, Validators.min(0)]],
    coordinatorId: [null as string | null], description: [''], active: [true],
  });

  ngOnInit(): void {
    this.api.get<Page<TeacherView>>('/teachers', { status: 'ACTIVE', size: 500 }).subscribe({ next: p => this.teachers.set(p.content), error: () => {} });
    if (this.data) this.form.patchValue(this.data as never);
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); this.error.set('Verifique os campos obrigatórios.'); return; }
    this.loading.set(true);
    const req = this.data ? this.api.put(`/courses/${this.data.id}`, this.form.getRawValue()) : this.api.post('/courses', this.form.getRawValue());
    req.subscribe({ next: r => this.ref.close(r), error: e => { this.loading.set(false); this.error.set(apiMessage(e)); } });
  }
}
