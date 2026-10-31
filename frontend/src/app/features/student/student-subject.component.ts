import { Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { apiMessage } from '../../core/api-error';
import { KINDS, MySubjectDetail, SITUATIONS, situationBadge } from '../../core/portal.models';

@Component({
  selector: 'app-student-subject',
  imports: [DatePipe, DecimalPipe, RouterLink, MatCardModule, MatIconModule],
  template: `
    @if (d(); as d) {
      <header class="page-header"><div>
        <a routerLink="/aluno" class="back-link"><span aria-hidden="true">←</span> Meu painel</a>
        <h1>{{ d.summary.subjectName }}</h1>
        <p class="muted">{{ d.summary.subjectCode }} · {{ d.summary.workloadHours }} h · {{ d.summary.teacherName ?? 'Professor a definir' }}</p>
      </div></header>
      <section class="card-grid">
        <mat-card class="stat-card"><mat-icon aria-hidden="true">grade</mat-icon><div><span class="stat-label">Nota final</span><strong class="stat-value">{{ d.summary.result.finalGrade != null ? (d.summary.result.finalGrade | number:'1.2-2') : '—' }}</strong></div></mat-card>
        <mat-card class="stat-card"><mat-icon aria-hidden="true">event_busy</mat-icon><div><span class="stat-label">Faltas</span><strong class="stat-value">{{ d.summary.result.absences }} de {{ d.summary.result.totalLessons }}</strong></div></mat-card>
        <mat-card class="stat-card"><mat-icon aria-hidden="true">event_available</mat-icon><div><span class="stat-label">Frequência</span><strong class="stat-value">{{ d.summary.result.attendancePct | number:'1.0-1' }}%</strong></div></mat-card>
        <mat-card class="stat-card"><mat-icon aria-hidden="true">flag</mat-icon><div><span class="stat-label">Situação</span><span class="badge" [class]="badge(d.summary.result)">{{ situations[d.summary.result.situation] }}</span></div></mat-card>
      </section>
      <div class="two-col">
        <section><h2 class="section-title">Avaliações</h2>
          <table class="data-table responsive">
            <thead><tr><th scope="col">Avaliação</th><th scope="col">Tipo</th><th scope="col">Peso</th><th scope="col">Data</th><th scope="col">Nota</th></tr></thead>
            <tbody>@for (a of d.assessments; track $index) {
              <tr><td data-label="Avaliação">{{ a.title }}</td><td data-label="Tipo">{{ kinds[a.kind] }}</td><td data-label="Peso">{{ a.weight }}</td>
                <td data-label="Data">{{ a.dueDate ? (a.dueDate | date:'dd/MM/yyyy') : '—' }}</td>
                <td data-label="Nota"><strong>{{ a.score != null ? (a.score | number:'1.1-2') : '—' }}</strong> <span class="muted small">/ {{ a.maxScore }}</span></td></tr>
            } @empty { <tr><td colspan="5" class="muted">Nenhuma avaliação lançada.</td></tr> }</tbody>
          </table></section>
        <section><h2 class="section-title">Frequência</h2>
          <table class="data-table responsive">
            <thead><tr><th scope="col">Data</th><th scope="col">Aulas</th><th scope="col">Faltas</th><th scope="col">Conteúdo</th></tr></thead>
            <tbody>@for (a of d.attendance; track $index) {
              <tr><td data-label="Data">{{ a.date | date:'dd/MM/yyyy' }}</td><td data-label="Aulas">{{ a.lessons }}</td>
                <td data-label="Faltas"><span class="badge" [class]="a.absences ? 'warn' : 'ok'">{{ a.absences ? a.absences + ' falta(s)' : 'Presente' }}</span></td>
                <td data-label="Conteúdo">{{ a.content ?? '—' }}</td></tr>
            } @empty { <tr><td colspan="4" class="muted">Nenhuma aula registrada.</td></tr> }</tbody>
          </table></section>
      </div>
      <div class="two-col mt">
        <mat-card><mat-card-header><mat-card-title>Plano de ensino</mat-card-title></mat-card-header>
          <mat-card-content><p class="wrap">{{ d.teachingPlan || 'O professor ainda não publicou o plano de ensino.' }}</p></mat-card-content></mat-card>
        <mat-card><mat-card-header><mat-card-title>Ementa</mat-card-title></mat-card-header>
          <mat-card-content><p class="wrap">{{ d.syllabus || 'Ementa não cadastrada.' }}</p></mat-card-content></mat-card>
      </div>
    } @else if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
  `,
})
export class StudentSubjectComponent implements OnInit {
  readonly id = input.required<string>();
  private http = inject(HttpClient);
  readonly kinds = KINDS;
  readonly situations = SITUATIONS;
  readonly badge = situationBadge;
  readonly d = signal<MySubjectDetail | null>(null);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.http.get<MySubjectDetail>(`/api/student/subjects/${this.id()}`).subscribe({ next: d => this.d.set(d), error: e => this.error.set(apiMessage(e)) });
  }
}
