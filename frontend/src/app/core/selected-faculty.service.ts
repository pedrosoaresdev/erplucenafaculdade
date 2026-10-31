import { Injectable, signal } from '@angular/core';

/** Faculdade escolhida pelo administrador Master; compartilhada entre as telas acadêmicas. */
@Injectable({ providedIn: 'root' })
export class SelectedFacultyService {
  readonly id = signal<string | null>(null);
}
