-- =====================================================================
-- V5 - Catálogo de permissões, planos e cargos-modelo do sistema.
-- Cargos-modelo (faculty_id NULL) são copiados para cada nova faculdade,
-- que depois pode personalizá-los ou criar cargos próprios.
-- =====================================================================

INSERT INTO plans (code, name, max_students, max_users, monthly_price) VALUES
 ('ESSENCIAL',    'Essencial',    1000,  150, 0),
 ('PROFISSIONAL', 'Profissional', 5000,  600, 0),
 ('CORPORATIVO',  'Corporativo',  NULL, NULL, 0);

INSERT INTO permissions (code, module, description, platform_only) VALUES
 ('PLATFORM_ADMIN',          'PLATAFORMA', 'Administração global da plataforma (Master)', TRUE),
 ('FACULTY_SETTINGS_MANAGE', 'INSTITUICAO', 'Gerenciar configurações da faculdade', FALSE),
 ('USER_VIEW',               'USUARIOS', 'Consultar usuários da faculdade', FALSE),
 ('USER_MANAGE',             'USUARIOS', 'Criar, editar, bloquear usuários e redefinir acesso', FALSE),
 ('ROLE_MANAGE',             'USUARIOS', 'Gerenciar cargos e permissões', FALSE),
 ('MFA_RESET',               'SEGURANCA', 'Recuperação de conta: redefinir autenticador de usuários', FALSE),
 ('AUDIT_VIEW',              'SEGURANCA', 'Consultar auditoria', FALSE),
 ('ACCESS_LOG_VIEW',         'SEGURANCA', 'Consultar logs de acesso', FALSE),
 ('SUPPORT_TICKET_MANAGE',   'SUPORTE', 'Gerenciar chamados técnicos', FALSE),
 ('COURSE_VIEW',             'ACADEMICO', 'Consultar cursos, disciplinas e turmas', FALSE),
 ('COURSE_MANAGE',           'ACADEMICO', 'Gerenciar cursos, disciplinas, matriz e turmas', FALSE),
 ('TEACHER_MANAGE',          'ACADEMICO', 'Gerenciar professores', FALSE),
 ('STUDENT_VIEW',            'ACADEMICO', 'Consultar alunos', FALSE),
 ('STUDENT_MANAGE',          'SECRETARIA', 'Cadastrar e alterar alunos', FALSE),
 ('ENROLLMENT_MANAGE',       'SECRETARIA', 'Matrículas, rematrículas, trancamentos e transferências', FALSE),
 ('DOCUMENT_ISSUE',          'SECRETARIA', 'Emitir declarações, históricos e certificados', FALSE),
 ('GRADE_MANAGE',            'ACADEMICO', 'Lançar notas (somente turmas vinculadas para professores)', FALSE),
 ('ATTENDANCE_MANAGE',       'ACADEMICO', 'Lançar frequência (somente turmas vinculadas para professores)', FALSE),
 ('ACADEMIC_REPORTS',        'ACADEMICO', 'Relatórios acadêmicos', FALSE),
 ('FINANCE_VIEW',            'FINANCEIRO', 'Consultar financeiro', FALSE),
 ('FINANCE_MANAGE',          'FINANCEIRO', 'Gerenciar títulos, descontos, bolsas e contas', FALSE),
 ('CHARGE_ISSUE',            'FINANCEIRO', 'Emitir boletos e Pix', FALSE),
 ('PAYMENT_REFUND',          'FINANCEIRO', 'Cancelar e estornar', FALSE),
 ('DEBT_NEGOTIATE',          'FINANCEIRO', 'Negociar débitos', FALSE),
 ('FINANCE_REPORTS',         'FINANCEIRO', 'Relatórios financeiros', FALSE),
 ('REQUEST_TYPE_MANAGE',     'REQUERIMENTOS', 'Configurar catálogo de requerimentos', FALSE),
 ('REQUEST_PROCESS',         'REQUERIMENTOS', 'Analisar e despachar requerimentos', FALSE),
 ('LIBRARY_MANAGE',          'BIBLIOTECA', 'Gerenciar acervo, empréstimos e devoluções', FALSE),
 ('MESSAGE_SEND',            'MENSAGENS', 'Enviar mensagens', FALSE),
 ('MESSAGE_BROADCAST',       'MENSAGENS', 'Enviar mensagens coletivas', FALSE),
 ('DASHBOARD_EXECUTIVE',     'GESTAO', 'Dashboards executivos da direção', FALSE),
 ('TEACHER_PORTAL',          'PORTAL', 'Acesso ao portal do professor', FALSE),
 ('STUDENT_PORTAL',          'PORTAL', 'Acesso ao portal do aluno', FALSE);

INSERT INTO roles (faculty_id, code, name, description, system_role) VALUES
 (NULL, 'DIRETOR_GERAL',     'Diretor Geral',     'Acesso integral à faculdade', TRUE),
 (NULL, 'DIRETOR_ACADEMICO', 'Diretor Acadêmico', 'Gestão acadêmica institucional', TRUE),
 (NULL, 'COORDENADOR',       'Coordenador',       'Coordenação de cursos', TRUE),
 (NULL, 'GERENTE',           'Gerente',           'Gestão administrativa', TRUE),
 (NULL, 'FINANCEIRO',        'Financeiro',        'Operações financeiras', TRUE),
 (NULL, 'SECRETARIA',        'Secretária',        'Secretaria acadêmica', TRUE),
 (NULL, 'PROFESSOR',         'Professor',         'Docente', TRUE),
 (NULL, 'SUPORTE_TI',        'Suporte/TI',        'Suporte técnico da faculdade (sem privilégios globais)', TRUE),
 (NULL, 'BIBLIOTECARIO',     'Bibliotecário',     'Gestão da biblioteca', TRUE),
 (NULL, 'ALUNO',             'Aluno',             'Discente', TRUE);

-- Helper temporário para vincular permissões aos cargos-modelo
CREATE TEMP TABLE _rp (role_code TEXT, perm_code TEXT);
INSERT INTO _rp
SELECT 'DIRETOR_GERAL', code FROM permissions WHERE NOT platform_only AND code NOT IN ('TEACHER_PORTAL','STUDENT_PORTAL');
INSERT INTO _rp VALUES
 ('DIRETOR_ACADEMICO','USER_VIEW'),('DIRETOR_ACADEMICO','COURSE_VIEW'),('DIRETOR_ACADEMICO','COURSE_MANAGE'),
 ('DIRETOR_ACADEMICO','TEACHER_MANAGE'),('DIRETOR_ACADEMICO','STUDENT_VIEW'),('DIRETOR_ACADEMICO','ACADEMIC_REPORTS'),
 ('DIRETOR_ACADEMICO','DASHBOARD_EXECUTIVE'),('DIRETOR_ACADEMICO','MESSAGE_SEND'),('DIRETOR_ACADEMICO','MESSAGE_BROADCAST'),
 ('DIRETOR_ACADEMICO','AUDIT_VIEW'),
 ('COORDENADOR','COURSE_VIEW'),('COORDENADOR','COURSE_MANAGE'),('COORDENADOR','TEACHER_MANAGE'),('COORDENADOR','STUDENT_VIEW'),
 ('COORDENADOR','ACADEMIC_REPORTS'),('COORDENADOR','MESSAGE_SEND'),('COORDENADOR','MESSAGE_BROADCAST'),
 ('GERENTE','USER_VIEW'),('GERENTE','COURSE_VIEW'),('GERENTE','STUDENT_VIEW'),('GERENTE','FINANCE_VIEW'),
 ('GERENTE','FINANCE_REPORTS'),('GERENTE','ACADEMIC_REPORTS'),('GERENTE','DASHBOARD_EXECUTIVE'),('GERENTE','MESSAGE_SEND'),
 ('FINANCEIRO','STUDENT_VIEW'),('FINANCEIRO','FINANCE_VIEW'),('FINANCEIRO','FINANCE_MANAGE'),('FINANCEIRO','CHARGE_ISSUE'),
 ('FINANCEIRO','PAYMENT_REFUND'),('FINANCEIRO','DEBT_NEGOTIATE'),('FINANCEIRO','FINANCE_REPORTS'),('FINANCEIRO','MESSAGE_SEND'),
 ('SECRETARIA','COURSE_VIEW'),('SECRETARIA','STUDENT_VIEW'),('SECRETARIA','STUDENT_MANAGE'),('SECRETARIA','ENROLLMENT_MANAGE'),
 ('SECRETARIA','DOCUMENT_ISSUE'),('SECRETARIA','REQUEST_PROCESS'),('SECRETARIA','MESSAGE_SEND'),
 ('PROFESSOR','TEACHER_PORTAL'),('PROFESSOR','GRADE_MANAGE'),('PROFESSOR','ATTENDANCE_MANAGE'),('PROFESSOR','MESSAGE_SEND'),
 ('SUPORTE_TI','USER_VIEW'),('SUPORTE_TI','USER_MANAGE'),('SUPORTE_TI','MFA_RESET'),('SUPORTE_TI','ACCESS_LOG_VIEW'),
 ('SUPORTE_TI','AUDIT_VIEW'),('SUPORTE_TI','SUPPORT_TICKET_MANAGE'),('SUPORTE_TI','REQUEST_TYPE_MANAGE'),
 ('SUPORTE_TI','REQUEST_PROCESS'),('SUPORTE_TI','CHARGE_ISSUE'),('SUPORTE_TI','MESSAGE_SEND'),
 ('BIBLIOTECARIO','LIBRARY_MANAGE'),('BIBLIOTECARIO','STUDENT_VIEW'),('BIBLIOTECARIO','MESSAGE_SEND'),
 ('ALUNO','STUDENT_PORTAL'),('ALUNO','MESSAGE_SEND');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM _rp
JOIN roles r ON r.code = _rp.role_code AND r.faculty_id IS NULL
JOIN permissions p ON p.code = _rp.perm_code;
DROP TABLE _rp;
