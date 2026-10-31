import { Component, computed, effect, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../core/auth.service';
import { PermissionView, RoleView } from '../../core/models';
import { apiMessage } from '../../core/api-error';
import { SelectedFacultyService } from '../../core/selected-faculty.service';
import { FacultyPickerComponent, facultyParam } from '../../shared/faculty-picker.component';

interface Draft { id?: string; code: string; name: string; description: string; systemRole: boolean; permissions: Set<string>; }

@Component({
  selector: 'app-roles',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatExpansionModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSnackBarModule, FacultyPickerComponent],
  template: `
    <header class="page-header"><div><h1>Cargos e permissões</h1><p class="muted">Controle de acesso por cargo (RBAC). As permissões são verificadas no servidor.</p></div>
      @if (canManage) { <button mat-flat-button (click)="newRole()" [disabled]="needsFaculty()"><mat-icon>add</mat-icon> Novo cargo</button> }</header>
    <section class="filters"><app-faculty-picker [(facultyId)]="facultyId" /></section>
    @if (error()) { <p class="alert error" role="alert">{{ error() }}</p> }
    <div class="roles-layout">
      <ul class="role-list" aria-label="Cargos">
        @for (r of roles(); track r.id) {
          <li><button type="button" class="role-item" [class.active]="draft()?.id === r.id" (click)="select(r)">
            <strong>{{ r.name }}</strong><span class="muted small">{{ r.permissions.length }} permissões · {{ r.users }} usuário(s)
              @if (r.systemRole) { · padrão }</span></button></li>
        } @empty { <li class="muted">{{ needsFaculty() ? 'Selecione uma faculdade.' : 'Nenhum cargo.' }}</li> }
      </ul>
      @if (draft(); as d) {
        <mat-card class="role-editor">
          <mat-card-content>
            <div class="form-grid">
              <mat-form-field><mat-label>Nome</mat-label><input matInput [(ngModel)]="d.name" [disabled]="!canManage" required></mat-form-field>
              <mat-form-field><mat-label>Código</mat-label><input matInput [(ngModel)]="d.code" [disabled]="!canManage || d.systemRole" required></mat-form-field>
              <mat-form-field class="span-2"><mat-label>Descrição</mat-label><input matInput [(ngModel)]="d.description" [disabled]="!canManage"></mat-form-field>
            </div>
            <mat-accordion multi>
              @for (g of groups(); track g.module) {
                <mat-expansion-panel>
                  <mat-expansion-panel-header><mat-panel-title>{{ g.module }}</mat-panel-title>
                    <mat-panel-description>{{ countIn(g.items) }}/{{ g.items.length }}</mat-panel-description></mat-expansion-panel-header>
                  @for (p of g.items; track p.code) {
                    <mat-checkbox class="perm" [checked]="d.permissions.has(p.code)" [disabled]="!canManage" (change)="toggle(p.code, $event.checked)">
                      {{ p.description }} <span class="muted small">({{ p.code }})</span></mat-checkbox>
                  }
                </mat-expansion-panel>
              }
            </mat-accordion>
            @if (canManage) {
              <div class="row-actions mt">
                <button mat-flat-button (click)="save()">Salvar cargo</button>
                @if (d.id && !d.systemRole) { <button mat-stroked-button class="danger-text" (click)="remove()">Excluir</button> }
                <button mat-button (click)="draft.set(null)">Fechar</button>
              </div>
              <p class="muted small">Você só pode conceder permissões que você mesmo possui (proteção contra escalonamento de privilégios).</p>
            }
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
})
export class RolesComponent {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);
  readonly auth = inject(AuthService);
  readonly roles = signal<RoleView[]>([]);
  readonly perms = signal<PermissionView[]>([]);
  readonly draft = signal<Draft | null>(null);
  readonly error = signal<string | null>(null);
  readonly facultyId = inject(SelectedFacultyService).id;
  readonly canManage = this.auth.hasPermission('ROLE_MANAGE');
  readonly groups = computed(() => {
    const m = new Map<string, PermissionView[]>();
    for (const p of this.perms()) m.set(p.module, [...(m.get(p.module) ?? []), p]);
    return [...m.entries()].map(([module, items]) => ({ module, items }));
  });

  constructor() {
    if (this.canManage) this.http.get<PermissionView[]>('/api/admin/permissions').subscribe(p => this.perms.set(p));
    effect(() => { this.facultyId(); this.load(); });
  }

  needsFaculty(): boolean { return !!this.auth.profile()?.platformAdmin && !this.facultyId(); }

  load(): void {
    if (this.needsFaculty()) { this.roles.set([]); return; }
    this.http.get<RoleView[]>('/api/admin/roles', { params: facultyParam(this.facultyId()) }).subscribe({ next: r => this.roles.set(r), error: e => this.error.set(apiMessage(e)) });
  }

  select(r: RoleView): void {
    this.draft.set({ id: r.id, code: r.code, name: r.name, description: r.description ?? '', systemRole: r.systemRole, permissions: new Set(r.permissions) });
    if (!this.perms().length) this.perms.set(r.permissions.map(code => ({ code, module: 'Permissões', description: code })));
  }

  newRole(): void { this.draft.set({ code: '', name: '', description: '', systemRole: false, permissions: new Set() }); }
  countIn(items: PermissionView[]): number { return items.filter(p => this.draft()?.permissions.has(p.code)).length; }

  toggle(code: string, on: boolean): void {
    const d = this.draft();
    if (!d) return;
    if (on) d.permissions.add(code); else d.permissions.delete(code);
    this.draft.set({ ...d, permissions: new Set(d.permissions) });
  }

  save(): void {
    const d = this.draft();
    if (!d) return;
    const body = { code: d.code.trim().toUpperCase(), name: d.name, description: d.description, permissions: [...d.permissions] };
    const params = facultyParam(this.facultyId());
    const req = d.id ? this.http.put<RoleView>(`/api/admin/roles/${d.id}`, body, { params }) : this.http.post<RoleView>('/api/admin/roles', body, { params });
    req.subscribe({ next: r => { this.snack.open('Cargo salvo.', 'OK', { duration: 3000 }); this.load(); this.select(r); this.error.set(null); }, error: e => this.error.set(apiMessage(e)) });
  }

  remove(): void {
    const d = this.draft();
    if (!d?.id) return;
    this.http.delete(`/api/admin/roles/${d.id}`, { params: facultyParam(this.facultyId()) })
      .subscribe({ next: () => { this.draft.set(null); this.load(); }, error: e => this.error.set(apiMessage(e)) });
  }
}
