# ERP Universitário Lucena

ERP SaaS multi-faculdade para instituições de ensino superior particulares: gestão acadêmica, financeira,
administrativa e institucional, com isolamento entre faculdades e verificação em duas etapas obrigatória
(Google Authenticator / TOTP) para **todos** os usuários.

| Camada | Tecnologia |
|---|---|
| Frontend | Angular 20, TypeScript, Angular Material, layout responsivo |
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Bean Validation, Maven |
| Banco | PostgreSQL 16 (fonte oficial dos dados acadêmicos e financeiros), migrations Flyway |
| Segurança | JWT curto + sessão no servidor, TOTP RFC 6238, Argon2id, AES-256-GCM |
| Firebase | Planejado para Storage (arquivos), FCM (push) e Firestore (tempo real) — ver [Arquitetura](docs/ARQUITETURA.md) |

## Status por fase

| Fase | Escopo | Status |
|---|---|---|
| 1 | Arquitetura, banco de dados (schema completo), multi-tenancy, autenticação | ✅ Entregue |
| 2 | Google Authenticator, códigos de recuperação, troca de dispositivo, step-up, segurança | ✅ Entregue |
| 3 | Painel Master, faculdades, planos, usuários, cargos e permissões, auditoria | ✅ Entregue |
| 4 | Cursos, disciplinas, matriz curricular, períodos letivos, turmas (horários), professores, alunos (login por matrícula), funcionários | ✅ Entregue |
| 5 | Portal do professor (diário: avaliações, notas, recuperação, frequência, plano de ensino, exportação Excel/CSV) e portal do aluno (painel, horários, notas, faltas, frequência, histórico, grade curricular) | ✅ Entregue |
| 6–12 | Secretaria, financeiro, requerimentos, mensagens, dashboards, biblioteca, relatórios | ⏳ Schema pronto, APIs/telas pendentes |

Módulos ainda não integrados aparecem na tela inicial como **"não integrado"** — não há botões decorativos.

## Como acessar (forma mais simples: Docker)

Pré-requisito: [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado e aberto.

```bash
git clone https://github.com/pedrosoaresdev/erplucenafaculdade.git
```

```bash
cd erplucenafaculdade
```

1. Gere o arquivo de configuração `.env` (cria as chaves secretas automaticamente; o e-mail do dono já vem preenchido):
   - **Windows** (PowerShell, na pasta do projeto): `powershell -ExecutionPolicy Bypass -File .\gerar-env.ps1`
   - **macOS/Linux**: `./gerar-env.sh`

   Guarde uma cópia do `.env` em local seguro: sem a `ERP_ENCRYPTION_KEY`, os autenticadores precisam ser reconfigurados.
2. Suba tudo (banco, backend e frontend):

```bash
docker compose up --build
```

3. No console aparece um quadro **INSTALAÇÃO INICIAL** com o **código de instalação** (ex.: `K7QM-2XPA-9DTR`).
4. Abra **http://localhost:8081** — o sistema abre a tela de **Instalação inicial**:
   1. informe o seu e-mail (`ERP_OWNER_EMAIL`) e o código do console;
   2. configure o **Google Authenticator** (QR Code ou chave) e digite o código de 6 dígitos;
   3. crie o seu **nome de usuário e senha**;
   4. guarde os códigos de recuperação. Pronto: você é o administrador Master.
5. Daí em diante, o acesso é em http://localhost:8081/login com usuário + senha + código do Google Authenticator.
   A instalação não pode ser repetida. Os demais usuários são criados por você (ou pela gestão de cada faculdade)
   e recebem senha temporária.
6. Em **Faculdades › Nova faculdade**, crie a instituição com o Diretor Geral inicial. O endereço de login
   dela será `http://localhost:8081/f/<identificador>/login`.

Os dados ficam no volume `erp-db` (persistem entre reinicializações). Para publicar na internet, use HTTPS e
`ERP_COOKIE_SECURE=true`.

## Executando localmente (desenvolvimento)

Pré-requisitos: Java 21, Maven 3.9+, Node 22+, PostgreSQL 16.

```bash
# 1) Banco
sudo -u postgres psql -c "create user erp with password 'erp' createdb;" -c "create database erp owner erp;" -c "create database erp_test owner erp;"
```

```bash
# 2) Backend (perfil dev: chaves de desenvolvimento e Master inicial)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
# 3) Frontend (proxy /api -> :8080)
cd frontend
npm install
npm start
```

Acesse:

- Painel Master: http://localhost:4200/login — usuário `master`, senha `Lucena@Plataforma#2026` (somente perfil dev).
  No primeiro acesso, configure o Google Authenticator (QR Code ou chave).
- Login de uma faculdade: `http://localhost:4200/f/<identificador>/login` (o identificador é definido ao criar a faculdade).
- API documentada (OpenAPI/Swagger): http://localhost:8080/swagger-ui.html

## Testes

```bash
# Backend: unitários + integração contra PostgreSQL real (banco erp_test, recriado a cada execução)
cd backend && mvn verify
```

```bash
# Frontend: unitários (Karma/Jasmine, Chrome headless)
cd frontend && npm test -- --watch=false
```

```bash
# Ponta a ponta no navegador (Playwright; na primeira vez: npx playwright install chromium).
# Recria o banco dev, sobe o backend e testa o fluxo completo.
# Requer `npm start` rodando em :4200.
cd frontend && ./e2e/reset-and-run.sh e2e-screens
```

O E2E cobre: login com senha errada, primeiro acesso com configuração obrigatória do autenticador,
códigos de recuperação, criação de faculdade, primeiro acesso do Diretor com troca obrigatória de senha
(senha atual + código TOTP), recusa de senha que contém o usuário, segundo acesso pedindo só o código,
bloqueio do painel Master para usuários da faculdade; cadastro de disciplinas, curso e matriz curricular,
abertura de período letivo, professor com acesso, turma com oferta de disciplina/horário e bloqueio de choque
de horário, aluno com matrícula gerada automaticamente e incluído na turma, primeiro acesso do aluno **pela
matrícula** com Google Authenticator; layout em celular sem rolagem horizontal e painel de acessibilidade.

## Variáveis de ambiente (produção)

| Variável | Descrição |
|---|---|
| `ERP_DB_URL`, `ERP_DB_USER`, `ERP_DB_PASSWORD` | Conexão PostgreSQL |
| `ERP_JWT_SECRET` | **Obrigatória.** Base64 ≥ 32 bytes (`openssl rand -base64 48`) |
| `ERP_ENCRYPTION_KEY` | **Obrigatória.** Base64 de exatamente 32 bytes (`openssl rand -base64 32`). Cifra os segredos TOTP — guarde em cofre; perdê-la invalida todos os autenticadores |
| `ERP_COOKIE_SECURE` | `true` em produção (HTTPS) |
| `ERP_CORS_ORIGINS` | Origens permitidas do frontend, separadas por vírgula |
| `ERP_TOTP_ISSUER` | Nome exibido no Google Authenticator |
| `ERP_BREACHED_PASSWORD_CHECK` | `true` para recusar senhas vazadas (HaveIBeenPwned, k-anonymity) |
| `ERP_BOOTSTRAP_MASTER_USERNAME` / `_PASSWORD` / `_EMAIL` | Cria o primeiro Master se não existir nenhum |
| `ERP_AUTH_RATE_LIMIT` | Requisições/minuto por IP nos endpoints de autenticação (padrão 30) |

Documentação técnica detalhada: [docs/ARQUITETURA.md](docs/ARQUITETURA.md) e [docs/SEGURANCA.md](docs/SEGURANCA.md).

## Capturas (geradas pelo teste E2E)

| Primeiro acesso — Google Authenticator | Verificação em duas etapas | Alterar senha (etapa 2) |
|---|---|---|
| ![](docs/screenshots/02-setup-autenticador.png) | ![](docs/screenshots/08-verificacao-totp.png) | ![](docs/screenshots/07-nova-senha.png) |

| Painel Master | Início do Diretor | Celular — usuários |
|---|---|---|
| ![](docs/screenshots/03-dashboard-plataforma.png) | ![](docs/screenshots/09-inicio-diretor.png) | ![](docs/screenshots/14-mobile-usuarios.png) |
