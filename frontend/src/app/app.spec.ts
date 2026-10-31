import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] }).compileComponents();
  });

  it('renderiza o botão de acessibilidade', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const btn = (fixture.nativeElement as HTMLElement).querySelector('button[aria-label="Opções de acessibilidade"]');
    expect(btn).toBeTruthy();
  });
});
