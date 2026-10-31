import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { AccessView, Page, SessionView, UserView } from '../../core/models';
import { facultyParam } from '../../shared/faculty-picker.component';
import { eventLabel } from '../../shared/labels';

@Component({
  selector: 'app-user-activity',
  imports: [DatePipe, MatDialogModule, MatButtonModule, MatTabsModule],
  template: `
    <h2 mat-dialog-title>{{ data.user.fullName }}</h2>
    <mat-dialog-content>
      <mat-tab-group mat-stretch-tabs="false">
        <mat-tab label="Sessões e dispositivos">
          <ul class="session-list">
            @for (s of sessions(); track s.id) {
              <li><div><strong>{{ s.deviceLabel }}</strong><span class="muted small">IP {{ s.ipAddress }} · última atividade {{ s.lastSeenAt | date:'short' }}</span></div></li>
            } @empty { <li class="muted">Nenhuma sessão ativa.</li> }
          </ul>
          <button mat-stroked-button (click)="revokeAll()" [disabled]="!sessions().length">Revogar todas as sessões</button>
        </mat-tab>
        <mat-tab label="Histórico de acesso">
          <table class="data-table responsive"><thead><tr><th scope="col">Data</th><th scope="col">Evento</th><th scope="col">Resultado</th><th scope="col">IP</th></tr></thead>
            <tbody>@for (a of access(); track $index) {
              <tr><td data-label="Data">{{ a.createdAt | date:'short' }}</td><td data-label="Evento">{{ label(a.event) }}</td>
                <td data-label="Resultado"><span class="badge" [class]="a.success ? 'ok' : 'error'">{{ a.success ? 'Sucesso' : 'Falha' }}</span> {{ a.detail ?? '' }}</td>
                <td data-label="IP">{{ a.ipAddress }}</td></tr>
            } @empty { <tr><td colspan="4" class="muted">Sem registros.</td></tr> }</tbody></table>
        </mat-tab>
      </mat-tab-group>
    </mat-dialog-content>
    <mat-dialog-actions align="end"><button mat-flat-button mat-dialog-close>Fechar</button></mat-dialog-actions>
  `,
})
export class UserActivityComponent implements OnInit {
  readonly data = inject<{ user: UserView; facultyId: string | null }>(MAT_DIALOG_DATA);
  private http = inject(HttpClient);
  readonly sessions = signal<SessionView[]>([]);
  readonly access = signal<AccessView[]>([]);
  readonly label = eventLabel;

  ngOnInit(): void { this.load(); }

  load(): void {
    const params = facultyParam(this.data.facultyId);
    const base = `/api/admin/users/${this.data.user.id}`;
    this.http.get<SessionView[]>(`${base}/sessions`, { params }).subscribe(s => this.sessions.set(s));
    this.http.get<Page<AccessView>>(`${base}/access-history`, { params: { ...params, size: 30 } }).subscribe(p => this.access.set(p.content));
  }

  revokeAll(): void {
    this.http.post(`/api/admin/users/${this.data.user.id}/sessions/revoke`, {}, { params: facultyParam(this.data.facultyId) }).subscribe(() => this.load());
  }
}
