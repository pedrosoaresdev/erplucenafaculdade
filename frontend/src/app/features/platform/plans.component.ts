import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { PlanView } from '../../core/models';
import { apiMessage } from '../../core/api-error';

@Component({
  selector: 'app-plans',
  imports: [CurrencyPipe, ReactiveFormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule],
  template: `
    <header class="page-header"><div><h1>Planos</h1><p class="muted">Planos comerciais atribuídos às faculdades.</p></div></header>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <div class="two-col">
      <table class="data-table responsive">
        <thead><tr><th scope="col">Plano</th><th scope="col">Alunos</th><th scope="col">Usuários</th><th scope="col">Mensalidade</th><th scope="col">Situação</th><th scope="col"><span class="sr-only">Ações</span></th></tr></thead>
        <tbody>@for (p of plans(); track p.id) {
          <tr><td data-label="Plano"><strong>{{ p.name }}</strong> <span class="muted small">{{ p.code }}</span></td>
            <td data-label="Alunos">{{ p.maxStudents ?? 'Ilimitado' }}</td><td data-label="Usuários">{{ p.maxUsers ?? 'Ilimitado' }}</td>
            <td data-label="Mensalidade">{{ p.monthlyPrice | currency:'BRL' }}</td>
            <td data-label="Situação"><span class="badge" [class]="p.active ? 'active' : 'inactive'">{{ p.active ? 'Ativo' : 'Inativo' }}</span></td>
            <td class="actions"><button mat-icon-button (click)="edit(p)" [attr.aria-label]="'Editar ' + p.name"><mat-icon>edit</mat-icon></button></td></tr>
        }</tbody>
      </table>
      <mat-card>
        <mat-card-header><mat-card-title>{{ editing() ? 'Editar plano' : 'Novo plano' }}</mat-card-title></mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="save()">
            <mat-form-field class="full"><mat-label>Código</mat-label><input matInput formControlName="code" required><mat-hint>MAIÚSCULAS e _</mat-hint></mat-form-field>
            <mat-form-field class="full"><mat-label>Nome</mat-label><input matInput formControlName="name" required></mat-form-field>
            <mat-form-field class="full"><mat-label>Limite de alunos (vazio = ilimitado)</mat-label><input matInput type="number" min="1" formControlName="maxStudents"></mat-form-field>
            <mat-form-field class="full"><mat-label>Limite de usuários (vazio = ilimitado)</mat-label><input matInput type="number" min="1" formControlName="maxUsers"></mat-form-field>
            <mat-form-field class="full"><mat-label>Valor mensal (R$)</mat-label><input matInput type="number" min="0" step="0.01" formControlName="monthlyPrice" required></mat-form-field>
            <mat-checkbox formControlName="active">Ativo</mat-checkbox>
            <div class="row-actions"><button mat-flat-button type="submit" [disabled]="form.invalid">Salvar</button>
              @if (editing()) { <button mat-button type="button" (click)="reset()">Cancelar</button> }</div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
})
export class PlansComponent implements OnInit {
  private http = inject(HttpClient);
  readonly plans = signal<PlanView[]>([]);
  readonly editing = signal<PlanView | null>(null);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Z0-9_]{2,40}$/)]], name: ['', Validators.required],
    maxStudents: [null as number | null], maxUsers: [null as number | null], monthlyPrice: [0, [Validators.required, Validators.min(0)]], active: [true],
  });

  ngOnInit(): void { this.load(); }
  load(): void { this.http.get<PlanView[]>('/api/platform/plans').subscribe(p => this.plans.set(p)); }
  edit(p: PlanView): void { this.editing.set(p); this.form.patchValue(p); }
  reset(): void { this.editing.set(null); this.form.reset({ active: true, monthlyPrice: 0 }); }

  save(): void {
    const e = this.editing();
    const req = e ? this.http.put(`/api/platform/plans/${e.id}`, this.form.getRawValue()) : this.http.post('/api/platform/plans', this.form.getRawValue());
    req.subscribe({ next: () => { this.reset(); this.load(); }, error: err => this.error.set(apiMessage(err)) });
  }
}
