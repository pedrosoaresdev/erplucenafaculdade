import { Component, computed, input } from '@angular/core';

/** Estimativa visual de força (orientativa; a validação real é feita no backend). */
export function passwordScore(p: string): number {
  if (!p) return 0;
  let s = 0;
  if (p.length >= 8) s++;
  if (p.length >= 12) s++;
  if (/[a-z]/.test(p) && /[A-Z]/.test(p)) s++;
  if (/\d/.test(p)) s++;
  if (/[^A-Za-z0-9]/.test(p)) s++;
  if (/^(.)\1+$/.test(p) || /^(12345|abcde|qwert|senha|password)/i.test(p)) s = Math.min(s, 1);
  return Math.min(s, 4);
}

@Component({
  selector: 'app-password-strength',
  template: `
    <div class="strength" [attr.data-level]="score()">
      <div class="bars" aria-hidden="true">@for (i of [1,2,3,4]; track i) {<span [class.on]="score() >= i"></span>}</div>
      <span class="label" aria-live="polite">Força da senha: <strong>{{ label() }}</strong></span>
    </div>
  `,
})
export class PasswordStrengthComponent {
  readonly password = input<string>('');
  readonly score = computed(() => passwordScore(this.password()));
  readonly label = computed(() => ['Muito fraca', 'Fraca', 'Razoável', 'Boa', 'Forte'][this.score()]);
}
