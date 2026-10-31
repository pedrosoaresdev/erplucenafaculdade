# Segurança, LGPD e auditoria

## Autenticação

1. **Etapa 1 — senha.** Mensagem genérica para usuário inexistente ou senha incorreta (sem enumeração de
   contas); tempo de resposta equalizado com hash Argon2 fictício. 5 falhas → bloqueio de 15 min (configurável).
2. **Etapa 2 — TOTP (Google Authenticator).** Um *desafio* opaco (somente o SHA-256 é salvo), válido por 5 min,
   máximo de 5 tentativas, ligado ao propósito (configuração × verificação).
3. **Primeiro acesso:** o acesso aos módulos fica bloqueado até configurar o autenticador. O segredo é gerado
   no servidor (160 bits), o QR Code é gerado localmente (o segredo não trafega para serviços de terceiros) e o
   2º fator só é ativado após **validação real de um código**. "Gerar novo segredo" substitui o pendente.
4. **Acessos seguintes:** não há novo QR Code; apenas o código de 6 dígitos.

### TOTP
- RFC 6238, HMAC-SHA1, 6 dígitos, 30 s (compatível com Google Authenticator; testado com os vetores da RFC).
- Tolerância de relógio de ±1 passo (±30 s).
- **Anti-replay:** o último passo aceito é gravado; códigos do mesmo passo ou anteriores são recusados.
  Bloqueio pessimista evita aceitação concorrente do mesmo código.
- Falhas de código contam no bloqueio da conta (protege contra força bruta abrindo vários desafios).
- Segredos cifrados com **AES-256-GCM** (IV aleatório, autenticado, prefixo de versão para rotação de chave);
  nunca retornados após a configuração.

### Códigos de recuperação
- 10 códigos (`XXXXX-XXXXX`, ~50 bits), exibidos uma única vez com opções de imprimir, baixar e copiar.
- Armazenados apenas como SHA-256; consumo atômico (uso único). O uso gera notificação e auditoria.
- Sem códigos: recuperação de conta pela equipe autorizada (`MFA_RESET`), exigindo **motivo** e **registro da
  verificação de identidade**, auditada; o usuário reconfigura o autenticador no próximo acesso.

### Sessões e tokens
- Access token JWT HS256 de 15 min, vinculado a uma sessão do servidor (`sid`).
- A cada requisição, sessão, usuário, faculdade e permissões são validados **no banco** — revogação imediata.
- Refresh token opaco (SHA-256 no banco) em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`,
  rotacionado a cada uso. O refresh exige o cabeçalho `X-ERP-Client` (mitigação de CSRF).
- O frontend mantém o access token somente em memória (nada sensível em `localStorage`).

### Operações sensíveis (step-up)
Exigem senha atual + código TOTP, gerando token de uso único (5 min) **vinculado ao propósito**:
alterar senha, trocar dispositivo, desativar autenticador, regenerar códigos, alterar e-mail.

### Alteração de senha
- Etapa 1: senha atual + código; etapa 2: nova senha + confirmação, com indicador de força.
- Regras: mínimo 8 caracteres, iguais, não vazia, diferente da atual, sem conter o usuário/matrícula, fora da
  lista de ~2.100 senhas comuns; opcionalmente verificação de senha vazada (HIBP, k-anonymity).
- Hash **Argon2id** (m=19 MiB, t=2, p=1). Todas as sessões são revogadas, auditoria + notificação, novo login.

### Troca de autenticador
Step-up com o dispositivo **atual** → novo segredo pendente (o atual continua válido) → validação de código do
**novo** dispositivo → segredo anterior revogado, novos códigos de recuperação, demais sessões encerradas.

## Autorização (RBAC)
- Permissões no banco (`permissions`), agrupadas em cargos por faculdade; `@PreAuthorize` em todos os endpoints
  administrativos; `/api/platform/**` exige `PLATFORM_ADMIN`.
- **Anti-escalonamento:** só é possível conceder cargos/permissões que o próprio operador possui (exceto
  permissões de escopo próprio: portal do aluno/professor e mensagens). Ninguém altera os próprios cargos ou situação.
- Suporte/TI da faculdade não possui privilégios globais.
- Usuário com senha temporária recebe zero permissões até concluir a troca.

## Proteções gerais
- SQL Injection: JPA/consultas parametrizadas exclusivamente.
- XSS: Angular escapa interpolação por padrão; API responde JSON com CSP `default-src 'none'`, `X-Frame-Options: DENY`,
  `Referrer-Policy: no-referrer`.
- Rate limiting por IP em `/api/auth/**` e step-up (complementar com gateway/Redis em múltiplas instâncias).
- Erros padronizados (RFC 9457) sem stack trace ou SQL.
- Validação de entrada com Bean Validation e restrições `CHECK` no banco.

## Auditoria e LGPD
- `audit_logs` é **somente-inserção** (trigger no PostgreSQL bloqueia UPDATE/DELETE) e sem FK, sobrevivendo a
  exclusões. Registra: criação/alteração/situação de faculdades e usuários, cargos e permissões, redefinição de
  senha e de autenticador, ativação/troca/desativação de MFA, uso de código de recuperação, alteração de senha,
  e-mail, configurações, revogação de sessões.
- `access_logs`: todas as tentativas de login (sucesso/falha, IP, agente) — visíveis ao usuário em
  "Histórico de acessos".
- Nunca registrados: senhas, segredos TOTP, códigos de recuperação, tokens.
- Mascaramento de CPF/e-mail disponível (`Masking`); endpoint público expõe apenas nome e logotipo.
- Retenção configurável por faculdade (`data_retention_years`); faculdades com dados são arquivadas, não excluídas.

## Backup e recuperação de desastres (recomendação operacional)
- PostgreSQL com backups completos diários + WAL contínuo (PITR), retenção ≥ 30 dias, réplica em outra zona.
- `ERP_ENCRYPTION_KEY` e `ERP_JWT_SECRET` em cofre de segredos com backup separado do banco
  (sem a chave de criptografia, os autenticadores precisam ser reconfigurados).
- Teste de restauração periódico.
