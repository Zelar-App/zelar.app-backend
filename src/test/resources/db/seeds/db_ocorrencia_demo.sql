-- ========== CASO DE SUCESSO 2: INSERTS VÁLIDOS ==========
-- IDs e e-mails diferentes do primeiro script, então os dois podem rodar no mesmo banco.

-- Usuários
INSERT INTO usuario (id, nome, email, senha_hash, telefone, papel) VALUES
('44444444-4444-4444-4444-444444444444', 'Ana Costa',     'ana.costa@email.com',  '$2a$10$hashFicticioAna1234567890abcdefghijklmnopqrstuvwx',  '+55 31 99999-0004', 'CIDADAO'),
('55555555-5555-5555-5555-555555555555', 'Carlos Pereira','carlos.p@email.com',   '$2a$10$hashFicticioCarlos12345678abcdefghijklmnopqrstuvw',  NULL,                'CIDADAO'),
('66666666-6666-6666-6666-666666666666', 'Fernanda Lima', 'fernanda@zelar.app',   '$2a$10$hashFicticioFernanda123456abcdefghijklmnopqrstuvw',  '31988880006',       'ADMINISTRADOR'),
-- nome no limite de 100 caracteres, sem nenhuma ocorrência associada
('77777777-7777-7777-7777-777777777777', LEFT(REPEAT('Nome Longo ', 20), 100), 'sem.ocorrencia@email.com', '$2a$10$hashFicticioSemOcorrencia12abcdefghijklmnopqrstuv', '31977770007', 'CIDADAO');

-- Ocorrências: todas as categorias, todos os status e todas as prioridades
INSERT INTO ocorrencia
  (id, descricao, status, categoria, latitude, longitude, endereco_aproximado, prioridade, data_atualizacao, usuario_id) VALUES
('bbbbbbbb-0000-0000-0000-000000000001', 'Cratera na pista após obra inacabada',  'EM_ANALISE', 'BURACO',      -19.9250, -43.9380, 'Rua Espírito Santo, 800',    'ALTA',  CURRENT_TIMESTAMP, '44444444-4444-4444-4444-444444444444'),
('bbbbbbbb-0000-0000-0000-000000000002', 'Três postes apagados no quarteirão',    'ENVIADA',    'ILUMINACAO',  -19.9330, -43.9410, 'Rua Sergipe, 120',           'ALTA',  NULL,              '44444444-4444-4444-4444-444444444444'),
('bbbbbbbb-0000-0000-0000-000000000003', 'Caçamba de entulho abandonada',         'RESOLVIDA',  'LIXO',        -19.9280, -43.9290, NULL,                         'MEDIA', CURRENT_TIMESTAMP, '55555555-5555-5555-5555-555555555555'),
('bbbbbbbb-0000-0000-0000-000000000004', 'Bueiro entupido causa alagamento',      'EM_ANALISE', 'ALAGAMENTO',  -19.9150, -43.9450, 'Av. Francisco Sales, 1500',  'ALTA',  CURRENT_TIMESTAMP, '55555555-5555-5555-5555-555555555555'),
('bbbbbbbb-0000-0000-0000-000000000005', 'Calçada sem rampa de acessibilidade',   'ENVIADA',    'CALCADA',     -19.9210, -43.9360, 'Rua Tupis, 300',             'BAIXA', NULL,              '44444444-4444-4444-4444-444444444444'),
('bbbbbbbb-0000-0000-0000-000000000006', 'Semáforo piscando em amarelo',          'RESOLVIDA',  'SINALIZACAO', -19.9190, -43.9310, 'Av. Amazonas com Rua Bahia', 'MEDIA', CURRENT_TIMESTAMP, '55555555-5555-5555-5555-555555555555'),
('bbbbbbbb-0000-0000-0000-000000000007', 'Poluição visível no córrego',           'ENVIADA',    'AMBIENTAL',   -19.8800, -43.9700, 'Córrego do Leitão',          NULL,    NULL,              '44444444-4444-4444-4444-444444444444'),
-- valores no limite: endereço com 255 caracteres, coordenadas extremas, sem prioridade
('bbbbbbbb-0000-0000-0000-000000000008', 'Teste de limites de tamanho e coordenadas', 'ENVIADA', 'BURACO', -90.0, 180.0, LEFT(REPEAT('Endereço longo ', 30), 255), NULL, NULL, '66666666-6666-6666-6666-666666666666');

-- Atualização válida (transição de status), que também preenche data_atualizacao
UPDATE ocorrencia
SET status = 'RESOLVIDA', data_atualizacao = CURRENT_TIMESTAMP
WHERE id = 'bbbbbbbb-0000-0000-0000-000000000001';

-- ========== CONSULTAS DE VERIFICAÇÃO ==========

-- Devem aparecer as 7 categorias oficiais (a BURACO com 2 ocorrências)
SELECT categoria, COUNT(*) AS total FROM ocorrencia WHERE id::text LIKE 'bbbbbbbb%' GROUP BY categoria ORDER BY categoria;

-- Distribuição por status e prioridade (NULL deve aparecer como linha própria)
SELECT status, prioridade, COUNT(*) AS total
FROM ocorrencia WHERE id::text LIKE 'bbbbbbbb%'
GROUP BY status, prioridade ORDER BY status, prioridade;

-- Usuário sem ocorrências deve aparecer com total = 0
SELECT u.nome, u.papel, COUNT(o.id) AS total
FROM usuario u LEFT JOIN ocorrencia o ON o.usuario_id = u.id
WHERE u.id IN ('44444444-4444-4444-4444-444444444444','55555555-5555-5555-5555-555555555555',
               '66666666-6666-6666-6666-666666666666','77777777-7777-7777-7777-777777777777')
GROUP BY u.nome, u.papel ORDER BY total DESC;

-- Confirma os limites de tamanho (espere 100 e 255)
SELECT LENGTH(nome) AS tam_nome FROM usuario WHERE id = '77777777-7777-7777-7777-777777777777';
SELECT LENGTH(endereco_aproximado) AS tam_endereco FROM ocorrencia WHERE id = 'bbbbbbbb-0000-0000-0000-000000000008';

-- Confirma que a atualização gravou a data
SELECT id, status, data_criacao, data_atualizacao
FROM ocorrencia WHERE id = 'bbbbbbbb-0000-0000-0000-000000000001';

-- Índice por usuário
EXPLAIN SELECT * FROM ocorrencia WHERE usuario_id = '44444444-4444-4444-4444-444444444444';
