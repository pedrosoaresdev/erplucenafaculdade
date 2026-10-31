import { Component, effect, inject, signal } from '@angular/core';
import { DatePipe, JsonPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { AuditView, Page } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { AUDIT_LABELS } from '../../shared/labels';
import { FacultyPickerComponent, facultyParam } from '../../shared/faculty-picker.component';

@Component({
  selector: 'app-audit',
  imports: [DatePipe, JsonPipe, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatPaginatorModule, MatIconModule, FacultyPickerComponent],
  template: `
    <header class="page-header"><div><h1>Auditoria</h1><p class="muted">Trilha imutável de operações sensíveis (somente inserção no banco de dados).</p></div>
      <button mat-stroked-button (click)="exportCsv()" [disabled]="!page()?.content?.length"><mat-icon>download</mat-icon> Exportar CSV (página)</button></header>
    <section class="filters">
      <app-faculty-picker [allowAll]="true" [(facultyId)]="facultyId" />
      <mat-form-field><mat-label>Ação</mat-label><mat-select [(ngModel)]="action" (selectionChange)="load(0)">
        <mat-option [value]="null">Todas</mat-option>
        @for (a of actions; track a[0]) { <mat-option [value]="a[0]">{{ a[1] }}</mat-option> }</mat-select></mat-form-field>
    </section>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <table class="data-table responsive">
      <caption class="sr-only">Registros de auditoria</caption>
      <thead><tr><th scope="col">Data</th><th scope="col">Ação</th><th scope="col">Responsável</th><th scope="col">Entidade</th><th scope="col">IP</th><th scope="col">Detalhes</th></tr></thead>
      <tbody>@for (a of page()?.content ?? []; track a.id) {
        <tr><td data-label="Data">{{ a.createdAt | date:'short' }}</td><td data-label="Ação">{{ label(a.action) }}</td>
          <td data-label="Responsável">{{ a.actorName ?? '—' }}</td><td data-label="Entidade">{{ a.entityType }}</td>
          <td data-label="IP">{{ a.ipAddress ?? '—' }}</td><td data-label="Detalhes"><code class="small wrap">{{ a.details | json }}</code></td></tr>
      } @empty { <tr><td colspan="6" class="muted">Sem registros.</td></tr> }</tbody>
    </table>
    @if (page(); as p) { <mat-paginator [length]="p.totalElements" [pageSize]="p.size" [pageIndex]="p.page" [hidePageSize]="true" (page)="onPage($event)" /> }
  `,
})
export class AuditComponent {
  private http = inject(HttpClient);
  readonly page = signal<Page<AuditView> | null>(null);
  readonly error = signal<string | null>(null);
  readonly facultyId = signal<string | null>(null);
  readonly actions = Object.entries(AUDIT_LABELS);
  action: string | null = null;

  constructor() { effect(() => { this.facultyId(); this.load(0); }); }

  label(a: string): string { return AUDIT_LABELS[a] ?? a; }

  load(page: number): void {
    const params: Record<string, string | number> = { ...facultyParam(this.facultyId()), page, size: 50 };
    if (this.action) params['action'] = this.action;
    this.http.get<Page<AuditView>>('/api/admin/audit', { params }).subscribe({ next: p => this.page.set(p), error: e => this.error.set(apiMessage(e)) });
  }

  onPage(e: PageEvent): void { this.load(e.pageIndex); }

  exportCsv(): void {
    const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const rows = (this.page()?.content ?? []).map(a => [a.createdAt, this.label(a.action), a.actorName, a.entityType, a.entityId, a.ipAddress, JSON.stringify(a.details ?? {})].map(esc).join(';'));
    const csv = '﻿' + ['Data;Ação;Responsável;Entidade;ID;IP;Detalhes', ...rows].join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const el = document.createElement('a');
    el.href = url; el.download = 'auditoria.csv'; el.click();
    URL.revokeObjectURL(url);
  }
}
