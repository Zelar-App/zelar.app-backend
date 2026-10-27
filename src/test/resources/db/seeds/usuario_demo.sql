-- Dados exclusivos de demonstracao/testes (DB-06)
-- Nao executar em producao.

INSERT INTO usuario (
    id,
    nome,
    email,
    senha_hash,
    telefone,
    papel,
    data_cadastro
)
SELECT
    'b1ffcd00-0d1c-5fa9-cc7e-7cc0ce491b22',
    'Cidadao Demonstracao',
    'cidadao.demo@zelar.app',
    '${DEMO_PASSWORD_HASH}',
    '31988888888',
    'CIDADAO',
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM usuario
    WHERE email = 'cidadao.demo@zelar.app'
);
