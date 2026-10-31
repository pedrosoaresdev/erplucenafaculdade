import { createHmac } from 'node:crypto';

/** TOTP RFC 6238 (SHA-1, 6 dígitos, 30 s) — simula o Google Authenticator no teste E2E. */
export function totp(base32, at = Date.now()) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const c of base32.replace(/[\s=]/g, '').toUpperCase()) bits += alphabet.indexOf(c).toString(2).padStart(5, '0');
  const key = Buffer.from(bits.match(/.{8}/g).map(b => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(at / 1000 / 30)));
  const h = createHmac('sha1', key).update(counter).digest();
  const o = h[h.length - 1] & 0xf;
  return String((h.readUInt32BE(o) & 0x7fffffff) % 1_000_000).padStart(6, '0');
}

/** Aguarda a próxima janela de 30 s (o servidor rejeita reutilização de código). */
export async function freshCode(base32) {
  const wait = 30_000 - (Date.now() % 30_000) + 300;
  await new Promise(r => setTimeout(r, wait));
  return totp(base32);
}

/** CPF válido determinístico para os testes. */
export function cpf(n) {
  const base = String(529982240 + n * 7919).padStart(9, '0').slice(0, 9);
  const dv = (s, w) => { let t = 0; for (let i = 0; i < s.length; i++) t += Number(s[i]) * (w - i); return ((t * 10) % 11) % 10; };
  const d1 = dv(base, 10);
  return base + d1 + dv(base + d1, 11);
}
