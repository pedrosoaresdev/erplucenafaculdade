import { Component, OnInit, inject, signal } from '@angular/core';
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
import { Router } from '@angular/router';
import { SelectedFacultyService } from '../../core/selected-faculty.service';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { filter, switchMap } from 'rxjs';
import { FacultyView, Page } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { STATUS_LABELS } from '../../shared/labels';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { CredentialDialogComponent } from '../../shared/credential-dialog.component';
import { CreatedFaculty, FacultyFormComponent } from './faculty-form.component';

@Component({
  selector: 'app-faculties',
  imports: [DatePipe, FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatMenuModule,
    MatDialogModule, MatPaginatorModule, MatSnackBarModule],
  template: `
    <header class="page-header"><div><h1>Faculdades</h1><p class="muted">Cada faculdade é um ambiente isolado, com seus próprios usuários, dados e permissões.</p></div>
      <button mat-flat-button (click)="create()"><mat-icon>add</mat-icon> Nova faculdade</button></header>
    <section class="filters" aria-label="Filtros">
      <mat-form-field><mat-label>Buscar (nome, CNPJ, identificador)</mat-label><input matInput [(ngModel)]="q" (keyup.enter)="load(0)"><mat-icon matSuffix>search</mat-icon></mat-form-field>
      <mat-form-field><mat-label>Situação</mat-label><mat-select [(ngModel)]="status" (selectionChange)="load(0)">
        <mat-option [value]="null">Todas</mat-option><mat-option value="ACTIVE">Ativas</mat-option><mat-option value="SUSPENDED">Suspensas</mat-option><mat-option value="ARCHIVED">Arquivadas</mat-option></mat-select></mat-form-field>
      <button mat-stroked-button (click)="load(0)">Filtrar</button>
    </section>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <table class="data-table responsive">
      <caption class="sr-only">Faculdades cadastradas</caption>
      <thead><tr><th scope="col">Faculdade</th><th scope="col">CNPJ</th><th scope="col">Plano</th><th scope="col">Situação</th><th scope="col">Usuários</th><th scope="col">Alunos</th><th scope="col">Cadastro</th><th scope="col"><span class="sr-only">Ações</span></th></tr></thead>
      <tbody>
        @for (f of page()?.content ?? []; track f.id) {
          <tr>
            <td data-label="Faculdade"><strong>{{ f.name }}</strong><br><span class="muted small">/f/{{ f.slug }}/login</span></td>
            <td data-label="CNPJ">{{ cnpj(f.cnpj) }}</td>
            <td data-label="Plano">{{ f.planName ?? '—' }}</td>
            <td data-label="Situação"><span class="badge" [class]="f.status.toLowerCase()">{{ label(f.status) }}</span>
              @if (f.statusReason && f.status !== 'ACTIVE') { <br><span class="muted small">{{ f.statusReason }}</span> }</td>
            <td data-label="Usuários">{{ f.users }}</td>
            <td data-label="Alunos">{{ f.students }}</td>
            <td data-label="Cadastro">{{ f.createdAt | date:'dd/MM/yyyy' }}</td>
            <td class="actions">
              <button mat-icon-button [matMenuTriggerFor]="m" [attr.aria-label]="'Ações para ' + f.name"><mat-icon>more_vert</mat-icon></button>
              <mat-menu #m="matMenu">
                @if (f.status === 'ACTIVE') { <button mat-menu-item (click)="support(f)"><mat-icon>support_agent</mat-icon> Acessar como suporte</button> }
                <button mat-menu-item (click)="edit(f)"><mat-icon>edit</mat-icon> Editar</button>
                @if (f.status === 'ACTIVE') { <button mat-menu-item (click)="changeStatus(f, 'SUSPEND')"><mat-icon>pause_circle</mat-icon> Suspender</button> }
                @if (f.status === 'SUSPENDED') { <button mat-menu-item (click)="changeStatus(f, 'REACTIVATE')"><mat-icon>play_circle</mat-icon> Reativar</button> }
                @if (f.status === 'ARCHIVED') { <button mat-menu-item (click)="changeStatus(f, 'REACTIVATE')"><mat-icon>unarchive</mat-icon> Reativar</button> }
                @else { <button mat-menu-item (click)="changeStatus(f, 'ARCHIVE')"><mat-icon>archive</mat-icon> Arquivar</button> }
                <button mat-menu-item (click)="remove(f)"><mat-icon>delete</mat-icon> Excluir (somente sem dados)</button>
              </mat-menu>
            </td>
          </tr>
        } @empty { <tr><td colspan="8" class="muted">Nenhuma faculdade encontrada.</td></tr> }
      </tbody>
    </table>
    @if (page(); as p) { <mat-paginator [length]="p.totalElements" [pageSize]="p.size" [pageIndex]="p.page" [pageSizeOptions]="[10,20,50]" (page)="onPage($event)" /> }
  `,
})
export class FacultiesComponent implements OnInit {
  private http = inject(HttpClient);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private router = inject(Router);
  private selected = inject(SelectedFacultyService);
  readonly page = signal<Page<FacultyView> | null>(null);
  readonly error = signal<string | null>(null);
  q = '';
  status: string | null = null;
  private size = 20;

  ngOnInit(): void { this.load(0); }

  load(page: number): void {
    const params: Record<string, string | number> = { page, size: this.size };
    if (this.q.trim()) params['q'] = this.q.trim();
    if (this.status) params['status'] = this.status;
    this.http.get<Page<FacultyView>>('/api/platform/faculties', { params }).subscribe({ next: p => this.page.set(p), error: e => this.error.set(apiMessage(e)) });
  }

  onPage(e: PageEvent): void { this.size = e.pageSize; this.load(e.pageIndex); }
  label(s: string): string { return STATUS_LABELS[s] ?? s; }

  /** Entra nos dados da faculdade para suporte (o acesso é registrado na auditoria dela). */
  support(f: FacultyView): void {
    this.selected.select(f.id, f.name);
    this.snack.open(`Suporte à ${f.name}: os acessos são registrados na auditoria da faculdade.`, 'OK', { duration: 5000 });
    this.router.navigate(['/academico/alunos']);
  }
  cnpj(c: string): string { return c.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5'); }

  create(): void {
    this.dialog.open(FacultyFormComponent, { data: null, width: '760px', maxWidth: '96vw' }).afterClosed().pipe(filter(Boolean)).subscribe((r: CreatedFaculty) => {
      this.load(0);
      if (r.initialAdminTemporaryPassword) {
        this.dialog.open(CredentialDialogComponent, { data: { username: r.initialAdminUsername, password: r.initialAdminTemporaryPassword,
          login: `${location.origin}/f/${r.faculty.slug}/login` }, disableClose: true });
      } else this.snack.open('Faculdade criada.', 'OK', { duration: 3000 });
    });
  }

  edit(f: FacultyView): void {
    this.dialog.open(FacultyFormComponent, { data: f, width: '760px', maxWidth: '96vw' }).afterClosed().pipe(filter(Boolean))
      .subscribe(() => { this.snack.open('Faculdade atualizada.', 'OK', { duration: 3000 }); this.load(this.page()?.page ?? 0); });
  }

  changeStatus(f: FacultyView, action: 'SUSPEND' | 'REACTIVATE' | 'ARCHIVE'): void {
    const needsReason = action !== 'REACTIVATE';
    const titles = { SUSPEND: 'Suspender faculdade', REACTIVATE: 'Reativar faculdade', ARCHIVE: 'Arquivar faculdade' };
    const msgs = {
      SUSPEND: 'Todos os usuários serão desconectados e não poderão entrar até a reativação.',
      REACTIVATE: 'Os usuários voltarão a ter acesso.',
      ARCHIVE: 'A faculdade ficará inacessível; os dados são mantidos conforme a política de retenção.',
    };
    this.dialog.open(ConfirmDialogComponent, { data: { title: `${titles[action]}: ${f.name}`, message: msgs[action], danger: action !== 'REACTIVATE',
      fields: needsReason ? [{ key: 'reason', label: 'Motivo', minLength: 5, multiline: true }] : undefined } })
      .afterClosed().pipe(filter(Boolean), switchMap((v: Record<string, string> | true) =>
        this.http.post(`/api/platform/faculties/${f.id}/status`, { action, reason: v === true ? null : v['reason'] })))
      .subscribe({ next: () => this.load(this.page()?.page ?? 0), error: e => this.error.set(apiMessage(e)) });
  }

  remove(f: FacultyView): void {
    this.dialog.open(ConfirmDialogComponent, { data: { title: 'Excluir faculdade', danger: true, confirmLabel: 'Excluir',
      message: `Excluir definitivamente "${f.name}"? Só é permitido para faculdades sem usuários ou dados acadêmicos; caso contrário, use Arquivar.` } })
      .afterClosed().pipe(filter(Boolean), switchMap(() => this.http.delete(`/api/platform/faculties/${f.id}`)))
      .subscribe({ next: () => this.load(0), error: e => this.error.set(apiMessage(e)) });
  }
}
