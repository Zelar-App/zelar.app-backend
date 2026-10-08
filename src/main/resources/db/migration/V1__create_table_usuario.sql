-- Criação da tabela de usuários alinhada ao modelo de dados do MVP
CREATE TABLE usuario (
    id UUID PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    telefone VARCHAR(20),
    papel VARCHAR(20) NOT NULL CHECK (papel IN ('CIDADAO', 'ADMINISTRADOR')),
    data_cadastro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
