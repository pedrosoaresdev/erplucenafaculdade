import { Routes } from '@angular/router';
import { authGuard, permissionGuard } from './core/guards';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent), title: 'Entrar — Plataforma' },
  { path: 'f/:slug/login', loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent), title: 'Entrar' },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      { path: 'inicio', loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent), title: 'Início' },
      { path: 'perfil', loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent), title: 'Meu perfil' },
      {
        path: 'plataforma', canActivate: [permissionGuard('PLATFORM_ADMIN')],
        children: [
          { path: '', loadComponent: () => import('./features/platform/platform-dashboard.component').then(m => m.PlatformDashboardComponent), title: 'Plataforma' },
          { path: 'faculdades', loadComponent: () => import('./features/platform/faculties.component').then(m => m.FacultiesComponent), title: 'Faculdades' },
          { path: 'planos', loadComponent: () => import('./features/platform/plans.component').then(m => m.PlansComponent), title: 'Planos' },
        ],
      },
      {
        path: 'academico',
        children: [
          { path: 'cursos', canActivate: [permissionGuard('COURSE_VIEW', 'COURSE_MANAGE')], loadComponent: () => import('./features/academic/courses.component').then(m => m.CoursesComponent), title: 'Cursos' },
          { path: 'cursos/:id', canActivate: [permissionGuard('COURSE_VIEW', 'COURSE_MANAGE')], loadComponent: () => import('./features/academic/curriculum.component').then(m => m.CurriculumComponent), title: 'Matriz curricular' },
          { path: 'disciplinas', canActivate: [permissionGuard('COURSE_VIEW', 'COURSE_MANAGE')], loadComponent: () => import('./features/academic/subjects.component').then(m => m.SubjectsComponent), title: 'Disciplinas' },
          { path: 'periodos', canActivate: [permissionGuard('COURSE_VIEW', 'COURSE_MANAGE')], loadComponent: () => import('./features/academic/terms.component').then(m => m.TermsComponent), title: 'Períodos letivos' },
          { path: 'turmas', canActivate: [permissionGuard('COURSE_VIEW', 'COURSE_MANAGE', 'ENROLLMENT_MANAGE')], loadComponent: () => import('./features/academic/classes.component').then(m => m.ClassesComponent), title: 'Turmas' },
          { path: 'turmas/:id', canActivate: [permissionGuard('COURSE_VIEW', 'COURSE_MANAGE', 'ENROLLMENT_MANAGE')], loadComponent: () => import('./features/academic/class-detail.component').then(m => m.ClassDetailComponent), title: 'Turma' },
          { path: 'professores', canActivate: [permissionGuard('TEACHER_MANAGE', 'COURSE_MANAGE')], data: { kind: 'teacher' }, loadComponent: () => import('./features/academic/people-list.component').then(m => m.PeopleListComponent), title: 'Professores' },
          { path: 'funcionarios', canActivate: [permissionGuard('USER_VIEW', 'USER_MANAGE')], data: { kind: 'employee' }, loadComponent: () => import('./features/academic/people-list.component').then(m => m.PeopleListComponent), title: 'Funcionários' },
          { path: 'alunos', canActivate: [permissionGuard('STUDENT_VIEW', 'STUDENT_MANAGE', 'ENROLLMENT_MANAGE')], loadComponent: () => import('./features/academic/students.component').then(m => m.StudentsComponent), title: 'Alunos' },
        ],
      },
      {
        path: 'admin',
        children: [
          { path: 'usuarios', canActivate: [permissionGuard('USER_VIEW', 'USER_MANAGE')], loadComponent: () => import('./features/admin/users.component').then(m => m.UsersComponent), title: 'Usuários' },
          { path: 'cargos', canActivate: [permissionGuard('ROLE_MANAGE', 'USER_VIEW', 'USER_MANAGE')], loadComponent: () => import('./features/admin/roles.component').then(m => m.RolesComponent), title: 'Cargos' },
          { path: 'configuracoes', canActivate: [permissionGuard('FACULTY_SETTINGS_MANAGE')], loadComponent: () => import('./features/admin/settings.component').then(m => m.SettingsComponent), title: 'Configurações' },
          { path: 'auditoria', canActivate: [permissionGuard('AUDIT_VIEW')], loadComponent: () => import('./features/admin/audit.component').then(m => m.AuditComponent), title: 'Auditoria' },
        ],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
