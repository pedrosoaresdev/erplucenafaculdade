import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const PUBLIC = ['/api/auth/', '/api/public/', '/api/setup/'];

/** Anexa o Bearer token e, em caso de 401, tenta renovar a sessão uma única vez. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  if (PUBLIC.some(p => req.url.startsWith(p))) return next(req);
  const withToken = () => {
    const t = auth.accessToken();
    return t ? req.clone({ setHeaders: { Authorization: `Bearer ${t}` } }) : req;
  };
  return next(withToken()).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        return auth.refresh().pipe(switchMap(ok => {
          if (ok) return next(withToken());
          auth.endLocalSession('Sua sessão expirou. Entre novamente.');
          return throwError(() => err);
        }));
      }
      return throwError(() => err);
    })
  );
};
