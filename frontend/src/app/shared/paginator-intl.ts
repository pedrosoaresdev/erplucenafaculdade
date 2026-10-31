import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

@Injectable()
export class PtBrPaginatorIntl extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Itens por página';
  override nextPageLabel = 'Próxima página';
  override previousPageLabel = 'Página anterior';
  override firstPageLabel = 'Primeira página';
  override lastPageLabel = 'Última página';
  override getRangeLabel = (page: number, size: number, length: number) => {
    if (length === 0) return '0 de 0';
    const start = page * size;
    return `${start + 1} – ${Math.min(start + size, length)} de ${length}`;
  };
}
