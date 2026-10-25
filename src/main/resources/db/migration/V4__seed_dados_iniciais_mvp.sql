-- Carga Inicial de Dados para o MVP (DB-06)

-- 1. Usuário Administrador Padrão
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
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'Administrador Zelar',
    'admin@zelar.app',
    '$2a$10$7EqJtq986P2B78P8O30Sce.4aZ5yLwZc0k9s2aZ5yLwZc0k9s2aZ5',
    '31999999999',
    'ADMINISTRADOR',
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM usuario WHERE email = 'admin@zelar.app'
);

-- 2. Cidadão Padrão para testes
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
    'b1ffcd00-0d1c-5fg9-cc7e-7cc0ce491b22',
    'Cidadão Teste',
    'cidadao@zelar.app',
    '$2a$10$7EqJtq986P2B78P8O30Sce.4aZ5yLwZc0k9s2aZ5yLwZc0k9s2aZ5',
    '31988888888',
    'CIDADAO',
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM usuario WHERE email = 'cidadao@zelar.app'
);
