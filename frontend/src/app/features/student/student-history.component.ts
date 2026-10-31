import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { apiMessage } from '../../core/api-error';
import { HistoryTerm, SITUATIONS, situationBadge } from '../../core/portal.models';

@Component({
  selector: 'app-student-history',
  imports: [DecimalPipe],
  template: `
    <header class="page-header"><div><h1>Histórico de semestres</h1><p class="muted">Resultados por período letivo. Documento oficial: solicite à Secretaria.</p></div></header>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    @for (t of terms(); track t.termCode) {
      <h2 class="section-title">{{ t.termCode }} — turma {{ t.classCode }} <span class="muted small">({{ t.courseName }}{{ t.termStatus === 'CLOSED' ? ', encerrado' : ', em andamento' }})</span></h2>
      <table class="data-table responsive">
        <thead><tr><th scope="col">Disciplina</th><th scope="col">Carga</th><th scope="col">Nota final</th><th scope="col">Frequência</th><th scope="col">Situação</th></tr></thead>
        <tbody>@for (s of t.subjects; track s.classSubjectId) {
          <tr><td data-label="Disciplina"><strong>{{ s.subjectName }}</strong> <span class="muted small">{{ s.subjectCode }}</span></td>
            <td data-label="Carga">{{ s.workloadHours }} h</td>
            <td data-label="Nota final">{{ s.result.finalGrade != null ? (s.result.finalGrade | number:'1.2-2') : '—' }}</td>
            <td data-label="Frequência">{{ s.result.attendancePct | number:'1.0-1' }}%</td>
            <td data-label="Situação"><span class="badge" [class]="badge(s.result)">{{ situations[s.result.situation] }}</span></td></tr>
        }</tbody>
      </table>
    } @empty { <p class="muted">Nenhum semestre cursado ainda.</p> }
  `,
})
export class StudentHistoryComponent implements OnInit {
  private http = inject(HttpClient);
  readonly situations = SITUATIONS;
  readonly badge = situationBadge;
  readonly terms = signal<HistoryTerm[]>([]);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.http.get<HistoryTerm[]>('/api/student/history').subscribe({ next: t => this.terms.set(t), error: e => this.error.set(apiMessage(e)) });
  }
}
