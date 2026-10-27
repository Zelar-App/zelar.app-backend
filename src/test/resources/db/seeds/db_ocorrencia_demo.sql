-- ============================================================
-- ZELAR.APP - VALIDACAO DAS MIGRATIONS V1, V2 E V3
-- PR #84 - Testes de integridade do banco de dados
--
-- Banco: PostgreSQL
-- Ambiente: desenvolvimento/testes
--
-- ATENCAO:
-- 1. Nao executar em producao.
-- 2. As migrations V1, V2 e V3 devem estar aplicadas.
-- 3. Os hashes abaixo sao ficticios. Nao testam login.
-- 4. Coordenadas sao testadas apenas quanto ao NOT NULL.
--    Faixas geograficas sao validadas na aplicacao Java.
-- 5. A transacao sera revertida ao final.
-- ============================================================

BEGIN;

-- ============================================================
-- 1. MASSA DE DADOS VALIDOS
-- ============================================================

INSERT INTO usuario (
    id, nome, email, senha_hash, telefone, papel
) VALUES
(
    '44444444-4444-4444-4444-444444444444',
    'Ana Costa',
    'ana.costa@email.com',
    '$2a$10$hashFicticioAna1234567890abcdefghijklmnopqrstuvwx',
    '+55 31 99999-0004',
    'CIDADAO'
),
(
    '55555555-5555-5555-5555-555555555555',
    'Carlos Pereira',
    'carlos.p@email.com',
    '$2a$10$hashFicticioCarlos12345678abcdefghijklmnopqrstuvw',
    NULL,
    'CIDADAO'
),
(
    '66666666-6666-6666-6666-666666666666',
    'Fernanda Lima',
    'fernanda@zelar.app',
    '$2a$10$hashFicticioFernanda123456abcdefghijklmnopqrstuvw',
    '31988880006',
    'ADMINISTRADOR'
),
(
    '77777777-7777-7777-7777-777777777777',
    LEFT(REPEAT('Nome Longo ', 20), 100),
    'sem.ocorrencia@email.com',
    '$2a$10$hashFicticioSemOcorrencia12abcdefghijklmnopqrstuv',
    '31977770007',
    'CIDADAO'
);

-- ============================================================
-- 2. OCORRENCIAS VALIDAS
-- Todas as categorias, status e prioridades
-- ============================================================

INSERT INTO ocorrencia (
    id,
    descricao,
    status,
    categoria,
    latitude,
    longitude,
    endereco_aproximado,
    prioridade,
    data_atualizacao,
    usuario_id
) VALUES
(
    'bbbbbbbb-0000-0000-0000-000000000001',
    'Cratera na pista apos obra inacabada',
    'EM_ANALISE',
    'BURACO',
    -19.9250,
    -43.9380,
    'Rua Espirito Santo, 800',
    'ALTA',
    CURRENT_TIMESTAMP,
    '44444444-4444-4444-4444-444444444444'
),
(
    'bbbbbbbb-0000-0000-0000-000000000002',
    'Tres postes apagados',
    'ENVIADA',
    'ILUMINACAO',
    -19.9330,
    -43.9410,
    'Rua Sergipe, 120',
    'ALTA',
    NULL,
    '44444444-4444-4444-4444-444444444444'
),
(
    'bbbbbbbb-0000-0000-0000-000000000003',
    'Entulho abandonado',
    'RESOLVIDA',
    'LIXO',
    -19.9280,
    -43.9290,
    NULL,
    'MEDIA',
    CURRENT_TIMESTAMP,
    '55555555-5555-5555-5555-555555555555'
),
(
    'bbbbbbbb-0000-0000-0000-000000000004',
    'Bueiro entupido',
    'EM_ANALISE',
    'ALAGAMENTO',
    -19.9150,
    -43.9450,
    'Av. Francisco Sales, 1500',
    'ALTA',
    CURRENT_TIMESTAMP,
    '55555555-5555-5555-5555-555555555555'
),
(
    'bbbbbbbb-0000-0000-0000-000000000005',
    'Calcada sem acessibilidade',
    'ENVIADA',
    'CALCADA',
    -19.9210,
    -43.9360,
    'Rua Tupis, 300',
    'BAIXA',
    NULL,
    '44444444-4444-4444-4444-444444444444'
),
(
    'bbbbbbbb-0000-0000-0000-000000000006',
    'Semaforo com defeito',
    'RESOLVIDA',
    'SINALIZACAO',
    -19.9190,
    -43.9310,
    'Av. Amazonas',
    'MEDIA',
    CURRENT_TIMESTAMP,
    '55555555-5555-5555-5555-555555555555'
),
(
    'bbbbbbbb-0000-0000-0000-000000000007',
    'Poluicao no corrego',
    'ENVIADA',
    'AMBIENTAL',
    -19.8800,
    -43.9700,
    'Corrego do Leitao',
    NULL,
    NULL,
    '44444444-4444-4444-4444-444444444444'
),
(
    'bbbbbbbb-0000-0000-0000-000000000008',
    'Teste de limites',
    'ENVIADA',
    'BURACO',
    -90.0,
    180.0,
    LEFT(REPEAT('Endereco longo ', 30), 255),
    NULL,
    NULL,
    '66666666-6666-6666-6666-666666666666'
);

-- Atualizacao valida de status
UPDATE ocorrencia
SET
    status = 'RESOLVIDA',
    data_atualizacao = CURRENT_TIMESTAMP
WHERE id = 'bbbbbbbb-0000-0000-0000-000000000001';

-- ============================================================
-- 3. VALIDACOES POSITIVAS AUTOMATIZADAS
-- ============================================================

DO $$
DECLARE
    v_total INTEGER;
BEGIN

    SELECT COUNT(DISTINCT categoria)
    INTO v_total
    FROM ocorrencia
    WHERE id::text LIKE 'bbbbbbbb%';

    IF v_total <> 7 THEN
        RAISE EXCEPTION
            'FALHOU: Esperadas 7 categorias, encontrado %',
            v_total;
    END IF;

    RAISE NOTICE 'PASSOU: 7 categorias oficiais';

    SELECT COUNT(*)
    INTO v_total
    FROM ocorrencia
    WHERE id::text LIKE 'bbbbbbbb%';

    IF v_total <> 8 THEN
        RAISE EXCEPTION
            'FALHOU: Esperadas 8 ocorrencias, encontrado %',
            v_total;
    END IF;

    RAISE NOTICE 'PASSOU: 8 ocorrencias validas';

    SELECT COUNT(*)
    INTO v_total
    FROM usuario
    WHERE id = '77777777-7777-7777-7777-777777777777'
      AND LENGTH(nome) = 100;

    IF v_total <> 1 THEN
        RAISE EXCEPTION
            'FALHOU: Limite do nome';
    END IF;

    RAISE NOTICE 'PASSOU: Limite de 100 caracteres';

    SELECT COUNT(*)
    INTO v_total
    FROM ocorrencia
    WHERE id = 'bbbbbbbb-0000-0000-0000-000000000008'
      AND LENGTH(endereco_aproximado) = 255;

    IF v_total <> 1 THEN
        RAISE EXCEPTION
            'FALHOU: Limite do endereco';
    END IF;

    RAISE NOTICE 'PASSOU: Limite de 255 caracteres';

    SELECT COUNT(*)
    INTO v_total
    FROM ocorrencia
    WHERE id = 'bbbbbbbb-0000-0000-0000-000000000001'
      AND status = 'RESOLVIDA'
      AND data_atualizacao IS NOT NULL;

    IF v_total <> 1 THEN
        RAISE EXCEPTION
            'FALHOU: Atualizacao da ocorrencia';
    END IF;

    RAISE NOTICE 'PASSOU: Atualizacao de status';

END $$;

-- ============================================================
-- 4. TESTES NEGATIVOS DE CONSTRAINTS
--
-- SQLSTATE:
-- 23514 = CHECK VIOLATION
-- 23502 = NOT NULL VIOLATION
-- 23503 = FOREIGN KEY VIOLATION
--
-- Cada teste deve produzir a violacao esperada.
-- Se o INSERT for aceito, o teste falha.
-- ============================================================

-- 4.1 CATEGORIA INVALIDA

DO $$
BEGIN
    INSERT INTO ocorrencia (
        id, descricao, status, categoria,
        latitude, longitude, usuario_id
    ) VALUES (
        'aaaaaaaa-0000-0000-0000-000000000001',
        'Teste categoria invalida',
        'ENVIADA',
        'CATEGORIA_INVALIDA',
        -19.9,
        -43.9,
        '44444444-4444-4444-4444-444444444444'
    );

    RAISE EXCEPTION
        'FALHOU: Categoria invalida foi aceita';

EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE
            'PASSOU: Categoria invalida rejeitada (23514)';
END $$;

-- 4.2 STATUS INVALIDO

DO $$
BEGIN
    INSERT INTO ocorrencia (
        id, descricao, status, categoria,
        latitude, longitude, usuario_id
    ) VALUES (
        'aaaaaaaa-0000-0000-0000-000000000002',
        'Teste status invalido',
        'CANCELADA',
        'BURACO',
        -19.9,
        -43.9,
        '44444444-4444-4444-4444-444444444444'
    );

    RAISE EXCEPTION
        'FALHOU: Status invalido foi aceito';

EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE
            'PASSOU: Status invalido rejeitado (23514)';
END $$;

-- 4.3 PRIORIDADE INVALIDA

DO $$
BEGIN
    INSERT INTO ocorrencia (
        id, descricao, status, categoria,
        latitude, longitude, prioridade, usuario_id
    ) VALUES (
        'aaaaaaaa-0000-0000-0000-000000000003',
        'Teste prioridade invalida',
        'ENVIADA',
        'BURACO',
        -19.9,
        -43.9,
        'URGENTE',
        '44444444-4444-4444-4444-444444444444'
    );

    RAISE EXCEPTION
        'FALHOU: Prioridade invalida foi aceita';

EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE
            'PASSOU: Prioridade invalida rejeitada (23514)';
END $$;

-- 4.4 DESCRICAO NULA

DO $$
BEGIN
    INSERT INTO ocorrencia (
        id, descricao, status, categoria,
        latitude, longitude, usuario_id
    ) VALUES (
        'aaaaaaaa-0000-0000-0000-000000000004',
        NULL,
        'ENVIADA',
        'BURACO',
        -19.9,
        -43.9,
        '44444444-4444-4444-4444-444444444444'
    );

    RAISE EXCEPTION
        'FALHOU: Descricao nula foi aceita';

EXCEPTION
    WHEN not_null_violation THEN
        RAISE NOTICE
            'PASSOU: Descricao nula rejeitada (23502)';
END $$;

-- 4.5 LATITUDE NULA

DO $$
BEGIN
    INSERT INTO ocorrencia (
        id, descricao, status, categoria,
        latitude, longitude, usuario_id
    ) VALUES (
        'aaaaaaaa-0000-0000-0000-000000000005',
        'Teste latitude nula',
        'ENVIADA',
        'BURACO',
        NULL,
        -43.9,
        '44444444-4444-4444-4444-444444444444'
    );

    RAISE EXCEPTION
        'FALHOU: Latitude nula foi aceita';

EXCEPTION
    WHEN not_null_violation THEN
        RAISE NOTICE
            'PASSOU: Latitude nula rejeitada (23502)';
END $$;

-- 4.6 LONGITUDE NULA

DO $$
BEGIN
    INSERT INTO ocorrencia (
        id, descricao, status, categoria,
        latitude, longitude, usuario_id
    ) VALUES (
        'aaaaaaaa-0000-0000-0000-000000000006',
        'Teste longitude nula',
        'ENVIADA',
        'BURACO',
        -19.9,
        NULL,
        '44444444-4444-4444-4444-444444444444'
    );

    RAISE EXCEPTION
        'FALHOU: Longitude nula foi aceita';

EXCEPTION
    WHEN not_null_violation THEN
        RAISE NOTICE
            'PASSOU: Longitude nula rejeitada (23502)';
END $$;

-- 4.7 USUARIO INEXISTENTE

DO $$
BEGIN
    INSERT INTO ocorrencia (
        id, descricao, status, categoria,
        latitude, longitude, usuario_id
    ) VALUES (
        'aaaaaaaa-0000-0000-0000-000000000007',
        'Teste FK inexistente',
        'ENVIADA',
        'BURACO',
        -19.9,
        -43.9,
        '99999999-9999-9999-9999-999999999999'
    );

    RAISE EXCEPTION
        'FALHOU: FK inexistente foi aceita';

EXCEPTION
    WHEN foreign_key_violation THEN
        RAISE NOTICE
            'PASSOU: Usuario inexistente rejeitado (23503)';
END $$;

-- ============================================================
-- 5. CONSULTAS PARA EVIDENCIAS
-- ============================================================

-- Quantidade por categoria
SELECT categoria, COUNT(*) AS total
FROM ocorrencia
WHERE id::text LIKE 'bbbbbbbb%'
GROUP BY categoria
ORDER BY categoria;

-- Distribuicao de status e prioridade
SELECT status, prioridade, COUNT(*) AS total
FROM ocorrencia
WHERE id::text LIKE 'bbbbbbbb%'
GROUP BY status, prioridade
ORDER BY status, prioridade;

-- Relacionamento entre usuario e ocorrencia
SELECT
    u.nome,
    u.papel,
    COUNT(o.id) AS total_ocorrencias
FROM usuario u
LEFT JOIN ocorrencia o ON o.usuario_id = u.id
WHERE u.id IN (
    '44444444-4444-4444-4444-444444444444',
    '55555555-5555-5555-5555-555555555555',
    '66666666-6666-6666-6666-666666666666',
    '77777777-7777-7777-7777-777777777777'
)
GROUP BY u.id, u.nome, u.papel
ORDER BY total_ocorrencias DESC;

-- Verificacao do indice criado na V3
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'ocorrencia'
  AND indexname = 'idx_ocorrencia_usuario_id';

-- ============================================================
-- 6. FINALIZACAO
-- ============================================================

DO $$
BEGIN
    RAISE NOTICE
        'TESTES CONCLUIDOS: Nenhuma validacao falhou';
END $$;

-- Reverte todos os inserts e updates de demonstracao.
-- As consultas e mensagens anteriores permanecem no log.
ROLLBACK;
