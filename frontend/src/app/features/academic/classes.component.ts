import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { filter } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ClassView, CourseView, SHIFTS, TermView } from '../../core/academic.models';
import { Page } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { FacultyPickerComponent } from '../../shared/faculty-picker.component';
import { AcademicApi } from './academic-api.service';
import { ClassFormComponent } from './class-form.component';

@Component({
  selector: 'app-classes',
  imports: [FormsModule, RouterLink, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatSelectModule, FacultyPickerComponent],
  template: `
    <header class="page-header"><div><h1>Turmas</h1><p class="muted">Turmas por período letivo, disciplinas ofertadas, professores, horários e alunos.</p></div>
      @if (canManage) { <button mat-flat-button (click)="create()" [disabled]="api.needsFaculty()"><mat-icon>add</mat-icon> Nova turma</button> }</header>
    <section class="filters">
      <app-faculty-picker [(facultyId)]="api.selected.id" />
      <mat-form-field><mat-label>Período letivo</mat-label><mat-select [(ngModel)]="termId" (selectionChange)="load()">
        <mat-option [value]="null">Todos</mat-option>@for (t of terms(); track t.id) { <mat-option [value]="t.id">{{ t.code }}</mat-option> }</mat-select></mat-form-field>
      <mat-form-field><mat-label>Curso</mat-label><mat-select [(ngModel)]="courseId" (selectionChange)="load()">
        <mat-option [value]="null">Todos</mat-option>@for (c of courses(); track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }</mat-select></mat-form-field>
    </section>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <table class="data-table responsive">
      <caption class="sr-only">Turmas</caption>
      <thead><tr><th scope="col">Turma</th><th scope="col">Curso</th><th scope="col">Período</th><th scope="col">Turno / sala</th><th scope="col">Alunos</th><th scope="col">Disciplinas</th><th scope="col">Situação</th></tr></thead>
      <tbody>@for (c of classes(); track c.id) {
        <tr>
          <td data-label="Turma"><a [routerLink]="['/academico/turmas', c.id]"><strong>{{ c.code }}</strong></a><br><span class="muted small">{{ c.semester }}º semestre</span></td>
          <td data-label="Curso">{{ c.courseName }}</td><td data-label="Período">{{ c.termCode }}</td>
          <td data-label="Turno / sala">{{ shift(c) }}{{ c.room ? ' · sala ' + c.room : '' }}</td>
          <td data-label="Alunos">{{ c.students }} / {{ c.capacity }}</td><td data-label="Disciplinas">{{ c.subjects }}</td>
          <td data-label="Situação"><span class="badge" [class]="c.active ? 'active' : 'inactive'">{{ c.active ? 'Ativa' : 'Inativa' }}</span></td>
        </tr>
      } @empty { <tr><td colspan="7" class="muted">{{ api.needsFaculty() ? 'Selecione uma faculdade.' : 'Nenhuma turma encontrada.' }}</td></tr> }</tbody>
    </table>
  `,
})
export class ClassesComponent {
  readonly api = inject(AcademicApi);
  private dialog = inject(MatDialog);
  readonly canManage = inject(AuthService).hasPermission('COURSE_MANAGE');
  readonly classes = signal<ClassView[]>([]);
  readonly terms = signal<TermView[]>([]);
  readonly courses = signal<CourseView[]>([]);
  readonly error = signal<string | null>(null);
  termId: string | null = null;
  courseId: string | null = null;

  constructor() {
    effect(() => {
      this.api.selected.id();
      if (this.api.needsFaculty()) { this.classes.set([]); return; }
      this.api.get<TermView[]>('/terms').subscribe(t => this.terms.set(t));
      this.api.get<Page<CourseView>>('/courses', { size: 200 }).subscribe(p => this.courses.set(p.content));
      this.load();
    });
  }

  shift(c: ClassView): string { return SHIFTS[c.shift]; }

  load(): void {
    if (this.api.needsFaculty()) return;
    this.api.get<ClassView[]>('/classes', { termId: this.termId, courseId: this.courseId })
      .subscribe({ next: c => { this.error.set(null); this.classes.set(c); }, error: e => this.error.set(apiMessage(e)) });
  }

  create(): void {
    this.dialog.open(ClassFormComponent, { data: null, width: '720px', maxWidth: '96vw' }).afterClosed().pipe(filter(Boolean)).subscribe(() => this.load());
  }
}
