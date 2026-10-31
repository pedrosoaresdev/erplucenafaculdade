import { Component, OnInit, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { Page, UserView } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { STATUS_LABELS } from '../../shared/labels';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { CredentialDialogComponent } from '../../shared/credential-dialog.component';
import { FacultyPickerComponent, facultyParam } from '../../shared/faculty-picker.component';
import { UserFormComponent } from './user-form.component';
import { UserActivityComponent } from './user-activity.component';

@Component({
  selector: 'app-users',
  imports: [DatePipe, FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatMenuModule,
    MatDialogModule, MatPaginatorModule, MatSnackBarModule, MatTooltipModule, FacultyPickerComponent],
  template: `
    <header class="page-header"><div><h1>Usuários</h1><p class="muted">Contas de acesso da instituição, cargos, situação e segurança.</p></div>
      @if (canManage) { <button mat-flat-button (click)="create()" [disabled]="needsFaculty()"><mat-icon>person_add</mat-icon> Novo usuário</button> }</header>
    <section class="filters" aria-label="Filtros">
      <app-faculty-picker [(facultyId)]="facultyId" />
      <mat-form-field><mat-label>Buscar (nome, usuário, e-mail)</mat-label><input matInput [(ngModel)]="q" (keyup.enter)="load(0)"><mat-icon matSuffix>search</mat-icon></mat-form-field>
      <mat-form-field><mat-label>Situação</mat-label><mat-select [(ngModel)]="status" (selectionChange)="load(0)">
        <mat-option [value]="null">Todas</mat-option><mat-option value="ACTIVE">Ativos</mat-option><mat-option value="INACTIVE">Inativos</mat-option><mat-option value="BLOCKED">Bloqueados</mat-option></mat-select></mat-form-field>
      <button mat-stroked-button (click)="load(0)">Filtrar</button>
    </section>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <table class="data-table responsive">
      <caption class="sr-only">Usuários</caption>
      <thead><tr><th scope="col">Nome</th><th scope="col">Usuário</th><th scope="col">Cargos</th><th scope="col">Situação</th><th scope="col">Segurança</th><th scope="col">Último acesso</th><th scope="col"><span class="sr-only">Ações</span></th></tr></thead>
      <tbody>
        @for (u of page()?.content ?? []; track u.id) {
          <tr>
            <td data-label="Nome"><strong>{{ u.fullName }}</strong><br><span class="muted small">{{ u.email }}</span></td>
            <td data-label="Usuário">{{ u.username }}</td>
            <td data-label="Cargos">{{ roleNames(u) }}</td>
            <td data-label="Situação"><span class="badge" [class]="u.status.toLowerCase()">{{ label(u.status) }}</span>
              @if (u.locked) { <span class="badge error" matTooltip="Bloqueio temporário por tentativas inválidas">Bloqueio temporário</span> }</td>
            <td data-label="Segurança">
              @if (u.mfaEnabled) { <span class="badge ok">2FA ativo</span> } @else { <span class="badge warn">2FA pendente</span> }
              @if (u.mustChangePassword) { <span class="badge warn">Senha temporária</span> }</td>
            <td data-label="Último acesso">{{ (u.lastLoginAt | date:'short') ?? 'Nunca' }}</td>
            <td class="actions">
              @if (canManage) {
                <button mat-icon-button [matMenuTriggerFor]="m" [attr.aria-label]="'Ações para ' + u.fullName"><mat-icon>more_vert</mat-icon></button>
                <mat-menu #m="matMenu">
                  <button mat-menu-item (click)="edit(u)"><mat-icon>edit</mat-icon> Editar / cargos</button>
                  <button mat-menu-item (click)="activity(u)"><mat-icon>history</mat-icon> Sessões e acessos</button>
                  <button mat-menu-item (click)="resetPassword(u)"><mat-icon>lock_reset</mat-icon> Redefinir senha</button>
                  @if (auth.hasPermission('MFA_RESET')) { <button mat-menu-item (click)="resetMfa(u)"><mat-icon>phonelink_erase</mat-icon> Recuperação de conta (redefinir 2FA)</button> }
                  @if (u.locked) { <button mat-menu-item (click)="unlock(u)"><mat-icon>lock_open</mat-icon> Desbloquear</button> }
                  @if (u.status !== 'ACTIVE') { <button mat-menu-item (click)="setStatus(u, 'ACTIVE')"><mat-icon>check_circle</mat-icon> Ativar</button> }
                  @if (u.status !== 'INACTIVE') { <button mat-menu-item (click)="setStatus(u, 'INACTIVE')"><mat-icon>person_off</mat-icon> Desativar</button> }
                  @if (u.status !== 'BLOCKED') { <button mat-menu-item (click)="setStatus(u, 'BLOCKED')"><mat-icon>block</mat-icon> Bloquear</button> }
                </mat-menu>
              }
            </td>
          </tr>
        } @empty { <tr><td colspan="7" class="muted">{{ needsFaculty() ? 'Selecione uma faculdade.' : 'Nenhum usuário encontrado.' }}</td></tr> }
      </tbody>
    </table>
    @if (page(); as p) { <mat-paginator [length]="p.totalElements" [pageSize]="p.size" [pageIndex]="p.page" [pageSizeOptions]="[10,20,50]" (page)="onPage($event)" /> }
  `,
})
export class UsersComponent implements OnInit {
  readonly auth = inject(AuthService);
  private http = inject(HttpClient);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  readonly page = signal<Page<UserView> | null>(null);
  readonly error = signal<string | null>(null);
  readonly facultyId = signal<string | null>(null);
  readonly canManage = this.auth.hasPermission('USER_MANAGE');
  q = '';
  status: string | null = null;
  private size = 20;

  constructor() {
    effect(() => { this.facultyId(); if (!this.needsFaculty()) this.load(0); });
  }

  ngOnInit(): void { if (!this.needsFaculty()) this.load(0); }

  needsFaculty(): boolean { return !!this.auth.profile()?.platformAdmin && !this.facultyId(); }
  private params(): Record<string, string> { return facultyParam(this.facultyId()); }

  load(page: number): void {
    if (this.needsFaculty()) return;
    const params: Record<string, string | number> = { ...this.params(), page, size: this.size };
    if (this.q.trim()) params['q'] = this.q.trim();
    if (this.status) params['status'] = this.status;
    this.http.get<Page<UserView>>('/api/admin/users', { params }).subscribe({ next: p => { this.error.set(null); this.page.set(p); }, error: e => this.error.set(apiMessage(e)) });
  }

  onPage(e: PageEvent): void { this.size = e.pageSize; this.load(e.pageIndex); }
  label(s: string): string { return STATUS_LABELS[s] ?? s; }
  roleNames(u: UserView): string { return u.roles.map(r => r.name).join(', '); }
  private reload(): void { this.load(this.page()?.page ?? 0); }
  private fail = (e: unknown) => this.error.set(apiMessage(e));

  create(): void {
    this.dialog.open(UserFormComponent, { data: { facultyId: this.facultyId() }, width: '520px', maxWidth: '96vw' }).afterClosed().pipe(filter(Boolean))
      .subscribe((r: { user: UserView; temporaryPassword: string }) => {
        this.reload();
        this.dialog.open(CredentialDialogComponent, { data: { username: r.user.username, password: r.temporaryPassword }, disableClose: true });
      });
  }

  edit(u: UserView): void {
    this.dialog.open(UserFormComponent, { data: { facultyId: this.facultyId(), user: u }, width: '520px', maxWidth: '96vw' }).afterClosed()
      .pipe(filter(Boolean)).subscribe(() => { this.snack.open('Usuário atualizado.', 'OK', { duration: 3000 }); this.reload(); });
  }

  activity(u: UserView): void {
    this.dialog.open(UserActivityComponent, { data: { user: u, facultyId: this.facultyId() }, width: '760px', maxWidth: '96vw' });
  }

  resetPassword(u: UserView): void {
    this.dialog.open(ConfirmDialogComponent, { data: { title: 'Redefinir senha', confirmLabel: 'Redefinir',
      message: `Gerar uma senha temporária para ${u.fullName}? As sessões atuais serão encerradas e a troca será exigida no próximo acesso.` } })
      .afterClosed().pipe(filter(Boolean), switchMap(() => this.http.post<{ username: string; temporaryPassword: string }>(`/api/admin/users/${u.id}/reset-password`, {}, { params: this.params() })))
      .subscribe({ next: r => { this.reload(); this.dialog.open(CredentialDialogComponent, { data: { username: r.username, password: r.temporaryPassword }, disableClose: true }); }, error: this.fail });
  }

  resetMfa(u: UserView): void {
    this.dialog.open(ConfirmDialogComponent, { data: { title: 'Recuperação de conta — redefinir autenticador', danger: true, confirmLabel: 'Redefinir autenticador',
      message: `O autenticador e os códigos de recuperação de ${u.fullName} serão revogados e todas as sessões encerradas. Procedimento auditado.`,
      fields: [{ key: 'reason', label: 'Motivo', minLength: 10, multiline: true }, { key: 'identityVerification', label: 'Como a identidade foi verificada', minLength: 5, multiline: true }] } })
      .afterClosed().pipe(filter(Boolean), switchMap((v: Record<string, string>) => this.http.post(`/api/admin/users/${u.id}/reset-mfa`, v, { params: this.params() })))
      .subscribe({ next: () => { this.snack.open('Autenticador redefinido. O usuário configurará um novo no próximo acesso.', 'OK', { duration: 4000 }); this.reload(); }, error: this.fail });
  }

  unlock(u: UserView): void {
    this.http.post(`/api/admin/users/${u.id}/unlock`, {}, { params: this.params() }).subscribe({ next: () => this.reload(), error: this.fail });
  }

  setStatus(u: UserView, status: 'ACTIVE' | 'INACTIVE' | 'BLOCKED'): void {
    const needsReason = status !== 'ACTIVE';
    this.dialog.open(ConfirmDialogComponent, { data: { title: `${STATUS_LABELS[status]}: ${u.fullName}`, danger: needsReason,
      message: needsReason ? 'As sessões ativas do usuário serão encerradas.' : 'O usuário voltará a ter acesso.',
      fields: needsReason ? [{ key: 'reason', label: 'Motivo', minLength: 3 }] : undefined } })
      .afterClosed().pipe(filter(Boolean), switchMap((v: Record<string, string> | true) =>
        this.http.post(`/api/admin/users/${u.id}/status`, { status, reason: v === true ? null : v['reason'] }, { params: this.params() })))
      .subscribe({ next: () => this.reload(), error: this.fail });
  }
}
