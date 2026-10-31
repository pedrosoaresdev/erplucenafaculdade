import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { SelectedFacultyService } from '../../core/selected-faculty.service';

/** Acesso à API acadêmica; anexa ?facultyId= somente quando o Master escolheu uma faculdade. */
@Injectable({ providedIn: 'root' })
export class AcademicApi {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  readonly selected = inject(SelectedFacultyService);

  /** Master precisa escolher a faculdade antes de operar. */
  needsFaculty(): boolean {
    return !!this.auth.profile()?.platformAdmin && !this.selected.id();
  }

  private params(extra: Record<string, unknown> = {}): Record<string, string> {
    const p: Record<string, string> = {};
    const fid = this.selected.id();
    if (this.auth.profile()?.platformAdmin && fid) p['facultyId'] = fid;
    for (const [k, v] of Object.entries(extra)) if (v !== null && v !== undefined && v !== '') p[k] = String(v);
    return p;
  }

  get<T>(path: string, query: Record<string, unknown> = {}): Observable<T> {
    return this.http.get<T>(`/api/academic${path}`, { params: this.params(query) });
  }

  post<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.post<T>(`/api/academic${path}`, body, { params: this.params() });
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(`/api/academic${path}`, body, { params: this.params() });
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(`/api/academic${path}`, { params: this.params() });
  }
}
