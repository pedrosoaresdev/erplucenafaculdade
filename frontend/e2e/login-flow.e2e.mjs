/**
 * Teste ponta a ponta (navegador real): login Master com configuração obrigatória do Google Authenticator,
 * criação de faculdade, primeiro acesso do Diretor (TOTP + troca obrigatória de senha) e responsividade.
 * Pré-requisitos: backend em :8080 (perfil dev, banco limpo) e `ng serve` em :4200.
 * Uso: node e2e/login-flow.e2e.mjs [pasta-de-screenshots]
 */
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { cpf, freshCode, markUsed, totp } from './totp.mjs';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4200';
const OUT = process.argv[2] ?? 'e2e-screens';
const MASTER_PASSWORD = process.env.E2E_MASTER_PASSWORD ?? 'Lucena@Plataforma#2026';
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch();
const errors = [];
const step = (m) => console.log('✔', m);

/** Diálogo aberto e com a animação concluída (o foco inicial já foi aplicado). */
async function openedDialog(page) {
  await page.locator('mat-dialog-container.mdc-dialog--open').last().waitFor();
  await page.waitForTimeout(400);
  return page.getByRole('dialog').last();
}

async function shot(page, name) { await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true }); }

async function setupAuthenticator(page, name) {
  await page.getByText('Segurança da conta — configure seu autenticador').waitFor();
  const secret = (await page.locator('.secret-value').first().innerText()).replace(/\s/g, '');
  if (name) await shot(page, name);
  await page.getByLabel('Código de 6 dígitos exibido no aplicativo').fill(totp(secret));
  markUsed(secret);
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
  page.on('pageerror', e => { errors.push(e.message); console.error('PAGEERROR:', e.message); });
  page.on('console', m => { if (m.type() === 'error') console.error('CONSOLE:', m.text()); });

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
  const dlg = await openedDialog(page);
  await dlg.getByLabel('Nome da faculdade').fill('Faculdade Lucena Centro');
  await dlg.getByLabel('Identificador (endereço de login)').fill('lucena-centro');
  await dlg.getByLabel('Razão social').fill('Lucena Educação Superior LTDA');
  await dlg.getByLabel('CNPJ', { exact: true }).fill('11.222.333/0001-81');
  await dlg.getByLabel('E-mail institucional').fill('contato@lucena.edu.br');
  await dlg.getByLabel('Usuário', { exact: true }).fill('diretor');
  await dlg.getByLabel('Nome completo').fill('Ana Diretora');
  await dlg.locator('fieldset').getByLabel('E-mail').fill('ana@lucena.edu.br');
  if ((await dlg.getByLabel('Nome da faculdade').inputValue()) !== 'Faculdade Lucena Centro') throw new Error('campo nome alterado indevidamente');
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

  // 5) Acadêmico (Fase 4): disciplinas, curso + matriz, período, professor, turma, horários e aluno
  async function select(scope, label, option) {
    try {
      // Clica no campo (como o usuário): em campos estreitos o rótulo cobre o centro do seletor
      await scope.locator('mat-form-field', { has: page.getByRole('combobox', { name: label, exact: true }) }).click({ timeout: 8000 });
    } catch (e) {
      console.error('DIAG overlay:', await page.locator('.cdk-overlay-container').innerHTML().catch(() => '?'));
      throw e;
    }
    await page.getByRole('option', { name: option }).click();
    await page.getByRole('listbox').waitFor({ state: 'detached', timeout: 5000 });
  }
  await page.goto(`${BASE}/academico/disciplinas`);
  for (const [code, name] of [['LOG1', 'Lógica de Programação'], ['BD1', 'Banco de Dados']]) {
    await page.getByLabel('Código', { exact: true }).fill(code);
    await page.getByLabel('Nome', { exact: true }).fill(name);
    await page.getByLabel('Carga horária (h)').fill('80');
    await page.getByRole('button', { name: 'Salvar' }).click();
    await page.getByRole('cell', { name: `${name} ${code}`, exact: true }).waitFor();
  }
  await page.goto(`${BASE}/academico/cursos`);
  await page.getByRole('button', { name: 'Novo curso' }).click();
  let form = await openedDialog(page);
  await form.getByLabel('Código').fill('ADS');
  await form.getByLabel('Nome do curso').fill('Análise e Desenvolvimento de Sistemas');
  await form.getByLabel('Duração (semestres)').fill('5');
  await form.getByLabel('Mensalidade (R$)').fill('899.90');
  await form.getByRole('button', { name: 'Salvar' }).click();
  await page.getByRole('link', { name: 'Análise e Desenvolvimento de Sistemas' }).click();
  await page.getByRole('heading', { name: /Matriz curricular/ }).waitFor();
  for (const name of ['LOG1 — Lógica de Programação', 'BD1 — Banco de Dados']) {
    await select(page, 'Disciplina', name);
    await page.getByRole('button', { name: 'Adicionar' }).click();
    await page.getByText('Matriz atualizada').waitFor();
    await page.getByText('Matriz atualizada').waitFor({ state: 'hidden' });
  }
  await page.getByText('2 disciplinas · 160 h').waitFor();
  await shot(page, '17-matriz-curricular');
  step('disciplinas, curso e matriz curricular cadastrados');

  await page.goto(`${BASE}/academico/periodos`);
  await page.getByLabel('Código (AAAA.S)').fill('2026.2');
  await page.getByLabel('Início').fill('2026-08-03');
  await page.getByLabel('Término').fill('2026-12-18');
  await page.getByRole('button', { name: 'Salvar' }).click();
  await page.getByRole('button', { name: 'Ações para 2026.2' }).click();
  await page.getByRole('menuitem', { name: 'Abrir período' }).click();
  await page.getByText('Aberto', { exact: true }).waitFor();

  await page.goto(`${BASE}/academico/professores`);
  await page.getByRole('button', { name: 'Novo professor' }).click();
  form = await openedDialog(page);
  await form.getByLabel('Nome completo').fill('Carlos Mendes');
  await form.getByLabel('CPF').fill(cpf(1));
  await form.getByLabel('E-mail').fill('carlos@lucena.edu.br');
  await form.getByLabel('Nome de usuário').fill('prof.carlos');
  await form.getByRole('button', { name: 'Salvar' }).click();
  await page.getByText('Acesso gerado').waitFor();
  const teacherTemp = (await page.locator('.secret-value').innerText()).trim();
  await page.getByRole('button', { name: 'Concluir' }).click();
  await page.locator('#conteudo').getByText('prof.carlos').waitFor();
  step('professor cadastrado com acesso (cargo Professor)');

  await page.goto(`${BASE}/academico/turmas`);
  await page.getByRole('button', { name: 'Nova turma' }).click();
  form = await openedDialog(page);
  await select(form, 'Curso', 'Análise e Desenvolvimento de Sistemas');
  await select(form, 'Período letivo', '2026.2');
  await form.getByLabel('Código da turma').fill('ADS1N');
  await form.getByLabel('Sala').fill('201');
  await form.getByRole('button', { name: 'Salvar' }).click();
  await form.waitFor({ state: 'detached' });
  await page.getByRole('link', { name: 'ADS1N' }).click();
  await page.waitForURL(/\/academico\/turmas\/[0-9a-f-]{36}$/);
  await page.getByRole('heading', { name: 'Turma ADS1N' }).waitFor();
  await page.getByRole('button', { name: 'Ofertar disciplina' }).click();
  form = await openedDialog(page);
  await select(form, 'Disciplina (da matriz do curso)', 'Lógica de Programação');
  await select(form, 'Professor', 'Carlos Mendes');
  await select(form, 'Dia da semana', 'Segunda');
  await form.getByLabel('Início').fill('19:00');
  await form.getByLabel('Término').fill('20:40');
  await form.getByRole('button', { name: 'Salvar' }).click();
  await page.getByRole('cell', { name: '19:00 – 20:40', exact: true }).waitFor();
  // Choque de horário dentro da própria turma é recusado pelo servidor
  await page.getByRole('button', { name: 'Ofertar disciplina' }).click();
  form = await openedDialog(page);
  await select(form, 'Disciplina (da matriz do curso)', 'Banco de Dados');
  await select(form, 'Dia da semana', 'Segunda');
  await form.getByLabel('Início').fill('20:00');
  await form.getByLabel('Término').fill('21:30');
  await form.getByRole('button', { name: 'Salvar' }).click();
  await form.getByText('A turma já tem aula de Lógica de Programação').waitFor();
  await form.getByLabel('Início').fill('20:40');
  await form.getByLabel('Término').fill('22:20');
  await form.getByRole('button', { name: 'Salvar' }).click();
  await page.getByRole('cell', { name: '20:40 – 22:20', exact: true }).waitFor();
  step('turma criada; oferta com professor/horário e choque de horário bloqueado');

  await page.goto(`${BASE}/academico/alunos`);
  await page.getByRole('button', { name: 'Novo aluno' }).click();
  form = await openedDialog(page);
  await form.getByLabel('Nome completo').fill('Beatriz Souza');
  await form.getByLabel('CPF').fill('123.456.789-00');
  await form.getByLabel('E-mail').fill('beatriz@aluno.lucena.edu.br');
  await form.getByRole('button', { name: 'Salvar' }).click();
  await form.getByText('Verifique os campos destacados').waitFor();
  await form.getByLabel('CPF').fill(cpf(2));
  await form.getByRole('button', { name: 'Salvar' }).click();
  await page.getByText('Acesso gerado').waitFor();
  const registration = (await page.locator('.credential-username').innerText()).trim();
  const studentTemp = (await page.locator('.secret-value').innerText()).trim();
  if (!/^\d{4}00001$/.test(registration)) throw new Error('matrícula inesperada: ' + registration);
  await page.getByRole('button', { name: 'Concluir' }).click();
  await page.getByRole('cell', { name: registration, exact: true }).waitFor();
  await shot(page, '18-alunos');

  await page.goto(`${BASE}/academico/turmas`);
  await page.getByRole('link', { name: 'ADS1N' }).click();
  await page.getByLabel('Incluir aluno (nome, matrícula ou CPF)').fill('Beat');
  await page.getByRole('option', { name: new RegExp(registration) }).click();
  await page.getByRole('cell', { name: 'Beatriz Souza', exact: true }).waitFor();
  await page.getByText('1 / 40').waitFor();
  await shot(page, '19-turma-detalhe');
  step(`aluno cadastrado (matrícula ${registration}) e incluído na turma`);

  // O aluno entra com a MATRÍCULA, configura o Google Authenticator e troca a senha temporária
  await page.getByRole('button', { name: 'Menu do usuário' }).click();
  await page.getByRole('menuitem', { name: 'Sair' }).click();
  await page.goto(`${BASE}/f/lucena-centro/login`);
  await page.getByLabel('Matrícula ou nome de usuário').fill(registration);
  await page.getByLabel('Senha', { exact: true }).fill(studentTemp);
  await page.getByRole('button', { name: 'Entrar' }).click();
  const studentSecret = await setupAuthenticator(page);
  await page.getByLabel('Senha atual').fill(studentTemp);
  await page.getByLabel('Código de 6 dígitos do Google Authenticator').fill(await freshCode(studentSecret));
  await page.getByRole('button', { name: 'Continuar' }).click();
  await page.getByLabel('Nova senha', { exact: true }).fill('Estudo#Forte2026');
  await page.getByLabel('Repetir nova senha').fill('Estudo#Forte2026');
  await page.getByRole('button', { name: 'Confirmar alteração de senha' }).click();
  await page.getByText('Senha alterada com sucesso').waitFor();
  await page.getByLabel('Matrícula ou nome de usuário').fill(registration);
  await page.getByLabel('Senha', { exact: true }).fill('Estudo#Forte2026');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.getByLabel('Código do autenticador').fill(await freshCode(studentSecret));
  await page.getByRole('button', { name: 'Verificar e entrar' }).click();
  await page.getByRole('heading', { name: /Olá, Beatriz/ }).waitFor();
  await page.getByText(registration).first().waitFor();
  if (await page.getByRole('link', { name: 'Cursos' }).count()) throw new Error('aluno não deveria ver o menu acadêmico');
  await page.goto(`${BASE}/academico/alunos`);
  await page.getByText('Você não tem permissão').waitFor();
  await shot(page, '20-aluno-inicio');
  step('aluno entrou com a matrícula + autenticador e não acessa cadastros');

  // 6) Fase 5: professor lança avaliação, nota e frequência; aluno confere no portal
  await page.getByRole('button', { name: 'Menu do usuário' }).click();
  await page.getByRole('menuitem', { name: 'Sair' }).click();
  await page.goto(`${BASE}/f/lucena-centro/login`);
  await page.getByLabel('Matrícula ou nome de usuário').fill('prof.carlos');
  await page.getByLabel('Senha', { exact: true }).fill(teacherTemp);
  await page.getByRole('button', { name: 'Entrar' }).click();
  const teacherSecret = await setupAuthenticator(page);
  await page.getByLabel('Senha atual').fill(teacherTemp);
  await page.getByLabel('Código de 6 dígitos do Google Authenticator').fill(await freshCode(teacherSecret));
  await page.getByRole('button', { name: 'Continuar' }).click();
  await page.getByLabel('Nova senha', { exact: true }).fill('Docencia#Forte2026');
  await page.getByLabel('Repetir nova senha').fill('Docencia#Forte2026');
  await page.getByRole('button', { name: 'Confirmar alteração de senha' }).click();
  await page.getByText('Senha alterada com sucesso').waitFor();
  await page.getByLabel('Matrícula ou nome de usuário').fill('prof.carlos');
  await page.getByLabel('Senha', { exact: true }).fill('Docencia#Forte2026');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.getByLabel('Código do autenticador').fill(await freshCode(teacherSecret));
  await page.getByRole('button', { name: 'Verificar e entrar' }).click();
  await page.getByRole('heading', { name: /Olá, Carlos/ }).waitFor();
  await page.getByRole('navigation', { name: 'Menu principal' }).getByRole('link', { name: 'Minhas turmas' }).click();
  await page.getByText('Lógica de Programação').first().waitFor();
  if (await page.getByText('Banco de Dados').count()) throw new Error('professor viu disciplina de outro professor');
  await shot(page, '21-professor-turmas');
  await page.getByRole('link', { name: 'Abrir diário' }).click();
  await page.getByRole('heading', { name: /Lógica de Programação — turma ADS1N/ }).waitFor();

  await page.getByRole('tab', { name: 'Avaliações' }).click();
  await page.getByLabel('Título').fill('Prova 1');
  await page.getByRole('button', { name: 'Salvar avaliação' }).click();
  await page.getByText('Avaliação salva').waitFor();
  await page.getByRole('tab', { name: 'Notas e situação' }).click();
  await page.getByRole('combobox', { name: 'Avaliação' }).click();
  await page.getByRole('option', { name: /Prova 1/ }).click();
  await page.getByRole('listbox').waitFor({ state: 'detached' });
  await page.getByLabel('Nota de Beatriz Souza').fill('11');
  if (await page.getByRole('button', { name: 'Salvar notas' }).isEnabled()) throw new Error('nota acima do máximo deveria bloquear');
  await page.getByLabel('Nota de Beatriz Souza').fill('8.5');
  await page.getByRole('button', { name: 'Salvar notas' }).click();
  await page.getByText('Notas salvas').waitFor();
  await page.getByRole('cell', { name: '8,50', exact: true }).first().waitFor();

  await page.getByRole('tab', { name: 'Frequência (diário)' }).click();
  await page.getByLabel('Conteúdo ministrado').fill('Algoritmos e fluxogramas');
  await page.getByRole('button', { name: 'Faltou' }).click();
  await page.getByRole('button', { name: 'Salvar frequência' }).click();
  await page.getByText('Frequência registrada').waitFor();
  await page.getByRole('cell', { name: 'Algoritmos e fluxogramas', exact: true }).waitFor();
  await page.getByRole('tab', { name: 'Notas e situação' }).click();
  await page.getByRole('cell', { name: '0%', exact: true }).waitFor();
  await shot(page, '22-diario-notas');
  step('professor lançou avaliação, nota (com bloqueio acima do máximo) e frequência');

  await page.getByRole('button', { name: 'Menu do usuário' }).click();
  await page.getByRole('menuitem', { name: 'Sair' }).click();
  await page.goto(`${BASE}/f/lucena-centro/login`);
  await page.getByLabel('Matrícula ou nome de usuário').fill(registration);
  await page.getByLabel('Senha', { exact: true }).fill('Estudo#Forte2026');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.getByLabel('Código do autenticador').fill(await freshCode(studentSecret));
  await page.getByRole('button', { name: 'Verificar e entrar' }).click();
  await page.getByRole('heading', { name: /Olá, Beatriz/ }).waitFor();
  await page.getByRole('navigation', { name: 'Menu principal' }).getByRole('link', { name: 'Meu painel' }).click();
  await page.getByRole('heading', { name: 'Olá, Beatriz!' }).waitFor();
  await page.getByRole('cell', { name: '8,50', exact: true }).waitFor();
  await page.getByText('2 de 2').waitFor();
  await shot(page, '23-aluno-painel');
  await page.getByRole('link', { name: 'Lógica de Programação' }).click();
  await page.getByText('Algoritmos e fluxogramas').waitFor();
  await page.getByText('Prova 1').waitFor();
  await shot(page, '24-aluno-disciplina');
  await page.goto(`${BASE}/aluno/grade`);
  await page.getByText('0% concluído').waitFor();
  step('aluno vê nota, faltas, frequência, detalhe da disciplina e grade curricular');

  // 7) Responsividade (celular): menu vira gaveta, tabela vira cartões
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
  for (const ctx of browser.contexts()) for (const p of ctx.pages()) await p.screenshot({ path: `${OUT}/falha-${Date.now()}.png`, fullPage: true }).catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
