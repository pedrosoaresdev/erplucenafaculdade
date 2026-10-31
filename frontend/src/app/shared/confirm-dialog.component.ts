import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface ConfirmData {
  title: string; message: string; confirmLabel?: string; danger?: boolean;
  /** Campos de texto obrigatórios (ex.: motivo). */
  fields?: { key: string; label: string; minLength?: number; multiline?: boolean }[];
}

@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule, FormsModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
      @for (f of data.fields ?? []; track f.key) {
        <mat-form-field class="full">
          <mat-label>{{ f.label }}</mat-label>
          @if (f.multiline) { <textarea matInput rows="3" [(ngModel)]="values[f.key]" required></textarea> }
          @else { <input matInput [(ngModel)]="values[f.key]" required> }
          @if (f.minLength) { <mat-hint>Mínimo {{ f.minLength }} caracteres</mat-hint> }
        </mat-form-field>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancelar</button>
      <button mat-flat-button [class.danger]="data.danger" [disabled]="!valid()" [mat-dialog-close]="result()">{{ data.confirmLabel ?? 'Confirmar' }}</button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmData>(MAT_DIALOG_DATA);
  values: Record<string, string> = {};

  valid(): boolean {
    return (this.data.fields ?? []).every(f => (this.values[f.key] ?? '').trim().length >= (f.minLength ?? 1));
  }

  result(): Record<string, string> | true {
    return this.data.fields?.length ? this.values : true;
  }
}
