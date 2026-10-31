import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { STATUS_LABELS } from '../../shared/labels';

interface Dashboard {
  faculties: number; activeFaculties: number; suspendedFaculties: number; archivedFaculties: number; students: number; teachers: number;
  employees: number; courses: number; users: number; logins24h: number; failedLogins24h: number; activeSessions: number; openTickets: number;
  securityEvents7d: number; facultiesByUsage: { name: string; status: string; users: number; students: number }[];
  loginsByDay: { day: string; success: number; failure: number }[];
}

@Component({
  selector: 'app-platform-dashboard',
  imports: [MatCardModule, MatIconModule, MatButtonModule, RouterLink],
  template: `
    <header class="page-header"><div><h1>Painel da plataforma</h1><p class="muted">Indicadores globais calculados em tempo real a partir do banco de dados.</p></div>
      <a mat-flat-button routerLink="/plataforma/faculdades"><mat-icon>add</mat-icon> Gerenciar faculdades</a></header>
    @if (d(); as d) {
      <section class="card-grid kpis" aria-label="Indicadores">
        @for (k of kpis(); track k.label) {
          <mat-card class="stat-card"><mat-icon aria-hidden="true">{{ k.icon }}</mat-icon>
            <div><span class="stat-label">{{ k.label }}</span><strong class="stat-value" [class]="k.tone ?? ''">{{ k.value }}</strong></div></mat-card>
        }
      </section>
      <section class="two-col">
        <mat-card>
          <mat-card-header><mat-card-title>Acessos nos últimos 14 dias</mat-card-title></mat-card-header>
          <mat-card-content>
            @if (d.loginsByDay.length) {
              <div class="bar-chart" role="img" [attr.aria-label]="chartSummary()">
                @for (p of d.loginsByDay; track p.day) {
                  <div class="bar-col" [title]="p.day + ': ' + p.success + ' sucesso(s), ' + p.failure + ' falha(s)'">
                    <div class="bar fail" [style.height.px]="px(p.failure)"></div>
                    <div class="bar ok" [style.height.px]="px(p.success)"></div>
                    <span>{{ p.day.slice(8) }}</span>
                  </div>
                }
              </div>
              <p class="legend"><span class="dot ok"></span> Sucesso <span class="dot fail"></span> Falha</p>
            } @else { <p class="muted">Sem acessos no período.</p> }
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-header><mat-card-title>Utilização por faculdade</mat-card-title></mat-card-header>
          <mat-card-content>
            <table class="data-table responsive"><thead><tr><th scope="col">Faculdade</th><th scope="col">Situação</th><th scope="col">Usuários</th><th scope="col">Alunos</th></tr></thead>
              <tbody>@for (f of d.facultiesByUsage; track f.name) {
                <tr><td data-label="Faculdade">{{ f.name }}</td><td data-label="Situação"><span class="badge" [class]="f.status.toLowerCase()">{{ statusLabel(f.status) }}</span></td>
                  <td data-label="Usuários">{{ f.users }}</td><td data-label="Alunos">{{ f.students }}</td></tr>
              } @empty { <tr><td colspan="4" class="muted">Nenhuma faculdade cadastrada.</td></tr> }</tbody></table>
          </mat-card-content>
        </mat-card>
      </section>
      <p class="muted small">Integrações externas (pagamentos, Firebase, e-mail) ainda não integradas: o monitoramento de “serviços indisponíveis” será exibido quando configuradas.</p>
    }
  `,
})
export class PlatformDashboardComponent implements OnInit {
  private http = inject(HttpClient);
  readonly d = signal<Dashboard | null>(null);
  readonly statusLabel = (s: string) => STATUS_LABELS[s] ?? s;
  readonly max = computed(() => Math.max(1, ...(this.d()?.loginsByDay ?? []).map(p => p.success + p.failure)));
  readonly kpis = computed(() => {
    const d = this.d();
    if (!d) return [];
    return [
      { icon: 'account_balance', label: 'Faculdades', value: d.faculties },
      { icon: 'check_circle', label: 'Faculdades ativas', value: d.activeFaculties, tone: 'ok' },
      { icon: 'pause_circle', label: 'Faculdades suspensas', value: d.suspendedFaculties, tone: d.suspendedFaculties ? 'warn' : '' },
      { icon: 'school', label: 'Alunos', value: d.students },
      { icon: 'co_present', label: 'Professores', value: d.teachers },
      { icon: 'badge', label: 'Funcionários', value: d.employees },
      { icon: 'menu_book', label: 'Cursos', value: d.courses },
      { icon: 'group', label: 'Usuários', value: d.users },
      { icon: 'login', label: 'Acessos (24 h)', value: d.logins24h },
      { icon: 'gpp_maybe', label: 'Falhas de login (24 h)', value: d.failedLogins24h, tone: d.failedLogins24h ? 'warn' : '' },
      { icon: 'devices', label: 'Sessões ativas', value: d.activeSessions },
      { icon: 'support_agent', label: 'Chamados abertos', value: d.openTickets },
      { icon: 'policy', label: 'Eventos de segurança (7 dias)', value: d.securityEvents7d },
    ];
  });

  ngOnInit(): void { this.http.get<Dashboard>('/api/platform/dashboard').subscribe(d => this.d.set(d)); }
  /** Altura em px dentro da área útil de 150 px (barras empilhadas sucesso + falha). */
  px(v: number): number { return (v / this.max()) * 150; }
  chartSummary(): string {
    const t = (this.d()?.loginsByDay ?? []).reduce((a, p) => ({ s: a.s + p.success, f: a.f + p.failure }), { s: 0, f: 0 });
    return `Gráfico de acessos: ${t.s} com sucesso e ${t.f} com falha nos últimos 14 dias`;
  }
}
