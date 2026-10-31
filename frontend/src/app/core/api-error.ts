import { HttpErrorResponse } from '@angular/common/http';

/** Extrai a mensagem amigável de um ProblemDetail (RFC 9457) retornado pela API. */
export function apiMessage(err: unknown, fallback = 'Não foi possível concluir a operação.'): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Servidor indisponível. Verifique sua conexão.';
    const body = err.error;
    if (body && typeof body === 'object') {
      if (body.fields && typeof body.fields === 'object') {
        const first = Object.entries(body.fields)[0];
        if (first) return `${body.detail ?? 'Dados inválidos'}: ${first[0]} — ${first[1]}`;
      }
      if (typeof body.detail === 'string') return body.detail;
    }
  }
  return fallback;
}

export function apiCode(err: unknown): string | undefined {
  return err instanceof HttpErrorResponse && err.error && typeof err.error === 'object' ? err.error.code : undefined;
}
