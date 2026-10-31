import { Component, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../../core/auth.service';
import { TERM_STATUS, TermStatus, TermView } from '../../core/academic.models';
import { apiMessage } from '../../core/api-error';
import { FacultyPickerComponent } from '../../shared/faculty-picker.component';
import { AcademicApi } from './academic-api.service';

@Component({
  selector: 'app-terms',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatMenuModule, FacultyPickerComponent],
  template: `
    <header class="page-header"><div><h1>Períodos letivos</h1><p class="muted">Semestres letivos. Período encerrado bloqueia alterações em turmas.</p></div></header>
    <section class="filters"><app-faculty-picker [(facultyId)]="api.selected.id" /></section>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <div class="two-col wide-left">
      <table class="data-table responsive">
        <thead><tr><th scope="col">Período</th><th scope="col">Início</th><th scope="col">Término</th><th scope="col">Turmas</th><th scope="col">Situação</th><th scope="col"><span class="sr-only">Ações</span></th></tr></thead>
        <tbody>@for (t of terms(); track t.id) {
          <tr><td data-label="Período"><strong>{{ t.code }}</strong></td><td data-label="Início">{{ t.startDate | date:'dd/MM/yyyy' }}</td>
            <td data-label="Término">{{ t.endDate | date:'dd/MM/yyyy' }}</td><td data-label="Turmas">{{ t.classes }}</td>
            <td data-label="Situação"><span class="badge" [class]="badge(t.status)">{{ label(t.status) }}</span></td>
            <td class="actions">@if (canManage) {
              <button mat-icon-button [matMenuTriggerFor]="m" [attr.aria-label]="'Ações para ' + t.code"><mat-icon>more_vert</mat-icon></button>
              <mat-menu #m="matMenu">
                @if (t.status !== 'CLOSED') { <button mat-menu-item (click)="edit(t)"><mat-icon>edit</mat-icon> Editar</button> }
                @if (t.status === 'PLANNED') { <button mat-menu-item (click)="status(t, 'OPEN')"><mat-icon>lock_open</mat-icon> Abrir período</button> }
                @if (t.status === 'OPEN') { <button mat-menu-item (click)="status(t, 'CLOSED')"><mat-icon>lock</mat-icon> Encerrar período</button>
                  <button mat-menu-item (click)="status(t, 'PLANNED')"><mat-icon>undo</mat-icon> Voltar para planejado</button> }
                @if (t.status === 'CLOSED') { <button mat-menu-item (click)="status(t, 'OPEN')"><mat-icon>lock_open</mat-icon> Reabrir</button> }
                @if (!t.classes) { <button mat-menu-item (click)="remove(t)"><mat-icon>delete</mat-icon> Excluir</button> }
              </mat-menu> }</td></tr>
        } @empty { <tr><td colspan="6" class="muted">{{ api.needsFaculty() ? 'Selecione uma faculdade.' : 'Nenhum período cadastrado.' }}</td></tr> }</tbody>
      </table>
      @if (canManage) {
        <mat-card><mat-card-header><mat-card-title>{{ editing() ? 'Editar período' : 'Novo período' }}</mat-card-title></mat-card-header>
          <mat-card-content>
            <form [formGroup]="form" (ngSubmit)="save()">
              <mat-form-field class="full"><mat-label>Código (AAAA.S)</mat-label><input matInput formControlName="code" placeholder="2026.1" required></mat-form-field>
              <mat-form-field class="full"><mat-label>Início</mat-label><input matInput type="date" formControlName="startDate" required></mat-form-field>
              <mat-form-field class="full"><mat-label>Término</mat-label><input matInput type="date" formControlName="endDate" required></mat-form-field>
              <div class="row-actions"><button mat-flat-button type="submit" [disabled]="form.invalid || api.needsFaculty()">Salvar</button>
                @if (editing()) { <button mat-button type="button" (click)="reset()">Cancelar</button> }</div>
            </form>
          </mat-card-content></mat-card>
      }
    </div>
  `,
})
export class TermsComponent {
  readonly api = inject(AcademicApi);
  readonly canManage = inject(AuthService).hasPermission('COURSE_MANAGE');
  readonly terms = signal<TermView[]>([]);
  readonly editing = signal<TermView | null>(null);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{4}\.[12]$/)]], startDate: ['', Validators.required], endDate: ['', Validators.required],
  });

  constructor() { effect(() => { this.api.selected.id(); this.load(); }); }

  label(s: TermStatus): string { return TERM_STATUS[s]; }
  badge(s: TermStatus): string { return s === 'OPEN' ? 'ok' : s === 'CLOSED' ? 'inactive' : 'pending'; }

  load(): void {
    if (this.api.needsFaculty()) { this.terms.set([]); return; }
    this.api.get<TermView[]>('/terms').subscribe({ next: t => { this.error.set(null); this.terms.set(t); }, error: e => this.error.set(apiMessage(e)) });
  }

  edit(t: TermView): void { this.editing.set(t); this.form.patchValue(t); }
  reset(): void { this.editing.set(null); this.form.reset(); }

  save(): void {
    const e = this.editing();
    const req = e ? this.api.put(`/terms/${e.id}`, this.form.getRawValue()) : this.api.post('/terms', this.form.getRawValue());
    req.subscribe({ next: () => { this.reset(); this.load(); }, error: err => this.error.set(apiMessage(err)) });
  }

  status(t: TermView, status: TermStatus): void {
    this.api.post(`/terms/${t.id}/status`, { status }).subscribe({ next: () => this.load(), error: e => this.error.set(apiMessage(e)) });
  }

  remove(t: TermView): void {
    this.api.delete(`/terms/${t.id}`).subscribe({ next: () => this.load(), error: e => this.error.set(apiMessage(e)) });
  }
}
