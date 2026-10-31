import { Component, effect, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { filter, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { CourseView, DEGREES, MODALITIES } from '../../core/academic.models';
import { Page } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { FacultyPickerComponent } from '../../shared/faculty-picker.component';
import { AcademicApi } from './academic-api.service';
import { CourseFormComponent } from './course-form.component';

@Component({
  selector: 'app-courses',
  imports: [CurrencyPipe, FormsModule, RouterLink, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatMenuModule, MatSelectModule, MatSnackBarModule, FacultyPickerComponent],
  template: `
    <header class="page-header"><div><h1>Cursos</h1><p class="muted">Cursos oferecidos, duração, mensalidade e matriz curricular.</p></div>
      @if (canManage) { <button mat-flat-button (click)="edit(null)" [disabled]="api.needsFaculty()"><mat-icon>add</mat-icon> Novo curso</button> }</header>
    <section class="filters">
      <app-faculty-picker [(facultyId)]="api.selected.id" />
      <mat-form-field><mat-label>Buscar (nome ou código)</mat-label><input matInput [(ngModel)]="q" (keyup.enter)="load()"><mat-icon matSuffix>search</mat-icon></mat-form-field>
      <mat-form-field><mat-label>Situação</mat-label><mat-select [(ngModel)]="active" (selectionChange)="load()">
        <mat-option [value]="null">Todos</mat-option><mat-option [value]="true">Ativos</mat-option><mat-option [value]="false">Inativos</mat-option></mat-select></mat-form-field>
      <button mat-stroked-button (click)="load()">Filtrar</button>
    </section>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <table class="data-table responsive">
      <caption class="sr-only">Cursos</caption>
      <thead><tr><th scope="col">Curso</th><th scope="col">Grau / modalidade</th><th scope="col">Duração</th><th scope="col">Mensalidade</th><th scope="col">Coordenação</th><th scope="col">Matriz</th><th scope="col">Situação</th><th scope="col"><span class="sr-only">Ações</span></th></tr></thead>
      <tbody>
        @for (c of courses(); track c.id) {
          <tr>
            <td data-label="Curso"><a [routerLink]="['/academico/cursos', c.id]"><strong>{{ c.name }}</strong></a><br><span class="muted small">{{ c.code }}</span></td>
            <td data-label="Grau">{{ degree(c) }} · {{ modality(c) }}</td>
            <td data-label="Duração">{{ c.durationSemesters }} semestres</td>
            <td data-label="Mensalidade">{{ c.monthlyFee | currency:'BRL' }}</td>
            <td data-label="Coordenação">{{ c.coordinatorName ?? '—' }}</td>
            <td data-label="Matriz">{{ c.curriculumSubjects }} disciplina(s) · {{ c.classes }} turma(s)</td>
            <td data-label="Situação"><span class="badge" [class]="c.active ? 'active' : 'inactive'">{{ c.active ? 'Ativo' : 'Inativo' }}</span></td>
            <td class="actions">
              <button mat-icon-button [matMenuTriggerFor]="m" [attr.aria-label]="'Ações para ' + c.name"><mat-icon>more_vert</mat-icon></button>
              <mat-menu #m="matMenu">
                <a mat-menu-item [routerLink]="['/academico/cursos', c.id]"><mat-icon>account_tree</mat-icon> Matriz curricular</a>
                @if (canManage) {
                  <button mat-menu-item (click)="edit(c)"><mat-icon>edit</mat-icon> Editar</button>
                  <button mat-menu-item (click)="remove(c)"><mat-icon>delete</mat-icon> Excluir</button>
                }
              </mat-menu>
            </td>
          </tr>
        } @empty { <tr><td colspan="8" class="muted">{{ api.needsFaculty() ? 'Selecione uma faculdade.' : 'Nenhum curso cadastrado.' }}</td></tr> }
      </tbody>
    </table>
  `,
})
export class CoursesComponent {
  readonly api = inject(AcademicApi);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  readonly canManage = inject(AuthService).hasPermission('COURSE_MANAGE');
  readonly courses = signal<CourseView[]>([]);
  readonly error = signal<string | null>(null);
  q = '';
  active: boolean | null = null;

  constructor() { effect(() => { this.api.selected.id(); this.load(); }); }

  degree(c: CourseView): string { return DEGREES[c.degree]; }
  modality(c: CourseView): string { return MODALITIES[c.modality]; }

  load(): void {
    if (this.api.needsFaculty()) { this.courses.set([]); return; }
    this.api.get<Page<CourseView>>('/courses', { q: this.q.trim(), active: this.active, size: 200 })
      .subscribe({ next: p => { this.error.set(null); this.courses.set(p.content); }, error: e => this.error.set(apiMessage(e)) });
  }

  edit(c: CourseView | null): void {
    this.dialog.open(CourseFormComponent, { data: c, width: '720px', maxWidth: '96vw' }).afterClosed().pipe(filter(Boolean))
      .subscribe(() => { this.snack.open('Curso salvo.', 'OK', { duration: 3000 }); this.load(); });
  }

  remove(c: CourseView): void {
    this.dialog.open(ConfirmDialogComponent, { data: { title: 'Excluir curso', danger: true, confirmLabel: 'Excluir',
      message: `Excluir "${c.name}"? Cursos com turmas ou matrículas não podem ser excluídos — desative-os.` } })
      .afterClosed().pipe(filter(Boolean), switchMap(() => this.api.delete(`/courses/${c.id}`)))
      .subscribe({ next: () => this.load(), error: e => this.error.set(apiMessage(e)) });
  }
}
