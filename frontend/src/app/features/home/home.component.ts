import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/auth.service';

/** Roteiro de módulos (exibidos como "em implantação" até a respectiva fase ser entregue). */
const ROADMAP = [
  { icon: 'person', title: 'Portais do aluno e do professor', desc: 'Notas, faltas, diário de classe, materiais', phase: 'Fase 5' },
  { icon: 'description', title: 'Secretaria', desc: 'Matrículas, contratos, declarações e históricos', phase: 'Fase 6' },
  { icon: 'payments', title: 'Financeiro', desc: 'Cobranças, boletos/Pix, baixa automática, calculadoras', phase: 'Fase 7' },
  { icon: 'assignment', title: 'Requerimentos', desc: 'Catálogo, protocolos e acompanhamento', phase: 'Fase 8' },
  { icon: 'forum', title: 'Mensagens', desc: 'Conversas em tempo real e notificações', phase: 'Fase 9' },
  { icon: 'analytics', title: 'Direção e coordenação', desc: 'Dashboards e indicadores', phase: 'Fase 10' },
  { icon: 'local_library', title: 'Biblioteca', desc: 'Acervo, empréstimos e reservas', phase: 'Fase 11' },
];

@Component({
  selector: 'app-home',
  imports: [MatCardModule, MatIconModule, MatButtonModule, RouterLink, DatePipe],
  template: `
    <header class="page-header">
      <div>
        <h1>Olá, {{ (auth.profile()?.socialName || auth.profile()?.fullName || '').split(' ')[0] }}!</h1>
        <p class="muted">{{ auth.profile()?.faculty?.name ?? 'Administração da plataforma' }}
          @if (auth.profile()?.roles?.length) { · {{ auth.profile()!.roles.join(', ') }} }</p>
      </div>
    </header>
    @if (denied) { <p class="alert error" role="alert">Você não tem permissão para acessar a página solicitada.</p> }

    <section class="card-grid">
      <mat-card class="stat-card">
        <mat-icon aria-hidden="true">verified_user</mat-icon>
        <div><span class="stat-label">Verificação em duas etapas</span><strong class="stat-value ok">Ativa</strong></div>
      </mat-card>
      <mat-card class="stat-card">
        <mat-icon aria-hidden="true">schedule</mat-icon>
        <div><span class="stat-label">Último acesso</span><strong class="stat-value">{{ auth.profile()?.lastLoginAt | date:'short' }}</strong></div>
      </mat-card>
      @if (auth.profile()?.registration) {
        <mat-card class="stat-card"><mat-icon aria-hidden="true">badge</mat-icon>
          <div><span class="stat-label">Matrícula</span><strong class="stat-value">{{ auth.profile()!.registration }}</strong></div></mat-card>
      }
    </section>

    <section class="quick-links" aria-label="Atalhos">
      <a mat-stroked-button routerLink="/perfil"><mat-icon>manage_accounts</mat-icon> Meu perfil</a>
      <a mat-stroked-button routerLink="/perfil" [queryParams]="{ aba: 'senha' }"><mat-icon>password</mat-icon> Alterar senha</a>
      <a mat-stroked-button routerLink="/perfil" [queryParams]="{ aba: 'sessoes' }"><mat-icon>devices</mat-icon> Sessões ativas</a>
      @if (auth.profile()?.platformAdmin) { <a mat-flat-button routerLink="/plataforma"><mat-icon>insights</mat-icon> Painel da plataforma</a> }
      @if (auth.hasPermission('STUDENT_VIEW', 'STUDENT_MANAGE')) { <a mat-stroked-button routerLink="/academico/alunos"><mat-icon>badge</mat-icon> Alunos</a> }
      @if (auth.hasPermission('COURSE_VIEW', 'COURSE_MANAGE')) { <a mat-stroked-button routerLink="/academico/turmas"><mat-icon>groups</mat-icon> Turmas</a> }
      @if (auth.hasPermission('USER_VIEW', 'USER_MANAGE')) { <a mat-stroked-button routerLink="/admin/usuarios"><mat-icon>group</mat-icon> Usuários</a> }
    </section>

    <h2 class="section-title">Módulos em implantação</h2>
    <p class="muted">Os módulos abaixo ainda não estão integrados nesta versão e serão liberados conforme o cronograma de fases.</p>
    <section class="card-grid">
      @for (m of roadmap; track m.title) {
        <mat-card class="roadmap-card" aria-disabled="true">
          <mat-icon aria-hidden="true">{{ m.icon }}</mat-icon>
          <div><strong>{{ m.title }}</strong><span>{{ m.desc }}</span></div>
          <span class="badge pending">{{ m.phase }} · não integrado</span>
        </mat-card>
      }
    </section>
  `,
})
export class HomeComponent {
  readonly auth = inject(AuthService);
  readonly roadmap = ROADMAP;
  readonly denied = !!inject(ActivatedRoute).snapshot.queryParamMap.get('negado');
}
