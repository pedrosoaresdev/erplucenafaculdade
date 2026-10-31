import { Component, DOCUMENT, HostListener, effect, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

interface A11yPrefs {
  fontScale: number; contrast: 'normal' | 'high' | 'negative'; grayscale: boolean;
  highlightLinks: boolean; reduceMotion: boolean; readableFont: boolean;
}
const DEFAULTS: A11yPrefs = { fontScale: 100, contrast: 'normal', grayscale: false, highlightLinks: false, reduceMotion: false, readableFont: false };
const KEY = 'erp.a11y';

/**
 * Botão flutuante de acessibilidade (WCAG 2.2 AA). As preferências são conveniência do próprio
 * visitante e ficam no navegador (não são dados do ERP).
 */
@Component({
  selector: 'app-accessibility',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <button mat-fab class="a11y-fab" (click)="open.set(!open())" [attr.aria-expanded]="open()" aria-controls="a11y-panel"
            aria-label="Opções de acessibilidade" matTooltip="Acessibilidade" matTooltipPosition="left">
      <mat-icon aria-hidden="true">accessibility_new</mat-icon>
    </button>
    @if (open()) {
      <section id="a11y-panel" class="a11y-panel mat-elevation-z8" role="dialog" aria-label="Acessibilidade">
        <header><h2>Acessibilidade</h2>
          <button mat-icon-button (click)="open.set(false)" aria-label="Fechar painel de acessibilidade"><mat-icon>close</mat-icon></button>
        </header>
        <div class="a11y-row" role="group" aria-label="Tamanho da fonte">
          <button mat-stroked-button (click)="font(-10)" aria-label="Diminuir fonte">A-</button>
          <span aria-live="polite">{{ prefs().fontScale }}%</span>
          <button mat-stroked-button (click)="font(10)" aria-label="Aumentar fonte">A+</button>
        </div>
        <button mat-stroked-button [class.on]="prefs().contrast==='high'" [attr.aria-pressed]="prefs().contrast==='high'" (click)="contrast('high')"><mat-icon>contrast</mat-icon> Alto contraste</button>
        <button mat-stroked-button [class.on]="prefs().contrast==='negative'" [attr.aria-pressed]="prefs().contrast==='negative'" (click)="contrast('negative')"><mat-icon>invert_colors</mat-icon> Contraste negativo</button>
        <button mat-stroked-button [class.on]="prefs().grayscale" [attr.aria-pressed]="prefs().grayscale" (click)="toggle('grayscale')"><mat-icon>filter_b_and_w</mat-icon> Escala de cinza</button>
        <button mat-stroked-button [class.on]="prefs().highlightLinks" [attr.aria-pressed]="prefs().highlightLinks" (click)="toggle('highlightLinks')"><mat-icon>link</mat-icon> Destacar links</button>
        <button mat-stroked-button [class.on]="prefs().readableFont" [attr.aria-pressed]="prefs().readableFont" (click)="toggle('readableFont')"><mat-icon>text_fields</mat-icon> Fonte legível</button>
        <button mat-stroked-button [class.on]="prefs().reduceMotion" [attr.aria-pressed]="prefs().reduceMotion" (click)="toggle('reduceMotion')"><mat-icon>motion_photos_off</mat-icon> Reduzir animações</button>
        <button mat-flat-button (click)="reset()"><mat-icon>restart_alt</mat-icon> Restaurar padrão</button>
        <p class="a11y-hint">Navegação por teclado: use Tab / Shift+Tab; Alt+A abre este painel.</p>
      </section>
    }
  `,
})
export class AccessibilityComponent {
  private doc = inject(DOCUMENT);
  readonly open = signal(false);
  readonly prefs = signal<A11yPrefs>(this.load());

  constructor() {
    effect(() => this.apply(this.prefs()));
  }

  @HostListener('document:keydown', ['$event'])
  onKey(e: KeyboardEvent) {
    if (e.altKey && (e.key === 'a' || e.key === 'A')) { e.preventDefault(); this.open.set(!this.open()); }
    if (e.key === 'Escape' && this.open()) this.open.set(false);
  }

  font(delta: number) { this.update({ fontScale: Math.min(200, Math.max(80, this.prefs().fontScale + delta)) }); }
  contrast(c: 'high' | 'negative') { this.update({ contrast: this.prefs().contrast === c ? 'normal' : c }); }
  toggle(k: 'grayscale' | 'highlightLinks' | 'reduceMotion' | 'readableFont') { this.update({ [k]: !this.prefs()[k] } as Partial<A11yPrefs>); }
  reset() { this.prefs.set({ ...DEFAULTS }); }

  private update(p: Partial<A11yPrefs>) { this.prefs.set({ ...this.prefs(), ...p }); }

  private apply(p: A11yPrefs) {
    const html = this.doc.documentElement;
    html.style.fontSize = `${p.fontScale}%`;
    html.classList.toggle('a11y-high-contrast', p.contrast === 'high');
    html.classList.toggle('a11y-negative', p.contrast === 'negative');
    html.classList.toggle('a11y-grayscale', p.grayscale);
    html.classList.toggle('a11y-links', p.highlightLinks);
    html.classList.toggle('a11y-reduce-motion', p.reduceMotion);
    html.classList.toggle('a11y-readable', p.readableFont);
    try { localStorage.setItem(KEY, JSON.stringify(p)); } catch { /* armazenamento indisponível */ }
  }

  private load(): A11yPrefs {
    try { return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(KEY) ?? '{}') }; } catch { return { ...DEFAULTS }; }
  }
}
