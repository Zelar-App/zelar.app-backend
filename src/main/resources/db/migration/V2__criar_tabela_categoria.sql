-- Criação da tabela de categorias alinhada ao modelo de dados do MVP
CREATE TABLE categoria (
    id UUID PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE,
    descricao VARCHAR(255)
);