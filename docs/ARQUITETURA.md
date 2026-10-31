# Arquitetura

## Visão geral

```
 Navegador (Angular SPA)                      Backend (Spring Boot)                     PostgreSQL
 ┌──────────────────────┐   HTTPS / JSON    ┌──────────────────────────────┐   JDBC   ┌───────────────┐
 │ access token em      │ ───────────────▶ │ Filtro rate limit (auth)     │ ───────▶ │ 56 tabelas    │
 │ memória              │  Bearer JWT 15min │ JWT → SessionAuthConverter   │          │ Flyway V1..V5 │
 │ refresh = cookie     │ ◀─────────────── │   (sessão, usuário, faculdade│          │ audit_logs    │
 │ HttpOnly SameSite    │                   │    e permissões do BANCO)    │          │ append-only   │
 └──────────────────────┘                   │ @PreAuthorize + TenantResolver│          └───────────────┘
                                            │ Serviços / Auditoria          │
                                            └──────────────────────────────┘
```

### Pacotes do backend (`br.com.lucena.erp`)

| Pacote | Responsabilidade |
|---|---|
| `auth` | Login em etapas, desafios, TOTP (ativação/verificação), códigos de recuperação, sessões |
| `me` | Perfil do próprio usuário: dados, step-up, troca de senha, gerenciamento do autenticador, sessões, histórico, notificações |
| `platform` | Painel Master: faculdades (tenants), planos, configurações, dashboard; endpoint público de branding |
| `iam` | Usuários, cargos (RBAC), permissões, proteção contra escalonamento de privilégio, bootstrap do Master |
| `audit` | Auditoria (append-only) e logs de acesso |
| `security` | JWT, conversor de sessão, TOTP, política de senha, resolução de tenant, rate limit |
| `crypto` | AES-256-GCM, tokens aleatórios, hash, Argon2id |
| `notification` | Notificações persistidas + gateway de push (FCM plugável) |
| `common` | Erros RFC 9457, paginação, validação CPF/CNPJ, mascaramento LGPD |

## Multi-tenancy

- Modelo **banco compartilhado, schema compartilhado, coluna `faculty_id`** em toda tabela de tenant (com índice e FK).
- A faculdade de um usuário comum vem **sempre** do usuário autenticado, carregado do banco a cada requisição
  (`SessionAuthenticationConverter`). Parâmetros `facultyId` enviados pelo cliente só são aceitos para o
  administrador Master (`TenantResolver`); para os demais, valor diferente da própria faculdade → **403**.
- Consultas de serviço sempre filtram por `faculty_id` (`findByIdAndFacultyId`, etc.): um ID de outra faculdade
  retorna **404**, sem revelar existência.
- Usernames são únicos **por faculdade** (índice parcial); o login é escopado pelo identificador da faculdade
  na URL (`/f/<slug>/login`). O Master não pertence a faculdade (`faculty_id NULL`, restrição `CHECK`).
- Suspender/arquivar uma faculdade revoga imediatamente todas as sessões dela.
- Testes: `TenantIsolationIT`.

## Modelo de dados (resumo)

- **V1 núcleo**: `plans`, `faculties`, `faculty_settings`, `users`, `permissions`, `roles`, `role_permissions`,
  `user_roles`, `user_authenticator_secrets`, `recovery_codes`, `auth_challenges`, `user_sessions`,
  `access_logs`, `audit_logs` (trigger impede UPDATE/DELETE), `notifications`, `support_tickets`.
- **V2 acadêmico**: `students`, `teachers`, `employees`, `courses`, `subjects`, `curriculum`, `academic_terms`,
  `classes`, `class_students`, `class_subjects`, `enrollments`, `enrollment_subjects`, `contracts`,
  `assessments`, `grades`, `attendance_sessions`, `attendance`.
- **V3 financeiro**: `cost_centers`, `scholarships`, `financial_titles`, `discounts`, `charges`, `payments`
  (índice único em `provider_event_id` = idempotência de webhook), `webhook_events`, `accounts_receivable`,
  `accounts_payable`, `financial_simulations`.
- **V4 serviços**: `request_types`, `requests`, `request_history`, `documents`, `signatures`, `conversations`,
  `conversation_participants`, `messages`, `books`, `loans`, `reservations`, `book_favorites`, `annotations`.
- **V5 seed**: catálogo de permissões, planos e os 10 cargos-modelo (Diretor Geral, Diretor Acadêmico,
  Coordenador, Gerente, Financeiro, Secretária, Professor, Suporte/TI, Bibliotecário, Aluno), copiados para cada
  nova faculdade.

Valores monetários: `NUMERIC(14,2)`. Datas/horas: `timestamptz` (UTC). Restrições `CHECK` para domínios
(situações, percentuais, CPF/CNPJ numéricos, notas 0–10).

## Firebase (papel complementar)

O PostgreSQL é a fonte oficial. O Firebase será usado sem duplicar dados críticos:

| Serviço | Uso previsto | Estado |
|---|---|---|
| Firebase Storage | Arquivos (fotos, anexos, documentos emitidos); o banco guarda metadados, hash e caminho | Não integrado |
| Firebase Cloud Messaging | Push de notificações (interface `PushGateway`; hoje `NoopPushGateway`) | Não integrado |
| Firestore | Presença/"digitando" e entrega em tempo real de mensagens; mensagens persistidas no PostgreSQL | Não integrado |
| Firebase Authentication | **Não usado como provedor primário**: o requisito de matrícula + senha + TOTP próprio com auditoria é atendido pelo backend. Se necessário, o backend pode emitir *custom tokens* Firebase após o login para acesso ao Storage/Firestore com regras por `faculty_id` | Não integrado |

## Endpoints (principais)

Documentação completa e interativa em `/swagger-ui.html`.

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/login` | Etapa 1: faculdade + usuário/matrícula + senha → desafio |
| POST | `/api/auth/mfa/setup` | Primeiro acesso: QR Code + chave (gera novo segredo enquanto não ativado) |
| POST | `/api/auth/mfa/activate` | Valida o código e ativa o 2º fator; retorna códigos de recuperação (única vez) |
| POST | `/api/auth/mfa/verify` | Etapa 2: código TOTP |
| POST | `/api/auth/mfa/recovery` | Etapa 2 alternativa: código de recuperação |
| POST | `/api/auth/refresh`, `/api/auth/logout` | Renovação (cookie HttpOnly + cabeçalho `X-ERP-Client`) e logout |
| GET/PATCH | `/api/me` | Perfil |
| POST | `/api/me/step-up` | Senha atual + TOTP → token de uso único por propósito |
| POST | `/api/me/password` | Troca de senha (exige step-up) — revoga todas as sessões |
| GET/POST | `/api/me/mfa`, `/mfa/rotation/start`, `/mfa/rotation/confirm`, `/mfa/disable`, `/mfa/recovery-codes` | Gerenciar autenticador |
| GET/DELETE/POST | `/api/me/sessions`, `/sessions/{id}`, `/sessions/revoke-others` | Sessões |
| GET | `/api/me/access-history`, `/api/me/notifications` | Histórico e notificações |
| * | `/api/platform/**` | Painel Master (somente `PLATFORM_ADMIN`) |
| * | `/api/admin/users/**`, `/roles/**`, `/permissions`, `/faculty/settings`, `/audit`, `/access-logs` | Administração da instituição (RBAC) |
| GET | `/api/public/faculties/{slug}` | Nome/logo para a tela de login |
