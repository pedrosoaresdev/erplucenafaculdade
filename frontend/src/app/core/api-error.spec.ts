import { HttpErrorResponse } from '@angular/common/http';
import { apiCode, apiMessage } from './api-error';

describe('apiMessage', () => {
  it('usa o detail do ProblemDetail', () => {
    const e = new HttpErrorResponse({ status: 401, error: { detail: 'Código inválido', code: 'INVALID_CODE' } });
    expect(apiMessage(e)).toBe('Código inválido');
    expect(apiCode(e)).toBe('INVALID_CODE');
  });
  it('mostra o primeiro campo inválido', () => {
    const e = new HttpErrorResponse({ status: 400, error: { detail: 'Dados inválidos', fields: { email: 'inválido' } } });
    expect(apiMessage(e)).toContain('email');
  });
  it('trata servidor indisponível', () => {
    expect(apiMessage(new HttpErrorResponse({ status: 0 }))).toContain('indisponível');
  });
});
