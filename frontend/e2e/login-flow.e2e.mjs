/**
 * Teste ponta a ponta (navegador real): login Master com configuração obrigatória do Google Authenticator,
 * criação de faculdade, primeiro acesso do Diretor (TOTP + troca obrigatória de senha) e responsividade.
 * Pré-requisitos: backend em :8080 (perfil dev, banco limpo) e `ng serve` em :4200.
 * Uso: node e2e/login-flow.e2e.mjs [pasta-de-screenshots]
 */
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { freshCode, totp } from './totp.mjs';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4200';
const OUT = process.argv[2] ?? 'e2e-screens';
const MASTER_PASSWORD = process.env.E2E_MASTER_PASSWORD ?? 'Lucena@Plataforma#2026';
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch();
const errors = [];
const step = (m) => console.log('✔', m);

async function shot(page, name) { await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true }); }

async function setupAuthenticator(page, name) {
  await page.getByText('Segurança da conta — configure seu autenticador').waitFor();
  const secret = (await page.locator('.secret-value').first().innerText()).replace(/\s/g, '');
  if (name) await shot(page, name);
  await page.getByLabel('Código de 6 dígitos exibido no aplicativo').fill(totp(secret));
  await page.getByRole('button', { name: 'Validar e ativar' }).click();
  await page.getByText('códigos de recuperação', { exact: false }).first().waitFor();
  const codes = await page.locator('.codes code').allInnerTexts();
  if (codes.length !== 10) throw new Error('esperava 10 códigos de recuperação');
  await page.getByRole('checkbox', { name: 'Guardei meus códigos em local seguro' }).check();
  await page.getByRole('button', { name: 'Continuar' }).click();
  return secret;
}

try {
  const ctx = await browser.newContext({ viewport: { width: 1366, height: 860 } });
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push(e.message));

  // 1) Login Master — primeiro acesso exige autenticador
  await page.goto(`${BASE}/login`);
  await shot(page, '01-login-master');
  await page.getByLabel('Nome de usuário').fill('master');
  await page.getByLabel('Senha', { exact: true }).fill('senha-errada!');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.getByText('Usuário/matrícula ou senha inválidos').waitFor();
  step('senha incorreta rejeitada com mensagem genérica');
  await page.getByLabel('Senha', { exact: true }).fill(MASTER_PASSWORD);
  await page.getByRole('button', { name: 'Entrar' }).click();
  const masterSecret = await setupAuthenticator(page, '02-setup-autenticador');
  await page.getByRole('heading', { name: /Olá/ }).waitFor();
  step('Master configurou o Google Authenticator e entrou');

  // 2) Painel da plataforma e criação de faculdade
  await page.goto(`${BASE}/plataforma`);
  await page.getByText('Faculdades ativas').waitFor();
  await shot(page, '03-dashboard-plataforma');
  await page.goto(`${BASE}/plataforma/faculdades`);
  await page.getByRole('button', { name: 'Nova faculdade' }).click();
  const dlg = page.getByRole('dialog');
  await dlg.getByLabel('Nome da faculdade').fill('Faculdade Lucena Centro');
  await dlg.getByLabel('Identificador (endereço de login)').fill('lucena-centro');
  await dlg.getByLabel('Razão social').fill('Lucena Educação Superior LTDA');
  await dlg.getByLabel('CNPJ', { exact: true }).fill('11.222.333/0001-81');
  await dlg.getByLabel('E-mail institucional').fill('contato@lucena.edu.br');
  await dlg.getByLabel('Usuário', { exact: true }).fill('diretor');
  await dlg.getByLabel('Nome completo').fill('Ana Diretora');
  await dlg.locator('fieldset').getByLabel('E-mail').fill('ana@lucena.edu.br');
  await shot(page, '04-nova-faculdade');
  await page.getByRole('button', { name: 'Salvar' }).click();
  await page.getByText('Acesso gerado').waitFor();
  const tempPassword = (await page.locator('.secret-value').innerText()).trim();
  await page.getByRole('button', { name: 'Concluir' }).click();
  await page.getByText('Faculdade Lucena Centro').first().waitFor();
  await shot(page, '05-faculdades');
  step('faculdade criada com Diretor Geral e senha temporária exibida uma vez');

  // 3) Sair e entrar como Diretor na faculdade (login escopado + troca obrigatória)
  await page.getByRole('button', { name: 'Menu do usuário' }).click();
  await page.getByRole('menuitem', { name: 'Sair' }).click();
  await page.goto(`${BASE}/f/lucena-centro/login`);
  await page.getByRole('heading', { name: 'Lucena Centro' }).or(page.getByRole('heading', { name: 'Faculdade Lucena Centro' })).waitFor();
  await page.getByLabel('Matrícula ou nome de usuário').fill('diretor');
  await page.getByLabel('Senha', { exact: true }).fill(tempPassword);
  await page.getByRole('button', { name: 'Entrar' }).click();
  const dirSecret = await setupAuthenticator(page);
  await page.getByText('Você está usando uma senha temporária').waitFor();
  await shot(page, '06-troca-obrigatoria');
  step('Diretor configurou autenticador; troca de senha obrigatória exibida');

  // Política de senha: senha contendo o usuário é recusada pelo backend e a etapa 1 é exigida novamente
  await page.getByLabel('Senha atual').fill(tempPassword);
  await page.getByLabel('Código de 6 dígitos do Google Authenticator').fill(await freshCode(dirSecret));
  await page.getByRole('button', { name: 'Continuar' }).click();
  await page.getByLabel('Nova senha', { exact: true }).fill('Diretoria#Lucena2026');
  await page.getByLabel('Repetir nova senha').fill('Diretoria#Lucena2026');
  await page.getByRole('button', { name: 'Confirmar alteração de senha' }).click();
  await page.getByText('A senha não pode conter o nome de usuário').waitFor();
  step('senha contendo o nome de usuário recusada');

  await page.getByLabel('Senha atual').fill(tempPassword);
  await page.getByLabel('Código de 6 dígitos do Google Authenticator').fill(await freshCode(dirSecret));
  await page.getByRole('button', { name: 'Continuar' }).click();
  await page.getByLabel('Nova senha', { exact: true }).fill('Gestao#Lucena2026');
  await page.getByLabel('Repetir nova senha').fill('Gestao#Lucena2026');
  await page.getByText('Força da senha').waitFor();
  await shot(page, '07-nova-senha');
  await page.getByRole('button', { name: 'Confirmar alteração de senha' }).click();
  await page.getByText('Senha alterada com sucesso').waitFor();
  step('senha alterada com senha atual + TOTP; sessões encerradas e novo login exigido');

  // 4) Segundo acesso: somente código (sem novo QR Code)
  await page.getByLabel('Matrícula ou nome de usuário').fill('diretor');
  await page.getByLabel('Senha', { exact: true }).fill('Gestao#Lucena2026');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.getByText('Verificação em duas etapas').waitFor();
  if (await page.locator('img[alt^="QR Code"]').count()) throw new Error('QR Code não deveria reaparecer');
  await shot(page, '08-verificacao-totp');
  await page.getByLabel('Código do autenticador').fill(await freshCode(dirSecret));
  await page.getByRole('button', { name: 'Verificar e entrar' }).click();
  await page.getByRole('heading', { name: /Olá, Ana/ }).waitFor();
  await shot(page, '09-inicio-diretor');
  step('segundo acesso pediu apenas o código do autenticador');

  await page.goto(`${BASE}/admin/usuarios`);
  await page.getByText('ana@lucena.edu.br').waitFor();
  await shot(page, '10-usuarios');
  await page.goto(`${BASE}/admin/cargos`);
  await page.getByRole('button', { name: /Coordenador/ }).click();
  await shot(page, '11-cargos');
  await page.goto(`${BASE}/perfil?aba=autenticador`);
  await page.getByText('Trocar de dispositivo').waitFor();
  await shot(page, '12-autenticador');
  // Usuário da faculdade não acessa o painel Master
  await page.goto(`${BASE}/plataforma`);
  await page.getByText('Você não tem permissão').waitFor();
  step('rotas administrativas e bloqueio do painel Master verificados');

  // 5) Responsividade (celular): menu vira gaveta, tabela vira cartões
  const mobile = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, storageState: await ctx.storageState() });
  const m = await mobile.newPage();
  await m.goto(`${BASE}/f/lucena-centro/login`);
  await shot(m, '13-mobile-login');
  await m.getByLabel('Matrícula ou nome de usuário').fill('diretor');
  await m.getByLabel('Senha', { exact: true }).fill('Gestao#Lucena2026');
  await m.getByRole('button', { name: 'Entrar' }).click();
  await m.getByLabel('Código do autenticador').fill(await freshCode(dirSecret));
  await m.getByRole('button', { name: 'Verificar e entrar' }).click();
  await m.getByRole('heading', { name: /Olá, Ana/ }).waitFor();
  await m.goto(`${BASE}/admin/usuarios`);
  await m.getByText('ana@lucena.edu.br').waitFor();
  const width = await m.evaluate(() => document.documentElement.scrollWidth);
  if (width > 391) throw new Error(`rolagem horizontal no celular: ${width}px`);
  await shot(m, '14-mobile-usuarios');
  await m.getByRole('button', { name: 'Abrir menu' }).click();
  await m.getByRole('link', { name: 'Cargos e permissões' }).waitFor();
  await shot(m, '15-mobile-menu');
  await m.keyboard.press('Escape');
  await m.getByRole('button', { name: 'Opções de acessibilidade' }).click();
  await m.getByRole('button', { name: 'Alto contraste' }).click();
  await shot(m, '16-mobile-acessibilidade');
  step('layout móvel sem rolagem horizontal, menu em gaveta e painel de acessibilidade');

  void masterSecret;
  if (errors.length) throw new Error('erros de JavaScript na página: ' + errors.join(' | '));
  console.log('\nE2E OK');
} catch (e) {
  console.error('E2E FALHOU:', e);
  process.exitCode = 1;
} finally {
  await browser.close();
}
