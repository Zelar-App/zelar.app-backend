-- Carga Inicial de Dados de Demonstração/Testes para o MVP (DB-06)
-- Nota de Governança (INF-07): A conta de ADMINISTRADOR foi omitida deste script global.
-- O provisionamento de contas administrativas ocorre via fluxo controlado em variaveis de ambiente/infraestrutura.

-- 1. Usuario Cidadao de Demonstracao para testes locais e integracao
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
    '$2a$10$7EqJtq986P2B78P8O30Sce.4aZ5yLwZc0k9s2aZ5yLwZc0k9s2aZ5',
    '31988888888',
    'CIDADAO',
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM usuario WHERE email = 'cidadao.demo@zelar.app'
);
