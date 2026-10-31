import { Component, OnInit, inject, input, model, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { FacultyView, Page } from '../core/models';
import { AuthService } from '../core/auth.service';

/** Seletor de faculdade exibido apenas ao administrador Master (demais usuários operam na própria instituição). */
@Component({
  selector: 'app-faculty-picker',
  imports: [FormsModule, MatFormFieldModule, MatSelectModule],
  template: `
    @if (auth.profile()?.platformAdmin) {
      <mat-form-field class="faculty-picker">
        <mat-label>Faculdade</mat-label>
        <mat-select [ngModel]="facultyId()" (ngModelChange)="facultyId.set($event)">
          @if (allowAll()) { <mat-option [value]="null">Todas (visão global)</mat-option> }
          @for (f of faculties(); track f.id) { <mat-option [value]="f.id">{{ f.name }}</mat-option> }
        </mat-select>
      </mat-form-field>
    }
  `,
})
export class FacultyPickerComponent implements OnInit {
  readonly auth = inject(AuthService);
  private http = inject(HttpClient);
  readonly facultyId = model<string | null>(null);
  readonly faculties = signal<FacultyView[]>([]);
  /** Permite a opção "Todas" (visão global, ex.: auditoria). */
  readonly allowAll = input(false);

  ngOnInit(): void {
    if (!this.auth.profile()?.platformAdmin) return;
    this.http.get<Page<FacultyView>>('/api/platform/faculties', { params: { size: 100 } }).subscribe(p => {
      this.faculties.set(p.content);
      if (!this.facultyId() && !this.allowAll() && p.content.length) this.facultyId.set(p.content[0].id);
    });
  }
}

/** Parâmetro facultyId apenas quando o Master escolheu uma faculdade. */
export function facultyParam(id: string | null): Record<string, string> {
  return id ? { facultyId: id } : {};
}
