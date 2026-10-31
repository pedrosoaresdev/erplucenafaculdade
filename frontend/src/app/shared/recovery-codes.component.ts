import { Component, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';

/** Exibição única dos códigos de recuperação, com impressão, download e cópia. */
@Component({
  selector: 'app-recovery-codes',
  imports: [MatButtonModule, MatIconModule, MatCheckboxModule],
  template: `
    <div class="recovery">
      <p class="warn" role="alert"><mat-icon aria-hidden="true">warning</mat-icon>
        Guarde estes códigos em local seguro. Eles serão exibidos <strong>apenas agora</strong>. Cada código pode ser usado uma única vez
        caso você perca acesso ao Google Authenticator.</p>
      <ol class="codes" aria-label="Códigos de recuperação">
        @for (c of codes(); track c) { <li><code>{{ c }}</code></li> }
      </ol>
      <div class="actions">
        <button mat-stroked-button type="button" (click)="print()"><mat-icon>print</mat-icon> Imprimir</button>
        <button mat-stroked-button type="button" (click)="download()"><mat-icon>download</mat-icon> Baixar .txt</button>
        <button mat-stroked-button type="button" (click)="copy()"><mat-icon>content_copy</mat-icon> {{ copied() ? 'Copiado!' : 'Copiar' }}</button>
      </div>
      <mat-checkbox [checked]="saved()" (change)="saved.set($event.checked)">Guardei meus códigos em local seguro</mat-checkbox>
      <button mat-flat-button type="button" [disabled]="!saved()" (click)="done.emit()">Continuar</button>
    </div>
  `,
})
export class RecoveryCodesComponent {
  readonly codes = input.required<string[]>();
  readonly account = input<string>('');
  readonly done = output<void>();
  readonly saved = signal(false);
  readonly copied = signal(false);

  private text(): string {
    return `ERP Universitário — códigos de recuperação\nConta: ${this.account()}\nGerados em: ${new Date().toLocaleString('pt-BR')}\n\n`
      + this.codes().join('\n') + '\n\nCada código pode ser usado uma única vez.\n';
  }

  download() {
    const url = URL.createObjectURL(new Blob([this.text()], { type: 'text/plain;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url; a.download = 'codigos-recuperacao-erp.txt'; a.click();
    URL.revokeObjectURL(url);
  }

  print() {
    const w = window.open('', '_blank', 'noopener,width=480,height=640');
    if (!w) return;
    const pre = w.document.createElement('pre');
    pre.textContent = this.text();
    pre.style.font = '16px monospace';
    w.document.body.appendChild(pre);
    w.print();
  }

  async copy() {
    try { await navigator.clipboard.writeText(this.codes().join('\n')); this.copied.set(true); } catch { this.copied.set(false); }
  }
}
