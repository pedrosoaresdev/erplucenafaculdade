import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { filter } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { Created, STUDENT_STATUS, StudentStatus, StudentView } from '../../core/academic.models';
import { Page } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { CredentialDialogComponent } from '../../shared/credential-dialog.component';
import { FacultyPickerComponent } from '../../shared/faculty-picker.component';
import { AcademicApi } from './academic-api.service';
import { StudentFormComponent } from './student-form.component';
import { StudentStatusDialogComponent } from './student-status-dialog.component';

@Component({
  selector: 'app-students',
  imports: [FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule, MatMenuModule, MatPaginatorModule,
    MatSelectModule, MatSnackBarModule, FacultyPickerComponent],
  template: `
    <header class="page-header"><div><h1>Alunos</h1><p class="muted">Cadastro de alunos. O aluno entra no portal com a matrícula.</p></div>
      @if (canManage) { <button mat-flat-button (click)="edit(null)" [disabled]="api.needsFaculty()"><mat-icon>person_add</mat-icon> Novo aluno</button> }</header>
    <section class="filters">
      <app-faculty-picker [(facultyId)]="api.selected.id" />
      <mat-form-field><mat-label>Buscar (nome, matrícula ou CPF)</mat-label><input matInput [(ngModel)]="q" (keyup.enter)="load(0)"><mat-icon matSuffix>search</mat-icon></mat-form-field>
      <mat-form-field><mat-label>Situação</mat-label><mat-select [(ngModel)]="status" (selectionChange)="load(0)">
        <mat-option [value]="null">Todas</mat-option>@for (s of statuses; track s[0]) { <mat-option [value]="s[0]">{{ s[1] }}</mat-option> }</mat-select></mat-form-field>
      <button mat-stroked-button (click)="load(0)">Filtrar</button>
    </section>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <table class="data-table responsive">
      <caption class="sr-only">Alunos</caption>
      <thead><tr><th scope="col">Aluno</th><th scope="col">Matrícula</th><th scope="col">CPF</th><th scope="col">Turma atual</th><th scope="col">Situação</th><th scope="col">Acesso</th><th scope="col"><span class="sr-only">Ações</span></th></tr></thead>
      <tbody>@for (s of page()?.content ?? []; track s.id) {
        <tr>
          <td data-label="Aluno"><strong>{{ s.socialName || s.fullName }}</strong><br><span class="muted small">{{ s.email }}</span></td>
          <td data-label="Matrícula">{{ s.registration }}</td><td data-label="CPF">{{ s.cpfMasked }}</td>
          <td data-label="Turma atual">{{ s.currentClass ?? '—' }}</td>
          <td data-label="Situação"><span class="badge" [class]="s.status === 'ACTIVE' ? 'active' : 'warn'">{{ label(s.status) }}</span></td>
          <td data-label="Acesso">@if (s.hasAccess) { <span class="badge ok">Portal liberado</span> } @else { <span class="badge inactive">Sem acesso</span> }</td>
          <td class="actions">@if (canManage || canStatus) {
            <button mat-icon-button [matMenuTriggerFor]="m" [attr.aria-label]="'Ações para ' + s.fullName"><mat-icon>more_vert</mat-icon></button>
            <mat-menu #m="matMenu">
              @if (canManage) { <button mat-menu-item (click)="edit(s)"><mat-icon>edit</mat-icon> Editar cadastro</button> }
              <button mat-menu-item (click)="changeStatus(s)"><mat-icon>swap_horiz</mat-icon> Alterar situação</button>
              @if (canManage && !s.hasAccess) { <button mat-menu-item (click)="access(s)"><mat-icon>key</mat-icon> Gerar acesso ao portal</button> }
            </mat-menu> }</td>
        </tr>
      } @empty { <tr><td colspan="7" class="muted">{{ api.needsFaculty() ? 'Selecione uma faculdade.' : 'Nenhum aluno encontrado.' }}</td></tr> }</tbody>
    </table>
    @if (page(); as p) { <mat-paginator [length]="p.totalElements" [pageSize]="p.size" [pageIndex]="p.page" [pageSizeOptions]="[10,20,50]" (page)="onPage($event)" /> }
  `,
})
export class StudentsComponent {
  readonly api = inject(AcademicApi);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private auth = inject(AuthService);
  readonly canManage = this.auth.hasPermission('STUDENT_MANAGE');
  readonly canStatus = this.auth.hasPermission('STUDENT_MANAGE', 'ENROLLMENT_MANAGE');
  readonly statuses = Object.entries(STUDENT_STATUS);
  readonly page = signal<Page<StudentView> | null>(null);
  readonly error = signal<string | null>(null);
  q = '';
  status: StudentStatus | null = null;
  private size = 20;

  constructor() { effect(() => { this.api.selected.id(); this.load(0); }); }

  label(s: StudentStatus): string { return STUDENT_STATUS[s]; }

  load(page: number): void {
    if (this.api.needsFaculty()) { this.page.set(null); return; }
    this.api.get<Page<StudentView>>('/students', { q: this.q.trim(), status: this.status, page, size: this.size })
      .subscribe({ next: p => { this.error.set(null); this.page.set(p); }, error: e => this.error.set(apiMessage(e)) });
  }

  onPage(e: PageEvent): void { this.size = e.pageSize; this.load(e.pageIndex); }
  private reload(): void { this.load(this.page()?.page ?? 0); }

  edit(s: StudentView | null): void {
    const open = (data: StudentView | null) => this.dialog.open(StudentFormComponent, { data, width: '760px', maxWidth: '96vw' }).afterClosed()
      .pipe(filter(Boolean)).subscribe((r: Created<StudentView> | StudentView) => {
        this.reload();
        if ('record' in r && r.temporaryPassword) {
          this.dialog.open(CredentialDialogComponent, { data: { username: r.username, password: r.temporaryPassword }, disableClose: true });
        } else this.snack.open(s ? 'Cadastro atualizado.' : `Aluno cadastrado com matrícula ${'record' in r ? r.record.registration : ''}.`, 'OK', { duration: 4000 });
      });
    // A edição carrega o cadastro completo (CPF e endereço) do servidor
    if (s) this.api.get<StudentView>(`/students/${s.id}`).subscribe({ next: full => open(full), error: e => this.error.set(apiMessage(e)) });
    else open(null);
  }

  changeStatus(s: StudentView): void {
    this.dialog.open(StudentStatusDialogComponent, { data: s, width: '460px', maxWidth: '96vw' }).afterClosed().pipe(filter(Boolean)).subscribe(() => this.reload());
  }

  access(s: StudentView): void {
    this.api.post<Created<StudentView>>(`/students/${s.id}/access`).subscribe({
      next: r => { this.reload(); this.dialog.open(CredentialDialogComponent, { data: { username: r.username, password: r.temporaryPassword }, disableClose: true }); },
      error: e => this.error.set(apiMessage(e)),
    });
  }
}
