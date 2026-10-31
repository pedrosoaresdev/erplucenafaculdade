import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { StudentView, formatCpf } from '../../core/academic.models';
import { apiMessage } from '../../core/api-error';
import { cpfValidator } from '../../shared/validators';
import { AcademicApi } from './academic-api.service';

@Component({
  selector: 'app-student-form',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Editar aluno — ' + data.registration : 'Novo aluno' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content class="form-grid">
        @if (error()) { <p class="alert error span-2" role="alert">{{ error() }}</p> }
        <mat-form-field class="span-2"><mat-label>Nome completo</mat-label><input matInput formControlName="fullName" required></mat-form-field>
        <mat-form-field><mat-label>Nome social</mat-label><input matInput formControlName="socialName"></mat-form-field>
        <mat-form-field><mat-label>CPF</mat-label><input matInput formControlName="cpf" inputmode="numeric" required>
          @if (form.controls.cpf.touched && form.controls.cpf.hasError('cpf')) { <mat-error>CPF inválido</mat-error> }</mat-form-field>
        <mat-form-field><mat-label>RG</mat-label><input matInput formControlName="rg"></mat-form-field>
        <mat-form-field><mat-label>Data de nascimento</mat-label><input matInput type="date" formControlName="birthDate"></mat-form-field>
        <mat-form-field><mat-label>E-mail</mat-label><input matInput type="email" formControlName="email" required></mat-form-field>
        <mat-form-field><mat-label>Telefone</mat-label><input matInput type="tel" formControlName="phone"></mat-form-field>
        <mat-form-field class="span-2"><mat-label>Logradouro</mat-label><input matInput formControlName="addressStreet"></mat-form-field>
        <mat-form-field><mat-label>Número</mat-label><input matInput formControlName="addressNumber"></mat-form-field>
        <mat-form-field><mat-label>Bairro</mat-label><input matInput formControlName="addressDistrict"></mat-form-field>
        <mat-form-field><mat-label>Cidade</mat-label><input matInput formControlName="addressCity"></mat-form-field>
        <mat-form-field><mat-label>UF</mat-label><input matInput formControlName="addressState" maxlength="2"></mat-form-field>
        <mat-form-field><mat-label>CEP</mat-label><input matInput formControlName="addressZip" inputmode="numeric"></mat-form-field>
        @if (!data) {
          <mat-form-field><mat-label>Matrícula (opcional)</mat-label><input matInput formControlName="registration">
            <mat-hint>Em branco: gerada automaticamente (ano + sequência)</mat-hint></mat-form-field>
          <mat-checkbox class="span-2" formControlName="createAccess">Gerar acesso ao portal (usuário = matrícula, senha temporária)</mat-checkbox>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="loading()">Salvar</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class StudentFormComponent implements OnInit {
  readonly data = inject<StudentView | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<StudentFormComponent>);
  private api = inject(AcademicApi);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(200)]], socialName: [''], cpf: ['', [Validators.required, cpfValidator]],
    rg: [''], birthDate: [''], email: ['', [Validators.required, Validators.email]], phone: [''],
    addressStreet: [''], addressNumber: [''], addressDistrict: [''], addressCity: [''],
    addressState: ['', Validators.pattern(/^([A-Za-z]{2})?$/)], addressZip: ['', Validators.pattern(/^(\d{5}-?\d{3})?$/)],
    registration: ['', Validators.pattern(/^([A-Za-z0-9._-]{3,30})?$/)], createAccess: [true],
  });

  ngOnInit(): void {
    if (this.data) {
      const d = this.data;
      this.form.patchValue({ ...Object.fromEntries(Object.entries(d).map(([k, v]) => [k, v ?? ''])), cpf: formatCpf(d.cpf) } as never);
    }
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); this.error.set('Verifique os campos destacados.'); return; }
    const v = this.form.getRawValue();
    const body: Record<string, unknown> = { ...v };
    for (const k of Object.keys(body)) if (body[k] === '') body[k] = null;
    if (this.data) { body['createAccess'] = false; body['registration'] = this.data.registration; }
    this.loading.set(true);
    const req = this.data ? this.api.put(`/students/${this.data.id}`, body) : this.api.post('/students', body);
    req.subscribe({ next: r => this.ref.close(r), error: e => { this.loading.set(false); this.error.set(apiMessage(e)); } });
  }
}
