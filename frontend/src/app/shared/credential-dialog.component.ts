import { Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** Exibe uma senha temporária uma única vez (não é armazenada em claro no servidor). */
@Component({
  selector: 'app-credential-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Acesso gerado</h2>
    <mat-dialog-content>
      <p class="alert warn" role="alert"><mat-icon aria-hidden="true">warning</mat-icon> Esta senha temporária será exibida <strong>somente agora</strong>.
        Entregue-a ao usuário por canal seguro. No primeiro acesso ele configurará o Google Authenticator e definirá uma nova senha.</p>
      <dl class="info-list">
        @if (data.login) { <dt>Endereço de acesso</dt><dd><code>{{ data.login }}</code></dd> }
        <dt>Usuário</dt><dd><code>{{ data.username }}</code></dd>
        <dt>Senha temporária</dt><dd><code class="secret-value">{{ data.password }}</code></dd>
      </dl>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button (click)="copy()"><mat-icon>content_copy</mat-icon> {{ copied() ? 'Copiado!' : 'Copiar' }}</button>
      <button mat-flat-button mat-dialog-close>Concluir</button>
    </mat-dialog-actions>
  `,
})
export class CredentialDialogComponent {
  readonly data = inject<{ username: string; password: string; login?: string }>(MAT_DIALOG_DATA);
  readonly copied = signal(false);
  copy(): void {
    navigator.clipboard.writeText(`Usuário: ${this.data.username}\nSenha temporária: ${this.data.password}`).then(() => this.copied.set(true), () => {});
  }
}
