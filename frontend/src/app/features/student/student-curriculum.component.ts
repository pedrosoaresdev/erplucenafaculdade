import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { apiMessage } from '../../core/api-error';
import { CURRICULUM_STATUS, CurriculumProgress, CurriculumProgressItem } from '../../core/portal.models';

@Component({
  selector: 'app-student-curriculum',
  imports: [DecimalPipe, MatProgressBarModule],
  template: `
    <header class="page-header"><div><h1>Grade curricular</h1><p class="muted">{{ p()?.courseName ?? 'Sem curso vinculado' }}</p></div></header>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    @if (p(); as p) {
      <section class="progress-box" aria-label="Percentual de conclusão">
        <div class="progress-head"><strong>{{ p.completionPct | number:'1.0-1' }}% concluído</strong>
          <span class="muted small">{{ p.completedHours }} de {{ p.totalHours }} h · {{ p.completedCredits }} de {{ p.totalCredits }} créditos · {{ p.totalCredits - p.completedCredits }} créditos pendentes</span></div>
        <mat-progress-bar mode="determinate" [value]="p.completionPct" aria-hidden="true" />
        <div class="legend mt">@for (s of counts(); track s.key) { <span class="badge" [class]="badge(s.key)">{{ s.label }}: {{ s.n }}</span> }</div>
      </section>
      @for (sem of semesters(); track sem.n) {
        <h2 class="section-title">{{ sem.n }}º semestre</h2>
        <table class="data-table responsive">
          <thead><tr><th scope="col">Disciplina</th><th scope="col">Carga</th><th scope="col">Créditos</th><th scope="col">Tipo</th><th scope="col">Situação</th></tr></thead>
          <tbody>@for (i of sem.items; track i.subjectCode) {
            <tr><td data-label="Disciplina"><strong>{{ i.subjectName }}</strong> <span class="muted small">{{ i.subjectCode }}</span></td>
              <td data-label="Carga">{{ i.workloadHours }} h</td><td data-label="Créditos">{{ i.credits }}</td>
              <td data-label="Tipo">{{ i.mandatory ? 'Obrigatória' : 'Optativa' }}</td>
              <td data-label="Situação"><span class="badge" [class]="badge(i.status)">{{ label(i.status) }}</span></td></tr>
          }</tbody>
        </table>
      }
    }
  `,
})
export class StudentCurriculumComponent implements OnInit {
  private http = inject(HttpClient);
  readonly p = signal<CurriculumProgress | null>(null);
  readonly error = signal<string | null>(null);
  readonly semesters = computed(() => {
    const m = new Map<number, CurriculumProgressItem[]>();
    (this.p()?.items ?? []).forEach(i => m.set(i.semester, [...(m.get(i.semester) ?? []), i]));
    return [...m.entries()].sort((a, b) => a[0] - b[0]).map(([n, items]) => ({ n, items }));
  });
  readonly counts = computed(() => Object.entries(CURRICULUM_STATUS).map(([key, label]) =>
    ({ key, label, n: (this.p()?.items ?? []).filter(i => i.status === key).length })));

  ngOnInit(): void {
    this.http.get<CurriculumProgress>('/api/student/curriculum').subscribe({ next: p => this.p.set(p), error: e => this.error.set(apiMessage(e)) });
  }

  label(s: string): string { return CURRICULUM_STATUS[s] ?? s; }
  badge(s: string): string { return s === 'APROVADA' ? 'ok' : s === 'REPROVADA' ? 'error' : s === 'CURSANDO' ? 'pending' : 'inactive'; }
}
