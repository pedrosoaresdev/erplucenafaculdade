import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Valida dígitos verificadores do CPF (o backend valida novamente). */
export function cpfValidator(c: AbstractControl): ValidationErrors | null {
  const v = String(c.value ?? '').replace(/\D/g, '');
  if (!v) return null;
  if (v.length !== 11 || /^(\d)\1+$/.test(v)) return { cpf: true };
  for (let t = 9; t < 11; t++) {
    let sum = 0;
    for (let i = 0; i < t; i++) sum += Number(v[i]) * (t + 1 - i);
    if (((sum * 10) % 11) % 10 !== Number(v[t])) return { cpf: true };
  }
  return null;
}
