import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { apiMessage } from '../../core/api-error';
import { STUDENT_STATUS, StudentStatus, WEEKDAYS } from '../../core/academic.models';
import { SITUATIONS, ScheduleItem, StudentOverview, situationBadge } from '../../core/portal.models';

@Component({
  selector: 'app-student-home',
  imports: [DecimalPipe, RouterLink, MatCardModule, MatIconModule, MatButtonModule],
  template: `
    @if (o(); as o) {
      <header class="page-header"><div>
        <h1>Olá, {{ o.name.split(' ')[0] }}!</h1>
        <p class="muted">Matrícula {{ o.registration }} · {{ o.courseName ?? 'sem turma no momento' }}{{ o.semester ? ' · ' + o.semester + 'º semestre' : '' }}</p>
      </div></header>
      @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }

      <section class="card-grid">
        <mat-card class="stat-card"><mat-icon aria-hidden="true">badge</mat-icon><div><span class="stat-label">Situação da matrícula</span><strong class="stat-value">{{ statusLabel(o.status) }}</strong></div></mat-card>
        <mat-card class="stat-card"><mat-icon aria-hidden="true">groups</mat-icon><div><span class="stat-label">Turma / período</span><strong class="stat-value">{{ o.classCode ?? '—' }} {{ o.termCode ? '· ' + o.termCode : '' }}</strong></div></mat-card>
        <mat-card class="stat-card"><mat-icon aria-hidden="true">grade</mat-icon><div><span class="stat-label">Média geral</span><strong class="stat-value">{{ overall() != null ? (overall() | number:'1.2-2') : '—' }}</strong></div></mat-card>
        <mat-card class="stat-card"><mat-icon aria-hidden="true">event_available</mat-icon><div><span class="stat-label">Frequência geral</span><strong class="stat-value" [class.warn]="attendance() < o.minAttendancePct">{{ attendance() | number:'1.0-1' }}%</strong></div></mat-card>
      </section>

      @if (next(); as n) {
        <p class="alert info"><mat-icon aria-hidden="true">event</mat-icon> Próxima aula: <strong>{{ n.subjectName }}</strong> — {{ days[n.weekday] }}, {{ n.startTime.slice(0,5) }}{{ n.room ? ' · sala ' + n.room : '' }}{{ n.teacherName ? ' · ' + n.teacherName : '' }}</p>
      }

      <h2 class="section-title">Notas, faltas e frequência</h2>
      <table class="data-table responsive">
        <caption class="sr-only">Desempenho por disciplina</caption>
        <thead><tr><th scope="col">Disciplina</th><th scope="col">Professor</th><th scope="col">Nota</th><th scope="col">Faltas</th><th scope="col">Frequência</th><th scope="col">Situação</th></tr></thead>
        <tbody>@for (s of o.subjects; track s.classSubjectId) {
          <tr><td data-label="Disciplina"><a [routerLink]="['/aluno/disciplina', s.classSubjectId]"><strong>{{ s.subjectName }}</strong></a></td>
            <td data-label="Professor">{{ s.teacherName ?? 'A definir' }}</td>
            <td data-label="Nota" [class.risk]="s.result.riskGrade">{{ s.result.finalGrade != null ? (s.result.finalGrade | number:'1.2-2') : '—' }}</td>
            <td data-label="Faltas">{{ s.result.absences }} de {{ s.result.totalLessons }}</td>
            <td data-label="Frequência" [class.risk]="s.result.riskAttendance">{{ s.result.attendancePct | number:'1.0-1' }}%</td>
            <td data-label="Situação"><span class="badge" [class]="badge(s.result)">{{ situations[s.result.situation] }}</span></td></tr>
        } @empty { <tr><td colspan="6" class="muted">Você ainda não está em uma turma.</td></tr> }</tbody>
      </table>
      <p class="muted small">Aprovação: nota final ≥ {{ o.minGrade | number:'1.1-2' }} e frequência ≥ {{ o.minAttendancePct | number:'1.0-0' }}%.</p>

      <h2 class="section-title">Quadro de horários</h2>
      <div class="schedule-grid" role="table" aria-label="Quadro de horários semanal">
        @for (day of weekdays(); track day) {
          <section class="schedule-day" role="row"><h3 role="rowheader">{{ days[day] }}</h3>
            @for (c of byDay(day); track c.classSubjectId) {
              <div class="schedule-item" role="cell"><strong>{{ c.startTime.slice(0,5) }}–{{ c.endTime.slice(0,5) }}</strong><span>{{ c.subjectName }}</span>
                <small class="muted">{{ c.teacherName ?? '' }}{{ c.room ? ' · sala ' + c.room : '' }}</small></div>
            }
          </section>
        } @empty { <p class="muted">Sem horários definidos.</p> }
      </div>

      <section class="quick-links mt">
        <a mat-stroked-button routerLink="/aluno/historico"><mat-icon>history_edu</mat-icon> Histórico de semestres</a>
        <a mat-stroked-button routerLink="/aluno/grade"><mat-icon>account_tree</mat-icon> Grade curricular</a>
        <a mat-stroked-button routerLink="/perfil"><mat-icon>manage_accounts</mat-icon> Perfil e segurança</a>
      </section>
    } @else if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
  `,
})
export class StudentHomeComponent implements OnInit {
  private http = inject(HttpClient);
  readonly days = WEEKDAYS;
  readonly situations = SITUATIONS;
  readonly badge = situationBadge;
  readonly o = signal<StudentOverview | null>(null);
  readonly error = signal<string | null>(null);

  readonly overall = computed(() => {
    const g = (this.o()?.subjects ?? []).map(s => s.result.finalGrade).filter((v): v is number => v != null);
    return g.length ? g.reduce((a, b) => a + b, 0) / g.length : null;
  });
  readonly attendance = computed(() => {
    const s = this.o()?.subjects ?? [];
    const total = s.reduce((a, x) => a + x.result.totalLessons, 0);
    const abs = s.reduce((a, x) => a + x.result.absences, 0);
    return total ? ((total - abs) * 100) / total : 100;
  });
  readonly weekdays = computed(() => [...new Set((this.o()?.schedule ?? []).map(s => s.weekday))].sort());
  /** Próxima aula a partir de agora (semana corrente, segunda = 1). */
  readonly next = computed<ScheduleItem | null>(() => {
    const items = this.o()?.schedule ?? [];
    if (!items.length) return null;
    const now = new Date();
    const today = ((now.getDay() + 6) % 7) + 1;
    const hhmm = now.toTimeString().slice(0, 5);
    const key = (s: ScheduleItem) => ((s.weekday - today + 7) % 7) * 1440 + Number(s.startTime.slice(0, 2)) * 60 + Number(s.startTime.slice(3, 5));
    const nowKey = Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));
    return [...items].map(s => ({ s, k: key(s) < nowKey ? key(s) + 7 * 1440 : key(s) })).sort((a, b) => a.k - b.k)[0].s;
  });

  ngOnInit(): void {
    this.http.get<StudentOverview>('/api/student/overview').subscribe({ next: o => this.o.set(o), error: e => this.error.set(apiMessage(e)) });
  }

  byDay(day: number): ScheduleItem[] { return (this.o()?.schedule ?? []).filter(s => s.weekday === day); }
  statusLabel(s: string): string { return STUDENT_STATUS[s as StudentStatus] ?? s; }
}
