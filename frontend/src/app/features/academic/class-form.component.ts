import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ClassView, CourseView, SHIFTS, TermView } from '../../core/academic.models';
import { Page } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { AcademicApi } from './academic-api.service';

@Component({
  selector: 'app-class-form',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Editar turma' : 'Nova turma' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content class="form-grid">
        @if (error()) { <p class="alert error span-2" role="alert">{{ error() }}</p> }
        <mat-form-field><mat-label>Curso</mat-label><mat-select formControlName="courseId" required>
          @for (c of courses(); track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }</mat-select>
          @if (data) { <mat-hint>Não pode ser alterado</mat-hint> }</mat-form-field>
        <mat-form-field><mat-label>Período letivo</mat-label><mat-select formControlName="termId" required>
          @for (t of terms(); track t.id) { <mat-option [value]="t.id">{{ t.code }}</mat-option> }</mat-select></mat-form-field>
        <mat-form-field><mat-label>Código da turma</mat-label><input matInput formControlName="code" required></mat-form-field>
        <mat-form-field><mat-label>Semestre do curso</mat-label><input matInput type="number" min="1" max="20" formControlName="semester" required></mat-form-field>
        <mat-form-field><mat-label>Turno</mat-label><mat-select formControlName="shift" required>
          @for (s of shifts; track s[0]) { <mat-option [value]="s[0]">{{ s[1] }}</mat-option> }</mat-select></mat-form-field>
        <mat-form-field><mat-label>Capacidade</mat-label><input matInput type="number" min="1" formControlName="capacity" required></mat-form-field>
        <mat-form-field><mat-label>Sala</mat-label><input matInput formControlName="room"></mat-form-field>
        <mat-checkbox formControlName="active">Turma ativa</mat-checkbox>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="loading()">Salvar</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class ClassFormComponent implements OnInit {
  readonly data = inject<ClassView | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<ClassFormComponent>);
  private api = inject(AcademicApi);
  readonly shifts = Object.entries(SHIFTS);
  readonly courses = signal<CourseView[]>([]);
  readonly terms = signal<TermView[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).group({
    courseId: [null as string | null, Validators.required], termId: [null as string | null, Validators.required],
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{1,30}$/)]],
    semester: [1, [Validators.required, Validators.min(1)]], shift: ['NOITE', Validators.required],
    capacity: [40, [Validators.required, Validators.min(1)]], room: [''], active: [true],
  });

  ngOnInit(): void {
    this.api.get<Page<CourseView>>('/courses', { active: this.data ? null : true, size: 200 }).subscribe(p => this.courses.set(p.content));
    this.api.get<TermView[]>('/terms').subscribe(t => this.terms.set(this.data ? t : t.filter(x => x.status !== 'CLOSED')));
    if (this.data) {
      this.form.patchValue(this.data as never);
      this.form.controls.courseId.disable();
      this.form.controls.termId.disable();
    }
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    const body = this.form.getRawValue();
    const req = this.data ? this.api.put(`/classes/${this.data.id}`, body) : this.api.post('/classes', body);
    req.subscribe({ next: r => this.ref.close(r), error: e => { this.loading.set(false); this.error.set(apiMessage(e)); } });
  }
}
