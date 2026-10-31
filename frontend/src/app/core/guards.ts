import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/** Exige sessão válida; usuários com senha temporária são levados à troca obrigatória. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(map(ok => {
    if (!ok) {
      const slug = auth.lastFacultySlug;
      return router.createUrlTree(slug ? ['/f', slug, 'login'] : ['/login']);
    }
    if (auth.profile()?.mustChangePassword && !state.url.startsWith('/perfil')) {
      return router.createUrlTree(['/perfil'], { queryParams: { aba: 'senha', obrigatorio: 1 } });
    }
    return true;
  }));
};

/** Exige ao menos uma das permissões informadas (a API também valida no backend). */
export const permissionGuard = (...perms: string[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.hasPermission(...perms) ? true : router.createUrlTree(['/inicio'], { queryParams: { negado: 1 } });
};

/** Exige a permissão no próprio perfil (portais do aluno/professor não se aplicam ao administrador Master). */
export const ownPermissionGuard = (perm: string): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.profile()?.permissions.includes(perm) ? true : router.createUrlTree(['/inicio'], { queryParams: { negado: 1 } });
};
