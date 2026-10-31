import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { FacultyView, PlanView } from '../../core/models';
import { apiMessage } from '../../core/api-error';

export interface CreatedFaculty { faculty: FacultyView; initialAdminUsername?: string; initialAdminTemporaryPassword?: string; }

@Component({
  selector: 'app-faculty-form',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Editar faculdade' : 'Nova faculdade' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content class="form-grid">
        @if (error()) { <p class="alert error span-2" role="alert">{{ error() }}</p> }
        <mat-form-field><mat-label>Nome da faculdade</mat-label><input matInput formControlName="name" required></mat-form-field>
        <mat-form-field><mat-label>Identificador (endereço de login)</mat-label><input matInput formControlName="slug" required>
          <mat-hint>/f/{{ form.value.slug || 'identificador' }}/login</mat-hint></mat-form-field>
        <mat-form-field><mat-label>Razão social</mat-label><input matInput formControlName="legalName" required></mat-form-field>
        <mat-form-field><mat-label>Nome fantasia</mat-label><input matInput formControlName="tradeName"></mat-form-field>
        <mat-form-field><mat-label>CNPJ</mat-label><input matInput formControlName="cnpj" required inputmode="numeric"></mat-form-field>
        <mat-form-field><mat-label>E-mail institucional</mat-label><input matInput type="email" formControlName="email" required></mat-form-field>
        <mat-form-field><mat-label>Telefone</mat-label><input matInput formControlName="phone" type="tel"></mat-form-field>
        <mat-form-field><mat-label>Plano contratado</mat-label>
          <mat-select formControlName="planId"><mat-option [value]="null">—</mat-option>
            @for (p of plans(); track p.id) { <mat-option [value]="p.id">{{ p.name }}</mat-option> }</mat-select></mat-form-field>
        <mat-form-field><mat-label>Logradouro</mat-label><input matInput formControlName="addressStreet"></mat-form-field>
        <mat-form-field><mat-label>Número</mat-label><input matInput formControlName="addressNumber"></mat-form-field>
        <mat-form-field><mat-label>Complemento</mat-label><input matInput formControlName="addressComplement"></mat-form-field>
        <mat-form-field><mat-label>Bairro</mat-label><input matInput formControlName="addressDistrict"></mat-form-field>
        <mat-form-field><mat-label>Cidade</mat-label><input matInput formControlName="addressCity"></mat-form-field>
        <mat-form-field><mat-label>UF</mat-label><input matInput formControlName="addressState" maxlength="2"></mat-form-field>
        <mat-form-field><mat-label>CEP</mat-label><input matInput formControlName="addressZip" inputmode="numeric"></mat-form-field>
        <mat-form-field><mat-label>URL do logotipo (https)</mat-label><input matInput formControlName="logoUrl" type="url"></mat-form-field>
        @if (!data) {
          <fieldset class="span-2" formGroupName="initialAdmin">
            <legend><mat-checkbox [checked]="withAdmin()" (change)="toggleAdmin($event.checked)">Criar Diretor Geral inicial</mat-checkbox></legend>
            @if (withAdmin()) {
              <div class="form-grid">
                <mat-form-field><mat-label>Usuário</mat-label><input matInput formControlName="username"></mat-form-field>
                <mat-form-field><mat-label>Nome completo</mat-label><input matInput formControlName="fullName"></mat-form-field>
                <mat-form-field><mat-label>E-mail</mat-label><input matInput type="email" formControlName="email"></mat-form-field>
              </div>
              <p class="muted small">Uma senha temporária será exibida uma única vez. No primeiro acesso, o diretor configura o Google Authenticator e troca a senha.</p>
            }
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
export class FacultyFormComponent implements OnInit {
  readonly data = inject<FacultyView | null>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<FacultyFormComponent>);
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  readonly plans = signal<PlanView[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly withAdmin = signal(true);

  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9]+(-[a-z0-9]+)*$/)]],
    legalName: ['', Validators.required], tradeName: [''], cnpj: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]], phone: [''], planId: [null as string | null],
    addressStreet: [''], addressNumber: [''], addressComplement: [''], addressDistrict: [''], addressCity: [''],
    addressState: ['', Validators.pattern(/^[A-Za-z]{2}$/)], addressZip: [''], logoUrl: ['', Validators.pattern(/^(https:\/\/.+)?$/)],
    initialAdmin: this.fb.group({ username: ['', Validators.required], fullName: ['', Validators.required], email: ['', [Validators.required, Validators.email]] }),
  });

  ngOnInit(): void {
    this.http.get<PlanView[]>('/api/platform/plans').subscribe(p => this.plans.set(p.filter(x => x.active)));
    if (this.data) {
      this.form.patchValue({ ...this.data } as never);
      this.form.controls.initialAdmin.disable();
    }
  }

  toggleAdmin(on: boolean): void {
    this.withAdmin.set(on);
    if (on) this.form.controls.initialAdmin.enable(); else this.form.controls.initialAdmin.disable();
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); this.error.set('Verifique os campos obrigatórios.'); return; }
    const v = this.form.getRawValue();
    const body: Record<string, unknown> = { ...v, addressState: v.addressState?.toUpperCase() || null };
    for (const k of Object.keys(body)) if (body[k] === '') body[k] = null;
    if (this.data || !this.withAdmin()) delete body['initialAdmin'];
    this.loading.set(true);
    const req = this.data ? this.http.put(`/api/platform/faculties/${this.data.id}`, body) : this.http.post<CreatedFaculty>('/api/platform/faculties', body);
    req.subscribe({ next: r => this.ref.close(r), error: e => { this.loading.set(false); this.error.set(apiMessage(e)); } });
  }
}
