import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TeachingView } from '../../core/portal.models';
import { WEEKDAYS } from '../../core/academic.models';
import { apiMessage } from '../../core/api-error';

@Component({
  selector: 'app-my-classes',
  imports: [RouterLink, MatCardModule, MatIconModule, MatButtonModule],
  template: `
    <header class="page-header"><div><h1>Minhas turmas</h1><p class="muted">Disciplinas atribuídas a você. Acesse o diário para lançar frequência e notas.</p></div></header>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <section class="card-grid wide">
      @for (t of items(); track t.classSubjectId) {
        <mat-card class="teaching-card">
          <mat-card-header>
            <mat-card-title>{{ t.subjectName }}</mat-card-title>
            <mat-card-subtitle>Turma {{ t.classCode }} · {{ t.courseName }} · {{ t.termCode }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <p><mat-icon inline aria-hidden="true">schedule</mat-icon>
              {{ t.weekday ? days[t.weekday] + ', ' + t.startTime!.slice(0,5) + '–' + t.endTime!.slice(0,5) : 'Horário a definir' }}{{ t.room ? ' · sala ' + t.room : '' }}</p>
            <p class="muted small">{{ t.students }} aluno(s) · {{ t.sessions }} aula(s) registrada(s) · {{ t.assessments }} avaliação(ões)</p>
            <span class="badge" [class]="t.termStatus === 'OPEN' ? 'ok' : t.termStatus === 'CLOSED' ? 'inactive' : 'pending'">
              {{ t.termStatus === 'OPEN' ? 'Período aberto' : t.termStatus === 'CLOSED' ? 'Período encerrado' : 'Período planejado' }}</span>
          </mat-card-content>
          <mat-card-actions><a mat-flat-button [routerLink]="['/professor/diario', t.classSubjectId]"><mat-icon>menu_book</mat-icon> Abrir diário</a></mat-card-actions>
        </mat-card>
      } @empty { <p class="muted">Nenhuma disciplina atribuída a você.</p> }
    </section>
  `,
})
export class MyClassesComponent implements OnInit {
  private http = inject(HttpClient);
  readonly days = WEEKDAYS;
  readonly items = signal<TeachingView[]>([]);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.http.get<TeachingView[]>('/api/teacher/classes').subscribe({ next: t => this.items.set(t), error: e => this.error.set(apiMessage(e)) });
  }
}
