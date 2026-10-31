import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RoleView, UserView } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { facultyParam } from '../../shared/faculty-picker.component';

@Component({
  selector: 'app-user-form',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>{{ data.user ? 'Editar usuário' : 'Novo usuário' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content>
        @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
        <mat-form-field class="full"><mat-label>Nome de usuário</mat-label><input matInput formControlName="username" autocapitalize="none" required>
          <mat-hint>Aluno: use o número de matrícula</mat-hint></mat-form-field>
        <mat-form-field class="full"><mat-label>Nome completo</mat-label><input matInput formControlName="fullName" required></mat-form-field>
        <mat-form-field class="full"><mat-label>E-mail</mat-label><input matInput type="email" formControlName="email" required></mat-form-field>
        <mat-form-field class="full"><mat-label>Telefone</mat-label><input matInput type="tel" formControlName="phone"></mat-form-field>
        <mat-form-field class="full"><mat-label>Cargo(s)</mat-label>
          <mat-select formControlName="roleIds" multiple required>
            @for (r of roles(); track r.id) { <mat-option [value]="r.id">{{ r.name }}</mat-option> }
          </mat-select></mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="form.invalid || loading()">Salvar</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class UserFormComponent implements OnInit {
  readonly data = inject<{ facultyId: string | null; user?: UserView }>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<UserFormComponent>);
  private http = inject(HttpClient);
  readonly roles = signal<RoleView[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{3,60}$/)]],
    fullName: ['', Validators.required], email: ['', [Validators.required, Validators.email]], phone: [''],
    roleIds: [[] as string[], Validators.required],
  });

  ngOnInit(): void {
    this.http.get<RoleView[]>('/api/admin/roles', { params: facultyParam(this.data.facultyId) }).subscribe(r => this.roles.set(r));
    const u = this.data.user;
    if (u) {
      this.form.patchValue({ username: u.username, fullName: u.fullName, email: u.email, phone: u.phone ?? '', roleIds: u.roles.map(r => r.id) });
      this.form.controls.username.disable();
    }
  }

  save(): void {
    this.loading.set(true);
    const v = this.form.getRawValue();
    const params = facultyParam(this.data.facultyId);
    const u = this.data.user;
    const done = { next: (r: unknown) => this.ref.close(r), error: (e: unknown) => { this.loading.set(false); this.error.set(apiMessage(e)); } };
    if (!u) { this.http.post('/api/admin/users', v, { params }).subscribe(done); return; }
    this.http.put(`/api/admin/users/${u.id}`, { fullName: v.fullName, email: v.email, phone: v.phone }, { params }).subscribe({
      next: () => {
        const changed = [...v.roleIds].sort().join() !== u.roles.map(r => r.id).sort().join();
        if (!changed) { this.ref.close(true); return; }
        this.http.put(`/api/admin/users/${u.id}/roles`, { roleIds: v.roleIds }, { params }).subscribe(done);
      },
      error: done.error,
    });
  }
}
