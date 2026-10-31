import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../core/auth.service';
import { CurriculumSummary, CurriculumView, SubjectView } from '../../core/academic.models';
import { Page } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { AcademicApi } from './academic-api.service';

/** Matriz curricular do curso, agrupada por semestre, com pré-requisitos. */
@Component({
  selector: 'app-curriculum',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatSelectModule, MatSnackBarModule],
  template: `
    <header class="page-header"><div>
      <a routerLink="/academico/cursos" class="back-link"><span aria-hidden="true">←</span> Cursos</a>
      <h1>Matriz curricular{{ data() ? ': ' + data()!.courseName : '' }}</h1>
      @if (data(); as d) { <p class="muted">{{ d.durationSemesters }} semestres · {{ d.items.length }} disciplinas · {{ d.totalHours }} h · {{ d.totalCredits }} créditos</p> }
    </div></header>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <div class="curriculum-layout">
      <section>
        @for (sem of semesters(); track sem.n) {
          <h2 class="section-title">{{ sem.n }}º semestre <span class="muted small">({{ sem.hours }} h)</span></h2>
          <table class="data-table responsive">
            <thead><tr><th scope="col">Disciplina</th><th scope="col">Carga</th><th scope="col">Créditos</th><th scope="col">Tipo</th><th scope="col">Pré-requisito</th><th scope="col"><span class="sr-only">Ações</span></th></tr></thead>
            <tbody>@for (i of sem.items; track i.id) {
              <tr><td data-label="Disciplina"><strong>{{ i.subjectName }}</strong> <span class="muted small">{{ i.subjectCode }}</span></td>
                <td data-label="Carga">{{ i.workloadHours }} h</td><td data-label="Créditos">{{ i.credits }}</td>
                <td data-label="Tipo">{{ i.mandatory ? 'Obrigatória' : 'Optativa' }}</td>
                <td data-label="Pré-requisito">{{ i.prerequisiteName ?? '—' }}</td>
                <td class="actions">@if (canManage) {
                  <button mat-icon-button (click)="startEdit(i)" [attr.aria-label]="'Editar ' + i.subjectName"><mat-icon>edit</mat-icon></button>
                  <button mat-icon-button (click)="remove(i)" [attr.aria-label]="'Remover ' + i.subjectName"><mat-icon>delete</mat-icon></button> }</td></tr>
            }</tbody>
          </table>
        } @empty { <p class="muted">Nenhuma disciplina na matriz.</p> }
      </section>
      @if (canManage) {
        <mat-card class="sticky-card">
          <mat-card-header><mat-card-title>{{ editing() ? 'Editar item' : 'Adicionar disciplina' }}</mat-card-title></mat-card-header>
          <mat-card-content>
            <form [formGroup]="form" (ngSubmit)="save()">
              <mat-form-field class="full"><mat-label>Disciplina</mat-label>
                <mat-select formControlName="subjectId" required>
                  @for (s of available(); track s.id) { <mat-option [value]="s.id">{{ s.code }} — {{ s.name }}</mat-option> }
                </mat-select>
                @if (!subjects().length) { <mat-hint>Cadastre disciplinas em Acadêmico › Disciplinas</mat-hint> }</mat-form-field>
              <mat-form-field class="full"><mat-label>Semestre</mat-label>
                <mat-select formControlName="semester" required>
                  @for (n of semesterNumbers(); track n) { <mat-option [value]="n">{{ n }}º semestre</mat-option> }</mat-select></mat-form-field>
              <mat-form-field class="full"><mat-label>Pré-requisito</mat-label>
                <mat-select formControlName="prerequisiteSubjectId"><mat-option [value]="null">Nenhum</mat-option>
                  @for (p of prerequisiteOptions(); track p.subjectId) { <mat-option [value]="p.subjectId">{{ p.subjectName }} ({{ p.semester }}º)</mat-option> }
                </mat-select></mat-form-field>
              <mat-checkbox formControlName="mandatory">Obrigatória</mat-checkbox>
              <div class="row-actions mt"><button mat-flat-button type="submit" [disabled]="form.invalid">{{ editing() ? 'Salvar' : 'Adicionar' }}</button>
                @if (editing()) { <button mat-button type="button" (click)="reset()">Cancelar</button> }</div>
            </form>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
})
export class CurriculumComponent implements OnInit {
  readonly id = input.required<string>();
  private api = inject(AcademicApi);
  private snack = inject(MatSnackBar);
  readonly canManage = inject(AuthService).hasPermission('COURSE_MANAGE');
  readonly data = signal<CurriculumSummary | null>(null);
  readonly subjects = signal<SubjectView[]>([]);
  readonly editing = signal<CurriculumView | null>(null);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).group({
    subjectId: [null as string | null, Validators.required], semester: [1, Validators.required],
    prerequisiteSubjectId: [null as string | null], mandatory: [true],
  });
  private semesterValue = signal(1);

  readonly semesters = computed(() => {
    const d = this.data();
    if (!d) return [];
    const map = new Map<number, CurriculumView[]>();
    d.items.forEach(i => map.set(i.semester, [...(map.get(i.semester) ?? []), i]));
    return [...map.entries()].sort((a, b) => a[0] - b[0]).map(([n, items]) => ({ n, items, hours: items.reduce((s, i) => s + i.workloadHours, 0) }));
  });
  readonly semesterNumbers = computed(() => Array.from({ length: this.data()?.durationSemesters ?? 0 }, (_, i) => i + 1));
  readonly available = computed(() => {
    const used = new Set((this.data()?.items ?? []).map(i => i.subjectId));
    const e = this.editing();
    return this.subjects().filter(s => (s.active && !used.has(s.id)) || s.id === e?.subjectId);
  });
  readonly prerequisiteOptions = computed(() =>
    (this.data()?.items ?? []).filter(i => i.semester < this.semesterValue() && i.subjectId !== this.editing()?.subjectId));

  ngOnInit(): void {
    this.form.controls.semester.valueChanges.subscribe(v => this.semesterValue.set(v ?? 1));
    this.load();
    this.api.get<Page<SubjectView>>('/subjects', { size: 500 }).subscribe(p => this.subjects.set(p.content));
  }

  load(): void {
    this.api.get<CurriculumSummary>(`/courses/${this.id()}/curriculum`).subscribe({ next: d => this.data.set(d), error: e => this.error.set(apiMessage(e)) });
  }

  startEdit(i: CurriculumView): void {
    this.editing.set(i);
    this.form.patchValue({ subjectId: i.subjectId, semester: i.semester, prerequisiteSubjectId: i.prerequisiteSubjectId ?? null, mandatory: i.mandatory });
    this.form.controls.subjectId.disable();
  }

  reset(): void {
    this.editing.set(null);
    this.form.controls.subjectId.enable();
    this.form.reset({ subjectId: null, semester: 1, prerequisiteSubjectId: null, mandatory: true });
  }

  save(): void {
    const v = this.form.getRawValue();
    const e = this.editing();
    const req = e ? this.api.put<CurriculumSummary>(`/courses/${this.id()}/curriculum/${e.id}`, v) : this.api.post<CurriculumSummary>(`/courses/${this.id()}/curriculum`, v);
    req.subscribe({ next: d => { this.data.set(d); this.error.set(null); this.reset(); this.snack.open('Matriz atualizada.', 'OK', { duration: 2500 }); },
      error: err => this.error.set(apiMessage(err)) });
  }

  remove(i: CurriculumView): void {
    this.api.delete<CurriculumSummary>(`/courses/${this.id()}/curriculum/${i.id}`)
      .subscribe({ next: d => { this.data.set(d); this.error.set(null); }, error: e => this.error.set(apiMessage(e)) });
  }
}
