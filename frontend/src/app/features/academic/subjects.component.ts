import { Component, effect, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../core/auth.service';
import { SubjectView } from '../../core/academic.models';
import { Page } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { FacultyPickerComponent } from '../../shared/faculty-picker.component';
import { AcademicApi } from './academic-api.service';

@Component({
  selector: 'app-subjects',
  imports: [FormsModule, ReactiveFormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSnackBarModule, FacultyPickerComponent],
  template: `
    <header class="page-header"><div><h1>Disciplinas</h1><p class="muted">Catálogo de disciplinas usado nas matrizes curriculares e turmas.</p></div></header>
    <section class="filters">
      <app-faculty-picker [(facultyId)]="api.selected.id" />
      <mat-form-field><mat-label>Buscar (nome ou código)</mat-label><input matInput [(ngModel)]="q" (keyup.enter)="load()"><mat-icon matSuffix>search</mat-icon></mat-form-field>
      <button mat-stroked-button (click)="load()">Filtrar</button>
    </section>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <div class="two-col wide-left">
      <table class="data-table responsive">
        <caption class="sr-only">Disciplinas</caption>
        <thead><tr><th scope="col">Disciplina</th><th scope="col">Carga</th><th scope="col">Créditos</th><th scope="col">Situação</th><th scope="col"><span class="sr-only">Ações</span></th></tr></thead>
        <tbody>@for (s of subjects(); track s.id) {
          <tr><td data-label="Disciplina"><strong>{{ s.name }}</strong><br><span class="muted small">{{ s.code }}</span></td>
            <td data-label="Carga">{{ s.workloadHours }} h</td><td data-label="Créditos">{{ s.credits }}</td>
            <td data-label="Situação"><span class="badge" [class]="s.active ? 'active' : 'inactive'">{{ s.active ? 'Ativa' : 'Inativa' }}</span></td>
            <td class="actions">@if (canManage) {
              <button mat-icon-button (click)="edit(s)" [attr.aria-label]="'Editar ' + s.name"><mat-icon>edit</mat-icon></button>
              <button mat-icon-button (click)="remove(s)" [attr.aria-label]="'Excluir ' + s.name"><mat-icon>delete</mat-icon></button> }</td></tr>
        } @empty { <tr><td colspan="5" class="muted">{{ api.needsFaculty() ? 'Selecione uma faculdade.' : 'Nenhuma disciplina cadastrada.' }}</td></tr> }</tbody>
      </table>
      @if (canManage) {
        <mat-card>
          <mat-card-header><mat-card-title>{{ editing() ? 'Editar disciplina' : 'Nova disciplina' }}</mat-card-title></mat-card-header>
          <mat-card-content>
            <form [formGroup]="form" (ngSubmit)="save()">
              <mat-form-field class="full"><mat-label>Código</mat-label><input matInput formControlName="code" required></mat-form-field>
              <mat-form-field class="full"><mat-label>Nome</mat-label><input matInput formControlName="name" required></mat-form-field>
              <mat-form-field class="full"><mat-label>Carga horária (h)</mat-label><input matInput type="number" min="1" formControlName="workloadHours" required></mat-form-field>
              <mat-form-field class="full"><mat-label>Créditos</mat-label><input matInput type="number" min="0" formControlName="credits" required></mat-form-field>
              <mat-form-field class="full"><mat-label>Ementa</mat-label><textarea matInput rows="4" formControlName="syllabus"></textarea></mat-form-field>
              <mat-checkbox formControlName="active">Ativa</mat-checkbox>
              <div class="row-actions mt"><button mat-flat-button type="submit" [disabled]="form.invalid || api.needsFaculty()">Salvar</button>
                @if (editing()) { <button mat-button type="button" (click)="reset()">Cancelar</button> }</div>
            </form>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
})
export class SubjectsComponent {
  readonly api = inject(AcademicApi);
  private snack = inject(MatSnackBar);
  readonly canManage = inject(AuthService).hasPermission('COURSE_MANAGE');
  readonly subjects = signal<SubjectView[]>([]);
  readonly editing = signal<SubjectView | null>(null);
  readonly error = signal<string | null>(null);
  q = '';
  readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{1,20}$/)]], name: ['', Validators.required],
    workloadHours: [60, [Validators.required, Validators.min(1)]], credits: [4, [Validators.required, Validators.min(0)]],
    syllabus: [''], active: [true],
  });

  constructor() { effect(() => { this.api.selected.id(); this.load(); }); }

  load(): void {
    if (this.api.needsFaculty()) { this.subjects.set([]); return; }
    this.api.get<Page<SubjectView>>('/subjects', { q: this.q.trim(), size: 500 })
      .subscribe({ next: p => { this.error.set(null); this.subjects.set(p.content); }, error: e => this.error.set(apiMessage(e)) });
  }

  edit(s: SubjectView): void { this.editing.set(s); this.form.patchValue({ ...s, syllabus: s.syllabus ?? '' }); }
  reset(): void { this.editing.set(null); this.form.reset(); }

  save(): void {
    const e = this.editing();
    const req = e ? this.api.put(`/subjects/${e.id}`, this.form.getRawValue()) : this.api.post('/subjects', this.form.getRawValue());
    req.subscribe({ next: () => { this.snack.open('Disciplina salva.', 'OK', { duration: 2500 }); this.reset(); this.load(); }, error: err => this.error.set(apiMessage(err)) });
  }

  remove(s: SubjectView): void {
    this.api.delete(`/subjects/${s.id}`).subscribe({ next: () => this.load(), error: e => this.error.set(apiMessage(e)) });
  }
}
