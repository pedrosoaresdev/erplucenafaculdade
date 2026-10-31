import { passwordScore } from './password-strength.component';

describe('passwordScore', () => {
  it('classifica senhas', () => {
    expect(passwordScore('')).toBe(0);
    expect(passwordScore('12345678')).toBeLessThanOrEqual(1);
    expect(passwordScore('aaaaaaaaaaaa')).toBeLessThanOrEqual(1);
    expect(passwordScore('Campus#Norte2026')).toBe(4);
  });
});
