import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ClassDetail, ClassSubjectView, CurriculumSummary, TeacherView, WEEKDAYS } from '../../core/academic.models';
import { Page } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { AcademicApi } from './academic-api.service';

/** Oferta de disciplina na turma: professor, dia e horário (com verificação de choques no servidor). */
@Component({
  selector: 'app-class-subject-form',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>{{ data.offer ? 'Editar disciplina da turma' : 'Ofertar disciplina' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content>
        @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
        <mat-form-field class="full"><mat-label>Disciplina (da matriz do curso)</mat-label>
          <mat-select formControlName="subjectId" required>
            @for (i of options(); track i.subjectId) { <mat-option [value]="i.subjectId">{{ i.semester }}º · {{ i.subjectName }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field class="full"><mat-label>Professor</mat-label><mat-select formControlName="teacherId">
          <mat-option [value]="null">A definir</mat-option>
          @for (t of teachers(); track t.id) { <mat-option [value]="t.id">{{ t.fullName }}</mat-option> }</mat-select></mat-form-field>
        <div class="form-grid three">
          <mat-form-field><mat-label>Dia da semana</mat-label><mat-select formControlName="weekday">
            <mat-option [value]="null">—</mat-option>
            @for (d of days; track $index) { @if ($index > 0) { <mat-option [value]="$index">{{ d }}</mat-option> } }</mat-select></mat-form-field>
          <mat-form-field><mat-label>Início</mat-label><input matInput type="time" formControlName="startTime"></mat-form-field>
          <mat-form-field><mat-label>Término</mat-label><input matInput type="time" formControlName="endTime"></mat-form-field>
        </div>
        <mat-form-field class="full"><mat-label>Plano de ensino</mat-label><textarea matInput rows="4" formControlName="teachingPlan"></textarea></mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="form.invalid || loading()">Salvar</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class ClassSubjectFormComponent implements OnInit {
  readonly data = inject<{ detail: ClassDetail; offer?: ClassSubjectView }>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<ClassSubjectFormComponent>);
  private api = inject(AcademicApi);
  readonly days = WEEKDAYS;
  readonly options = signal<{ subjectId: string; subjectName: string; semester: number }[]>([]);
  readonly teachers = signal<TeacherView[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).group({
    subjectId: [null as string | null, Validators.required], teacherId: [null as string | null],
    weekday: [null as number | null], startTime: [''], endTime: [''], teachingPlan: [''],
  });

  ngOnInit(): void {
    const turma = this.data.detail.turma;
    const offered = new Set(this.data.detail.subjects.map(s => s.subjectId));
    this.api.get<CurriculumSummary>(`/courses/${turma.courseId}/curriculum`).subscribe(c => this.options.set(
      c.items.filter(i => !offered.has(i.subjectId) || i.subjectId === this.data.offer?.subjectId)
        .sort((a, b) => Math.abs(a.semester - turma.semester) - Math.abs(b.semester - turma.semester))));
    this.api.get<Page<TeacherView>>('/teachers', { status: 'ACTIVE', size: 500 }).subscribe(p => this.teachers.set(p.content));
    const o = this.data.offer;
    if (o) {
      this.form.patchValue({ subjectId: o.subjectId, teacherId: o.teacherId ?? null, weekday: o.weekday ?? null,
        startTime: o.startTime?.slice(0, 5) ?? '', endTime: o.endTime?.slice(0, 5) ?? '', teachingPlan: o.teachingPlan ?? '' });
      this.form.controls.subjectId.disable();
    }
  }

  save(): void {
    const v = this.form.getRawValue();
    const body = { ...v, startTime: v.startTime || null, endTime: v.endTime || null, teachingPlan: v.teachingPlan || null };
    const base = `/classes/${this.data.detail.turma.id}/subjects`;
    this.loading.set(true);
    const req = this.data.offer ? this.api.put(`${base}/${this.data.offer.id}`, body) : this.api.post(base, body);
    req.subscribe({ next: r => this.ref.close(r), error: e => { this.loading.set(false); this.error.set(apiMessage(e)); } });
  }
}
