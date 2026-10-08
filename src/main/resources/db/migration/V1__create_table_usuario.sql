-- 1. Criação do tipo ENUM para o papel do usuário
CREATE TYPE papel_usuario AS ENUM ('CIDADAO', 'ADMINISTRADOR');

-- 2. Criação da tabela de usuários
CREATE TABLE usuario (
    id UUID PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    telefone VARCHAR(20),
    papel papel_usuario NOT NULL DEFAULT 'CIDADAO',
    data_cadastro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Índice para otimizar buscas por e-mail no login
CREATE INDEX idx_usuario_email ON usuario(email);
