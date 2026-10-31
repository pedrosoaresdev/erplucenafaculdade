import { Injectable, signal } from '@angular/core';

/** Faculdade em atendimento pelo administrador Master (suporte); compartilhada entre todas as telas. */
@Injectable({ providedIn: 'root' })
export class SelectedFacultyService {
  readonly id = signal<string | null>(null);
  readonly name = signal<string | null>(null);

  select(id: string | null, name: string | null): void {
    this.id.set(id);
    this.name.set(name);
  }
}
