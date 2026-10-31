import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { apiMessage } from '../../core/api-error';
import { WEEKDAYS } from '../../core/academic.models';
import { AssessmentView, DiaryView, KINDS, SITUATIONS, SessionView, StudentRow, saveBlob, situationBadge } from '../../core/portal.models';

@Component({
  selector: 'app-diary',
  imports: [DatePipe, DecimalPipe, FormsModule, ReactiveFormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSelectModule, MatSnackBarModule, MatTabsModule, MatTooltipModule],
  templateUrl: './diary.component.html',
})
export class DiaryComponent implements OnInit {
  readonly id = input.required<string>();
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  readonly canGrade = this.auth.profile()?.permissions.includes('GRADE_MANAGE') ?? false;
  readonly canAttend = this.auth.profile()?.permissions.includes('ATTENDANCE_MANAGE') ?? false;
  readonly kinds = Object.entries(KINDS);
  readonly days = WEEKDAYS;
  readonly situations = SITUATIONS;
  readonly badge = situationBadge;

  readonly d = signal<DiaryView | null>(null);
  readonly sessions = signal<SessionView[]>([]);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);

  // Notas
  gradeAssessmentId: string | null = null;
  gradeInputs: Record<string, number | null> = {};
  readonly selectedAssessment = computed(() => this.d()?.assessments.find(a => a.id === this.gradeAssessmentIdSig()));
  private gradeAssessmentIdSig = signal<string | null>(null);

  // Avaliações
  readonly editingAssessment = signal<AssessmentView | null>(null);
  readonly assessmentForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(120)]], kind: ['PROVA', Validators.required],
    weight: [1, [Validators.required, Validators.min(0.01)]], maxScore: [10, [Validators.required, Validators.min(0.01)]], dueDate: [''],
  });

  // Frequência
  readonly editingSession = signal<SessionView | null>(null);
  readonly sessionForm = this.fb.nonNullable.group({
    date: [this.today(), Validators.required], lessons: [2, [Validators.required, Validators.min(1), Validators.max(10)]], content: [''],
  });
  absences: Record<string, number> = {};

  // Plano
  plan = '';

  ngOnInit(): void {
    this.load();
    this.loadSessions();
  }

  private base(): string { return `/api/teacher/class-subjects/${this.id()}`; }

  load(): void {
    this.http.get<DiaryView>(this.base()).subscribe({ next: d => this.apply(d), error: e => this.error.set(apiMessage(e)) });
  }

  private apply(d: DiaryView): void {
    this.d.set(d);
    this.plan = d.teachingPlan ?? '';
    this.error.set(null);
    if (this.gradeAssessmentId) this.selectAssessment(this.gradeAssessmentId);
  }

  loadSessions(): void {
    this.http.get<SessionView[]>(`${this.base()}/attendance`).subscribe({ next: s => this.sessions.set(s), error: e => this.error.set(apiMessage(e)) });
  }

  // ---------------- Notas ----------------
  selectAssessment(id: string | null): void {
    this.gradeAssessmentId = id;
    this.gradeAssessmentIdSig.set(id);
    this.gradeInputs = {};
    for (const s of this.d()?.students ?? []) this.gradeInputs[s.studentId] = id && s.scores[id] != null ? s.scores[id] : null;
  }

  score(s: StudentRow, a: AssessmentView): number | undefined { return s.scores[a.id]; }
  kindLabel(a: AssessmentView): string { return KINDS[a.kind]; }

  invalidGrades(): boolean {
    const a = this.selectedAssessment();
    if (!a) return true;
    return Object.values(this.gradeInputs).some(v => v != null && (v < 0 || v > a.maxScore));
  }

  saveGrades(): void {
    const a = this.selectedAssessment();
    if (!a) return;
    const grades = Object.entries(this.gradeInputs).map(([studentId, score]) => ({ studentId, score: score === null || (score as unknown) === '' ? null : score }));
    this.run(this.http.put<DiaryView>(`${this.base()}/assessments/${a.id}/grades`, { grades }), d => { this.apply(d); this.snack.open('Notas salvas.', 'OK', { duration: 2500 }); });
  }

  // ---------------- Avaliações ----------------
  editAssessment(a: AssessmentView): void {
    this.editingAssessment.set(a);
    this.assessmentForm.setValue({ title: a.title, kind: a.kind, weight: a.weight, maxScore: a.maxScore, dueDate: a.dueDate ?? '' });
  }

  resetAssessment(): void {
    this.editingAssessment.set(null);
    this.assessmentForm.reset();
  }

  saveAssessment(): void {
    const v = this.assessmentForm.getRawValue();
    const body = { ...v, dueDate: v.dueDate || null };
    const e = this.editingAssessment();
    const req = e ? this.http.put<DiaryView>(`${this.base()}/assessments/${e.id}`, body) : this.http.post<DiaryView>(`${this.base()}/assessments`, body);
    this.run(req, d => { this.apply(d); this.resetAssessment(); this.snack.open('Avaliação salva.', 'OK', { duration: 2500 }); });
  }

  deleteAssessment(a: AssessmentView): void {
    this.run(this.http.delete<DiaryView>(`${this.base()}/assessments/${a.id}`), d => { if (this.gradeAssessmentId === a.id) this.selectAssessment(null); this.apply(d); });
  }

  // ---------------- Frequência ----------------
  newSession(): void {
    this.editingSession.set(null);
    this.sessionForm.reset({ date: this.today(), lessons: 2, content: '' });
    this.absences = {};
  }

  editSession(s: SessionView): void {
    this.editingSession.set(s);
    this.sessionForm.setValue({ date: s.date, lessons: s.lessons, content: s.content ?? '' });
    this.absences = {};
    s.absences.forEach(a => (this.absences[a.studentId] = a.absences));
  }

  markAllAbsent(studentId: string): void {
    this.absences[studentId] = this.absences[studentId] ? 0 : this.sessionForm.getRawValue().lessons;
  }

  invalidAbsences(): boolean {
    const lessons = this.sessionForm.getRawValue().lessons;
    return Object.values(this.absences).some(v => v != null && (v < 0 || v > lessons));
  }

  saveSession(): void {
    const v = this.sessionForm.getRawValue();
    const body = { ...v, content: v.content || null,
      absences: Object.entries(this.absences).filter(([, n]) => n > 0).map(([studentId, absences]) => ({ studentId, absences, justified: false })) };
    const e = this.editingSession();
    const req = e ? this.http.put<SessionView[]>(`${this.base()}/attendance/${e.id}`, body) : this.http.post<SessionView[]>(`${this.base()}/attendance`, body);
    this.run(req, s => { this.sessions.set(s); this.newSession(); this.load(); this.snack.open('Frequência registrada.', 'OK', { duration: 2500 }); });
  }

  deleteSession(s: SessionView): void {
    this.run(this.http.delete<SessionView[]>(`${this.base()}/attendance/${s.id}`), list => { this.sessions.set(list); this.load(); });
  }

  // ---------------- Plano e exportação ----------------
  savePlan(): void {
    this.run(this.http.put<DiaryView>(`${this.base()}/plan`, { teachingPlan: this.plan }), d => { this.apply(d); this.snack.open('Plano de ensino salvo.', 'OK', { duration: 2500 }); });
  }

  export(format: 'xlsx' | 'csv'): void {
    const d = this.d();
    this.http.get(`${this.base()}/export`, { params: { format }, responseType: 'blob' }).subscribe({
      next: b => saveBlob(b, `diario-${d?.teaching.classCode}-${d?.teaching.subjectCode}.${format}`),
      error: e => this.error.set(apiMessage(e, 'Não foi possível exportar.')),
    });
  }

  private today(): string {
    const n = new Date();
    return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`;
  }

  private run<T>(obs: Observable<T>, ok: (v: T) => void): void {
    this.busy.set(true);
    obs.subscribe({ next: v => { this.busy.set(false); this.error.set(null); ok(v); }, error: e => { this.busy.set(false); this.error.set(apiMessage(e)); } });
  }
}
