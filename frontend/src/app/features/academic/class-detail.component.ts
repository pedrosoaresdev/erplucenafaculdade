import { Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { debounceTime, filter, of, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ClassDetail, ClassStudentView, ClassSubjectView, SHIFTS, STUDENT_STATUS, StudentView, WEEKDAYS } from '../../core/academic.models';
import { Page } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { AcademicApi } from './academic-api.service';
import { ClassFormComponent } from './class-form.component';
import { ClassSubjectFormComponent } from './class-subject-form.component';

@Component({
  selector: 'app-class-detail',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MatAutocompleteModule, MatButtonModule, MatCardModule, MatDialogModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSnackBarModule],
  template: `
    @if (d(); as d) {
      <header class="page-header"><div>
        <a routerLink="/academico/turmas" class="back-link"><span aria-hidden="true">←</span> Turmas</a>
        <h1>Turma {{ d.turma.code }}</h1>
        <p class="muted">{{ d.turma.courseName }} · {{ d.turma.semester }}º semestre · {{ d.turma.termCode }} · {{ shift(d) }}{{ d.turma.room ? ' · sala ' + d.turma.room : '' }}</p>
      </div>
        @if (canManage) { <div class="row-actions">
          <button mat-stroked-button (click)="edit()"><mat-icon>edit</mat-icon> Editar turma</button>
          <button mat-stroked-button class="danger-text" (click)="remove()"><mat-icon>delete</mat-icon> Excluir</button></div> }
      </header>
      @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }

      <section class="card-grid">
        <mat-card class="stat-card"><mat-icon aria-hidden="true">groups</mat-icon><div><span class="stat-label">Alunos</span><strong class="stat-value">{{ d.turma.students }} / {{ d.turma.capacity }}</strong></div></mat-card>
        <mat-card class="stat-card"><mat-icon aria-hidden="true">menu_book</mat-icon><div><span class="stat-label">Disciplinas</span><strong class="stat-value">{{ d.subjects.length }}</strong></div></mat-card>
        <mat-card class="stat-card"><mat-icon aria-hidden="true">schedule</mat-icon><div><span class="stat-label">Carga semanal</span><strong class="stat-value">{{ weeklyHours(d) }}</strong></div></mat-card>
      </section>

      <div class="toolbar-row"><h2 class="section-title">Disciplinas e quadro de horários</h2>
        @if (canManage) { <button mat-flat-button (click)="offer()"><mat-icon>add</mat-icon> Ofertar disciplina</button> }</div>
      <table class="data-table responsive">
        <thead><tr><th scope="col">Disciplina</th><th scope="col">Professor</th><th scope="col">Dia</th><th scope="col">Horário</th><th scope="col"><span class="sr-only">Ações</span></th></tr></thead>
        <tbody>@for (s of d.subjects; track s.id) {
          <tr><td data-label="Disciplina"><strong>{{ s.subjectName }}</strong> <span class="muted small">{{ s.subjectCode }} · {{ s.workloadHours }} h</span></td>
            <td data-label="Professor">{{ s.teacherName ?? 'A definir' }}</td>
            <td data-label="Dia">{{ s.weekday ? days[s.weekday] : '—' }}</td>
            <td data-label="Horário">{{ s.startTime ? s.startTime.slice(0,5) + ' – ' + s.endTime!.slice(0,5) : '—' }}</td>
            <td class="actions">@if (canManage) {
              <button mat-icon-button (click)="offer(s)" [attr.aria-label]="'Editar ' + s.subjectName"><mat-icon>edit</mat-icon></button>
              <button mat-icon-button (click)="removeOffer(s)" [attr.aria-label]="'Remover ' + s.subjectName"><mat-icon>delete</mat-icon></button> }</td></tr>
        } @empty { <tr><td colspan="5" class="muted">Nenhuma disciplina ofertada.</td></tr> }</tbody>
      </table>

      <div class="toolbar-row"><h2 class="section-title">Alunos da turma</h2></div>
      @if (canEnroll) {
        <mat-form-field class="student-search">
          <mat-label>Incluir aluno (nome, matrícula ou CPF)</mat-label>
          <input matInput [formControl]="search" [matAutocomplete]="auto">
          <mat-icon matSuffix>person_add</mat-icon>
          <mat-autocomplete #auto="matAutocomplete" (optionSelected)="addStudent($event.option.value)">
            @for (s of results(); track s.id) { <mat-option [value]="s">{{ s.registration }} — {{ s.socialName || s.fullName }}</mat-option> }
          </mat-autocomplete>
        </mat-form-field>
      }
      <table class="data-table responsive">
        <thead><tr><th scope="col">Matrícula</th><th scope="col">Nome</th><th scope="col">Situação</th><th scope="col">Inclusão</th><th scope="col"><span class="sr-only">Ações</span></th></tr></thead>
        <tbody>@for (s of d.students; track s.studentId) {
          <tr><td data-label="Matrícula">{{ s.registration }}</td><td data-label="Nome">{{ s.name }}</td>
            <td data-label="Situação"><span class="badge" [class]="s.status === 'ACTIVE' ? 'active' : 'warn'">{{ status(s) }}</span></td>
            <td data-label="Inclusão">{{ s.joinedAt | date:'dd/MM/yyyy' }}</td>
            <td class="actions">@if (canEnroll) { <button mat-icon-button (click)="removeStudent(s)" [attr.aria-label]="'Remover ' + s.name"><mat-icon>person_remove</mat-icon></button> }</td></tr>
        } @empty { <tr><td colspan="5" class="muted">Nenhum aluno na turma.</td></tr> }</tbody>
      </table>
    } @else if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
  `,
})
export class ClassDetailComponent implements OnInit {
  readonly id = input.required<string>();
  private api = inject(AcademicApi);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private router = inject(Router);
  private auth = inject(AuthService);
  readonly canManage = this.auth.hasPermission('COURSE_MANAGE');
  readonly canEnroll = this.auth.hasPermission('COURSE_MANAGE', 'ENROLLMENT_MANAGE');
  readonly days = WEEKDAYS;
  readonly d = signal<ClassDetail | null>(null);
  readonly error = signal<string | null>(null);
  readonly results = signal<StudentView[]>([]);
  readonly search = new FormControl<string | StudentView>('');

  ngOnInit(): void {
    this.load();
    this.search.valueChanges.pipe(debounceTime(250), switchMap(v => typeof v === 'string' && v.trim().length >= 2
      ? this.api.get<Page<StudentView>>('/students', { q: v.trim(), status: 'ACTIVE', size: 10 }) : of(null)))
      .subscribe(p => this.results.set(p?.content ?? []));
  }

  shift(d: ClassDetail): string { return SHIFTS[d.turma.shift]; }
  status(s: ClassStudentView): string { return STUDENT_STATUS[s.status]; }

  weeklyHours(d: ClassDetail): string {
    const min = d.subjects.reduce((acc, s) => {
      if (!s.startTime || !s.endTime) return acc;
      const [h1, m1] = s.startTime.split(':').map(Number), [h2, m2] = s.endTime.split(':').map(Number);
      return acc + (h2 * 60 + m2) - (h1 * 60 + m1);
    }, 0);
    return `${Math.floor(min / 60)}h${String(min % 60).padStart(2, '0')}`;
  }

  load(): void {
    this.api.get<ClassDetail>(`/classes/${this.id()}`).subscribe({ next: d => this.d.set(d), error: e => this.error.set(apiMessage(e)) });
  }

  private apply = { next: (d: ClassDetail) => { this.d.set(d); this.error.set(null); }, error: (e: unknown) => this.error.set(apiMessage(e)) };

  edit(): void {
    this.dialog.open(ClassFormComponent, { data: this.d()!.turma, width: '720px', maxWidth: '96vw' }).afterClosed().pipe(filter(Boolean)).subscribe(() => this.load());
  }

  remove(): void {
    this.dialog.open(ConfirmDialogComponent, { data: { title: 'Excluir turma', danger: true, confirmLabel: 'Excluir',
      message: 'Somente turmas sem alunos e sem registros acadêmicos podem ser excluídas.' } })
      .afterClosed().pipe(filter(Boolean), switchMap(() => this.api.delete(`/classes/${this.id()}`)))
      .subscribe({ next: () => this.router.navigate(['/academico/turmas']), error: e => this.error.set(apiMessage(e)) });
  }

  offer(o?: ClassSubjectView): void {
    this.dialog.open(ClassSubjectFormComponent, { data: { detail: this.d(), offer: o }, width: '640px', maxWidth: '96vw' })
      .afterClosed().pipe(filter(Boolean)).subscribe((d: ClassDetail) => { this.d.set(d); this.snack.open('Disciplina salva.', 'OK', { duration: 2500 }); });
  }

  removeOffer(o: ClassSubjectView): void {
    this.api.delete<ClassDetail>(`/classes/${this.id()}/subjects/${o.id}`).subscribe(this.apply);
  }

  addStudent(s: StudentView): void {
    this.search.setValue('');
    this.api.post<ClassDetail>(`/classes/${this.id()}/students`, { studentId: s.id }).subscribe(this.apply);
  }

  removeStudent(s: ClassStudentView): void {
    this.api.delete<ClassDetail>(`/classes/${this.id()}/students/${s.studentId}`).subscribe(this.apply);
  }
}
