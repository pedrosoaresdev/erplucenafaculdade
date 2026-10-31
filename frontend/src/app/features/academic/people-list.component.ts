import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
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
import { Created, EmployeeView, TeacherView } from '../../core/academic.models';
import { Page } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { CredentialDialogComponent } from '../../shared/credential-dialog.component';
import { FacultyPickerComponent } from '../../shared/faculty-picker.component';
import { AcademicApi } from './academic-api.service';
import { PersonFormComponent } from './person-form.component';

type Person = TeacherView & EmployeeView;

/** Lista de professores (kind=teacher) ou funcionários (kind=employee). */
@Component({
  selector: 'app-people-list',
  imports: [FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule, MatMenuModule, MatSelectModule,
    MatSnackBarModule, FacultyPickerComponent],
  template: `
    <header class="page-header"><div><h1>{{ teacher() ? 'Professores' : 'Funcionários' }}</h1>
      <p class="muted">{{ teacher() ? 'Corpo docente, titulação, aulas atribuídas e acesso ao portal do professor.' : 'Colaboradores administrativos e acesso ao sistema.' }}</p></div>
      @if (canManage()) { <button mat-flat-button (click)="edit(null)" [disabled]="api.needsFaculty()"><mat-icon>person_add</mat-icon> {{ teacher() ? 'Novo professor' : 'Novo funcionário' }}</button> }</header>
    <section class="filters">
      <app-faculty-picker [(facultyId)]="api.selected.id" />
      <mat-form-field><mat-label>Buscar (nome, {{ teacher() ? 'e-mail' : 'setor' }} ou CPF)</mat-label><input matInput [(ngModel)]="q" (keyup.enter)="load()"><mat-icon matSuffix>search</mat-icon></mat-form-field>
      <mat-form-field><mat-label>Situação</mat-label><mat-select [(ngModel)]="status" (selectionChange)="load()">
        <mat-option [value]="null">Todas</mat-option><mat-option value="ACTIVE">Ativos</mat-option><mat-option value="INACTIVE">Inativos</mat-option></mat-select></mat-form-field>
      <button mat-stroked-button (click)="load()">Filtrar</button>
    </section>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <table class="data-table responsive">
      <thead><tr><th scope="col">Nome</th><th scope="col">CPF</th><th scope="col">{{ teacher() ? 'Titulação' : 'Setor / função' }}</th>
        @if (teacher()) { <th scope="col">Aulas</th> }<th scope="col">Situação</th><th scope="col">Acesso</th><th scope="col"><span class="sr-only">Ações</span></th></tr></thead>
      <tbody>@for (p of people(); track p.id) {
        <tr>
          <td data-label="Nome"><strong>{{ p.fullName }}</strong><br><span class="muted small">{{ p.email ?? '' }}</span></td>
          <td data-label="CPF">{{ p.cpfMasked }}</td>
          <td [attr.data-label]="teacher() ? 'Titulação' : 'Setor'">{{ teacher() ? (p.academicDegree ?? '—') : ((p.department ?? '—') + (p.jobTitle ? ' · ' + p.jobTitle : '')) }}</td>
          @if (teacher()) { <td data-label="Aulas">{{ p.classSubjects }}</td> }
          <td data-label="Situação"><span class="badge" [class]="p.status === 'ACTIVE' ? 'active' : 'inactive'">{{ p.status === 'ACTIVE' ? 'Ativo' : 'Inativo' }}</span></td>
          <td data-label="Acesso">@if (p.hasAccess) { <span class="badge ok">{{ p.username }}</span> } @else { <span class="badge inactive">Sem acesso</span> }</td>
          <td class="actions">@if (canManage()) {
            <button mat-icon-button [matMenuTriggerFor]="m" [attr.aria-label]="'Ações para ' + p.fullName"><mat-icon>more_vert</mat-icon></button>
            <mat-menu #m="matMenu">
              <button mat-menu-item (click)="edit(p)"><mat-icon>edit</mat-icon> {{ p.hasAccess ? 'Editar' : 'Editar / gerar acesso' }}</button>
              <button mat-menu-item (click)="remove(p)"><mat-icon>delete</mat-icon> Excluir</button>
            </mat-menu> }</td>
        </tr>
      } @empty { <tr><td colspan="7" class="muted">{{ api.needsFaculty() ? 'Selecione uma faculdade.' : 'Nenhum registro encontrado.' }}</td></tr> }</tbody>
    </table>
  `,
})
export class PeopleListComponent {
  readonly kind = input.required<'teacher' | 'employee'>();
  readonly api = inject(AcademicApi);
  private auth = inject(AuthService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  readonly people = signal<Person[]>([]);
  readonly error = signal<string | null>(null);
  q = '';
  status: string | null = null;

  constructor() { effect(() => { this.api.selected.id(); this.kind(); this.load(); }); }

  teacher(): boolean { return this.kind() === 'teacher'; }
  canManage(): boolean { return this.auth.hasPermission(this.teacher() ? 'TEACHER_MANAGE' : 'USER_MANAGE'); }
  private path(): string { return this.teacher() ? '/teachers' : '/employees'; }

  load(): void {
    if (this.api.needsFaculty()) { this.people.set([]); return; }
    this.api.get<Page<Person>>(this.path(), { q: this.q.trim(), status: this.status, size: 200 })
      .subscribe({ next: p => { this.error.set(null); this.people.set(p.content); }, error: e => this.error.set(apiMessage(e)) });
  }

  edit(p: Person | null): void {
    this.dialog.open(PersonFormComponent, { data: { kind: this.kind(), record: p ?? undefined }, width: '720px', maxWidth: '96vw' })
      .afterClosed().pipe(filter(Boolean)).subscribe((r: Created<Person> | Person) => {
        this.load();
        if ('record' in r && r.temporaryPassword) {
          this.dialog.open(CredentialDialogComponent, { data: { username: r.username, password: r.temporaryPassword }, disableClose: true });
        } else this.snack.open('Cadastro salvo.', 'OK', { duration: 3000 });
      });
  }

  remove(p: Person): void {
    this.dialog.open(ConfirmDialogComponent, { data: { title: 'Excluir cadastro', danger: true, confirmLabel: 'Excluir',
      message: `Excluir ${p.fullName}? Cadastros com acesso, aulas ou coordenação vinculados não podem ser excluídos — inative-os.` } })
      .afterClosed().pipe(filter(Boolean), switchMap(() => this.api.delete(`${this.path()}/${p.id}`)))
      .subscribe({ next: () => this.load(), error: e => this.error.set(apiMessage(e)) });
  }
}
