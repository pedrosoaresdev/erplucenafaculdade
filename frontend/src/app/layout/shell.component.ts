import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { BreakpointObserver } from '@angular/cdk/layout';
import { HttpClient } from '@angular/common/http';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatBadgeModule } from '@angular/material/badge';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { filter, Subscription } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { NotificationView, Page } from '../core/models';
import { DatePipe } from '@angular/common';

interface NavItem { label: string; icon: string; link: string; perms?: string[]; platformOnly?: boolean; }
interface NavGroup { title: string; items: NavItem[]; }

const NAV: NavGroup[] = [
  { title: 'Geral', items: [
    { label: 'Início', icon: 'dashboard', link: '/inicio' },
    { label: 'Meu perfil e segurança', icon: 'manage_accounts', link: '/perfil' },
  ] },
  { title: 'Plataforma (Master)', items: [
    { label: 'Dashboard da plataforma', icon: 'insights', link: '/plataforma', platformOnly: true },
    { label: 'Faculdades', icon: 'account_balance', link: '/plataforma/faculdades', platformOnly: true },
    { label: 'Planos', icon: 'workspace_premium', link: '/plataforma/planos', platformOnly: true },
  ] },
  { title: 'Acadêmico', items: [
    { label: 'Cursos', icon: 'school', link: '/academico/cursos', perms: ['COURSE_VIEW', 'COURSE_MANAGE'] },
    { label: 'Disciplinas', icon: 'menu_book', link: '/academico/disciplinas', perms: ['COURSE_VIEW', 'COURSE_MANAGE'] },
    { label: 'Períodos letivos', icon: 'date_range', link: '/academico/periodos', perms: ['COURSE_VIEW', 'COURSE_MANAGE'] },
    { label: 'Turmas', icon: 'groups', link: '/academico/turmas', perms: ['COURSE_VIEW', 'COURSE_MANAGE', 'ENROLLMENT_MANAGE'] },
    { label: 'Alunos', icon: 'badge', link: '/academico/alunos', perms: ['STUDENT_VIEW', 'STUDENT_MANAGE', 'ENROLLMENT_MANAGE'] },
    { label: 'Professores', icon: 'co_present', link: '/academico/professores', perms: ['TEACHER_MANAGE', 'COURSE_MANAGE'] },
    { label: 'Funcionários', icon: 'work', link: '/academico/funcionarios', perms: ['USER_VIEW', 'USER_MANAGE'] },
  ] },
  { title: 'Administração', items: [
    { label: 'Usuários', icon: 'group', link: '/admin/usuarios', perms: ['USER_VIEW', 'USER_MANAGE'] },
    { label: 'Cargos e permissões', icon: 'admin_panel_settings', link: '/admin/cargos', perms: ['ROLE_MANAGE', 'USER_VIEW', 'USER_MANAGE'] },
    { label: 'Configurações da instituição', icon: 'tune', link: '/admin/configuracoes', perms: ['FACULTY_SETTINGS_MANAGE'] },
    { label: 'Auditoria', icon: 'policy', link: '/admin/auditoria', perms: ['AUDIT_VIEW'] },
  ] },
];

const TITLES: Record<string, string> = {
  inicio: 'Início', perfil: 'Meu perfil', plataforma: 'Plataforma', faculdades: 'Faculdades', planos: 'Planos', admin: 'Administração',
  academico: 'Acadêmico', cursos: 'Cursos', disciplinas: 'Disciplinas', periodos: 'Períodos letivos', turmas: 'Turmas',
  alunos: 'Alunos', professores: 'Professores', funcionarios: 'Funcionários', usuarios: 'Usuários', cargos: 'Cargos e permissões', configuracoes: 'Configurações', auditoria: 'Auditoria',
};

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatIconModule, MatButtonModule, MatMenuModule,
    MatBadgeModule, MatTooltipModule, MatDividerModule, DatePipe],
  templateUrl: './shell.component.html',
})
export class ShellComponent implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  private http = inject(HttpClient);
  private router = inject(Router);
  private bp = inject(BreakpointObserver);
  private subs = new Subscription();
  private poll?: ReturnType<typeof setInterval>;

  readonly mobile = signal(false);
  readonly opened = signal(true);
  readonly collapsed = signal(false);
  readonly unread = signal(0);
  readonly notifications = signal<NotificationView[]>([]);
  readonly crumbs = signal<{ label: string; link: string }[]>([]);

  readonly nav = computed(() => {
    const p = this.auth.profile();
    if (!p) return [];
    return NAV.map(g => ({ ...g, items: g.items.filter(i => i.platformOnly ? p.platformAdmin : (!i.perms || this.auth.hasPermission(...i.perms))) }))
      .filter(g => g.items.length);
  });

  ngOnInit(): void {
    this.subs.add(this.bp.observe('(max-width: 959px)').subscribe(r => {
      this.mobile.set(r.matches);
      this.opened.set(!r.matches);
      if (r.matches) this.collapsed.set(false);
    }));
    this.subs.add(this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => {
      this.buildCrumbs();
      if (this.mobile()) this.opened.set(false);
    }));
    this.buildCrumbs();
    this.refreshUnread();
    this.poll = setInterval(() => this.refreshUnread(), 60_000);
  }

  ngOnDestroy(): void { this.subs.unsubscribe(); clearInterval(this.poll); }

  toggleMenu(): void {
    if (this.mobile()) this.opened.set(!this.opened());
    else this.collapsed.set(!this.collapsed());
  }

  refreshUnread(): void {
    if (!this.auth.isAuthenticated()) return;
    this.http.get<{ count: number }>('/api/me/notifications/unread-count').subscribe({ next: r => this.unread.set(r.count), error: () => {} });
  }

  loadNotifications(): void {
    this.http.get<Page<NotificationView>>('/api/me/notifications', { params: { size: 8 } }).subscribe(p => this.notifications.set(p.content));
  }

  markAllRead(): void {
    this.http.post('/api/me/notifications/read-all', {}).subscribe(() => { this.unread.set(0); this.loadNotifications(); });
  }

  initials(): string {
    const n = this.auth.profile()?.socialName || this.auth.profile()?.fullName || '';
    return n.split(' ').filter(Boolean).slice(0, 2).map(x => x[0].toUpperCase()).join('');
  }

  private buildCrumbs(): void {
    const parts = this.router.url.split('?')[0].split('/').filter(Boolean);
    let acc = '';
    this.crumbs.set(parts.filter(p => TITLES[p]).map(p => { acc += '/' + p; return { label: TITLES[p], link: acc }; }));
  }
}
