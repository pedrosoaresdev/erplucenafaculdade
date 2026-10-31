import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('AuthService + interceptor', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let client: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  it('mantém o token só em memória e anexa o Bearer', () => {
    auth.establish({ accessToken: 'abc', tokenType: 'Bearer', expiresIn: 900, mustChangePassword: false }).subscribe();
    const me = http.expectOne('/api/me');
    expect(me.request.headers.get('Authorization')).toBe('Bearer abc');
    me.flush({ id: '1', permissions: ['USER_VIEW'], platformAdmin: false, roles: [] });
    expect(auth.hasPermission('USER_VIEW')).toBeTrue();
    expect(auth.hasPermission('FINANCE_MANAGE')).toBeFalse();
    expect(JSON.stringify(localStorage)).not.toContain('abc');
  });

  it('em 401 renova a sessão uma vez e repete a requisição', () => {
    auth.establish({ accessToken: 'old', tokenType: 'Bearer', expiresIn: 900, mustChangePassword: false }).subscribe();
    http.expectOne('/api/me').flush({ id: '1', permissions: [], platformAdmin: false, roles: [] });

    let result: unknown;
    client.get('/api/admin/users').subscribe(r => (result = r));
    http.expectOne('/api/admin/users').flush({}, { status: 401, statusText: 'Unauthorized' });
    const refresh = http.expectOne('/api/auth/refresh');
    expect(refresh.request.headers.get('X-ERP-Client')).toBe('web');
    expect(refresh.request.withCredentials).toBeTrue();
    refresh.flush({ accessToken: 'new', tokenType: 'Bearer', expiresIn: 900, mustChangePassword: false });
    http.expectOne('/api/me').flush({ id: '1', permissions: [], platformAdmin: false, roles: [] });
    const retry = http.expectOne('/api/admin/users');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new');
    retry.flush({ ok: true });
    expect(result).toEqual({ ok: true });
  });

  it('não anexa token em rotas de autenticação', () => {
    auth.establish({ accessToken: 'abc', tokenType: 'Bearer', expiresIn: 900, mustChangePassword: false }).subscribe();
    http.expectOne('/api/me').flush({ id: '1', permissions: [], platformAdmin: false, roles: [] });
    auth.login('fac', 'aluno', 'x').subscribe();
    expect(http.expectOne('/api/auth/login').request.headers.has('Authorization')).toBeFalse();
  });
});
