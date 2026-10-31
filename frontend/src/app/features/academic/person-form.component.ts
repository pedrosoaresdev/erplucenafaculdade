import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { EmployeeView, TeacherView, formatCpf } from '../../core/academic.models';
import { RoleView } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { cpfValidator } from '../../shared/validators';
import { AcademicApi } from './academic-api.service';

export interface PersonFormData { kind: 'teacher' | 'employee'; record?: TeacherView | EmployeeView; }

/** Cadastro de professor ou funcionário, com geração opcional de acesso. */
@Component({
  selector: 'app-person-form',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>{{ data.record ? 'Editar' : 'Novo' }} {{ teacher ? 'professor' : 'funcionário' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content class="form-grid">
        @if (error()) { <p class="alert error span-2" role="alert">{{ error() }}</p> }
        <mat-form-field class="span-2"><mat-label>Nome completo</mat-label><input matInput formControlName="fullName" required></mat-form-field>
        <mat-form-field><mat-label>CPF</mat-label><input matInput formControlName="cpf" inputmode="numeric" required>
          @if (form.controls.cpf.touched && form.controls.cpf.hasError('cpf')) { <mat-error>CPF inválido</mat-error> }</mat-form-field>
        <mat-form-field><mat-label>E-mail</mat-label><input matInput type="email" formControlName="email"></mat-form-field>
        <mat-form-field><mat-label>Telefone</mat-label><input matInput type="tel" formControlName="phone"></mat-form-field>
        @if (teacher) {
          <mat-form-field><mat-label>Titulação</mat-label><mat-select formControlName="academicDegree">
            <mat-option [value]="null">—</mat-option>
            @for (t of degrees; track t) { <mat-option [value]="t">{{ t }}</mat-option> }</mat-select></mat-form-field>
        } @else {
          <mat-form-field><mat-label>Setor</mat-label><input matInput formControlName="department"></mat-form-field>
          <mat-form-field><mat-label>Função</mat-label><input matInput formControlName="jobTitle"></mat-form-field>
        }
        <mat-form-field><mat-label>Situação</mat-label><mat-select formControlName="status">
          <mat-option value="ACTIVE">Ativo</mat-option><mat-option value="INACTIVE">Inativo</mat-option></mat-select></mat-form-field>
        @if (!data.record?.hasAccess) {
          <fieldset class="span-2">
            <legend>Acesso ao sistema (opcional)</legend>
            <div class="form-grid">
              <mat-form-field><mat-label>Nome de usuário</mat-label><input matInput formControlName="username" autocapitalize="none">
                <mat-hint>{{ teacher ? 'Recebe o cargo Professor' : 'Em branco: sem acesso' }}</mat-hint></mat-form-field>
              @if (!teacher) {
                <mat-form-field><mat-label>Cargo(s)</mat-label><mat-select formControlName="roleIds" multiple>
                  @for (r of roles(); track r.id) { <mat-option [value]="r.id">{{ r.name }}</mat-option> }</mat-select></mat-form-field>
              }
            </div>
            <p class="muted small">É gerada uma senha temporária (exibida uma vez); o e-mail é obrigatório para criar o acesso.</p>
          </fieldset>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="loading()">Salvar</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class PersonFormComponent implements OnInit {
  readonly data = inject<PersonFormData>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<PersonFormComponent>);
  private api = inject(AcademicApi);
  private http = inject(HttpClient);
  readonly teacher = this.data.kind === 'teacher';
  readonly degrees = ['Graduação', 'Especialização', 'Mestrado', 'Doutorado', 'Pós-doutorado'];
  readonly roles = signal<RoleView[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).group({
    fullName: ['', [Validators.required, Validators.maxLength(200)]], cpf: ['', [Validators.required, cpfValidator]],
    email: ['', Validators.email], phone: [''], academicDegree: [null as string | null], department: [''], jobTitle: [''],
    status: ['ACTIVE'], username: ['', Validators.pattern(/^([A-Za-z0-9._-]{3,60})?$/)], roleIds: [[] as string[]],
  });

  ngOnInit(): void {
    if (!this.teacher) {
      const fid = this.api.selected.id();
      this.http.get<RoleView[]>('/api/admin/roles', { params: fid ? { facultyId: fid } : {} })
        .subscribe({ next: r => this.roles.set(r.filter(x => !['ALUNO', 'PROFESSOR'].includes(x.code))), error: () => {} });
    }
    const r = this.data.record;
    if (r) this.form.patchValue({ ...(r as object), cpf: formatCpf(r.cpf), username: '' } as never);
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); this.error.set('Verifique os campos destacados.'); return; }
    const v = this.form.getRawValue();
    if (v.username && !v.email) { this.error.set('Informe o e-mail para gerar o acesso.'); return; }
    const body: Record<string, unknown> = { ...v };
    for (const k of Object.keys(body)) if (body[k] === '') body[k] = null;
    const path = this.teacher ? '/teachers' : '/employees';
    this.loading.set(true);
    const req = this.data.record ? this.api.put(`${path}/${this.data.record.id}`, body) : this.api.post(path, body);
    req.subscribe({
      next: res => {
        // Edição de cadastro sem acesso + usuário informado: gera o acesso em seguida
        if (this.data.record && v.username) {
          this.api.post(`${path}/${this.data.record.id}/access`, { username: v.username, roleIds: v.roleIds }).subscribe({
            next: a => this.ref.close(a), error: e => { this.loading.set(false); this.error.set(apiMessage(e)); },
          });
        } else this.ref.close(res);
      },
      error: e => { this.loading.set(false); this.error.set(apiMessage(e)); },
    });
  }
}
