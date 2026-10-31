/**
 * E2E da instalação inicial: e-mail do dono + código do console → Google Authenticator → usuário e senha.
 * Uso: e2e/setup-run.sh (sobe o backend sem Master e informa o código via SETUP_CODE).
 */
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { freshCode, markUsed, totp } from './totp.mjs';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4200';
const OUT = process.argv[2] ?? 'e2e-screens';
const OWNER = process.env.OWNER_EMAIL;
const CODE = process.env.SETUP_CODE;
mkdirSync(OUT, { recursive: true });
const step = (m) => console.log('✔', m);
const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 860 } });
  await page.goto(`${BASE}/login`);
  await page.waitForURL(/\/instalacao$/);
  await page.getByRole('heading', { name: 'Instalação inicial' }).waitFor();
  step('sem administrador Master: login redireciona para a instalação');

  await page.getByLabel('Seu e-mail').fill('intruso@exemplo.com');
  await page.getByLabel('Código de instalação').fill(CODE);
  await page.getByRole('button', { name: 'Continuar' }).click();
  await page.getByText('E-mail ou código de instalação inválidos').waitFor();
  step('e-mail diferente do dono é recusado');

  await page.getByLabel('Seu e-mail').fill(OWNER.toUpperCase());
  await page.getByLabel('Código de instalação').fill(CODE.toLowerCase());
  await page.screenshot({ path: `${OUT}/30-instalacao-codigo.png`, fullPage: true });
  await page.getByRole('button', { name: 'Continuar' }).click();
  await page.getByText('Configure o Google Authenticator').waitFor();
  const secret = (await page.locator('.secret-value').innerText()).replace(/\s/g, '');
  await page.screenshot({ path: `${OUT}/31-instalacao-autenticador.png`, fullPage: true });
  await page.getByLabel('Código de 6 dígitos exibido no aplicativo').fill(totp(secret));
  markUsed(secret);
  await page.getByRole('button', { name: 'Validar e continuar' }).click();
  await page.getByText('Crie seu usuário e senha').waitFor();
  await page.getByLabel('Seu nome completo').fill('Pedro Lucena');
  await page.getByLabel('Nome de usuário').fill('pedro');
  await page.getByLabel('Senha', { exact: true }).fill('Plataforma#Lucena2026');
  await page.getByLabel('Repetir senha').fill('Plataforma#Lucena2026');
  await page.screenshot({ path: `${OUT}/32-instalacao-conta.png`, fullPage: true });
  await page.getByRole('button', { name: 'Concluir instalação' }).click();
  await page.getByText('Instalação concluída').waitFor();
  await page.getByRole('checkbox', { name: 'Guardei meus códigos em local seguro' }).check();
  await page.getByRole('button', { name: 'Continuar' }).click();
  await page.getByRole('heading', { name: 'Painel da plataforma' }).waitFor();
  step('dono validou o autenticador, criou usuário e senha e entrou no painel Master');

  // Instalação não pode ser repetida; login normal com usuário + senha + código
  await page.getByRole('button', { name: 'Menu do usuário' }).click();
  await page.getByRole('menuitem', { name: 'Sair' }).click();
  await page.goto(`${BASE}/instalacao`);
  await page.getByText('A plataforma já está instalada').waitFor();
  await page.goto(`${BASE}/login`);
  await page.getByLabel('Nome de usuário').fill('pedro');
  await page.getByLabel('Senha', { exact: true }).fill('Plataforma#Lucena2026');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.getByLabel('Código do autenticador').fill(await freshCode(secret));
  await page.getByRole('button', { name: 'Verificar e entrar' }).click();
  await page.getByRole('heading', { name: /Olá, Pedro/ }).waitFor();
  step('instalação bloqueada após concluir; próximo acesso com usuário, senha e Google Authenticator');
  console.log('\nE2E INSTALAÇÃO OK');
} catch (e) {
  console.error('E2E INSTALAÇÃO FALHOU:', e);
  process.exitCode = 1;
} finally {
  await browser.close();
}
