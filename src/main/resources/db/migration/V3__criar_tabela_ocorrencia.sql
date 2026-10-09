-- Criação da tabela de ocorrências alinhada ao modelo de dados do MVP
CREATE TABLE ocorrencia (
    id UUID PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    descricao TEXT NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA')),
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Chaves estrangeiras (Foreign Keys)
    categoria_id UUID NOT NULL,
    usuario_id UUID NOT NULL, 
    
    -- Definição dos relacionamentos
    CONSTRAINT fk_ocorrencia_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id),
    CONSTRAINT fk_ocorrencia_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);

-- Criação de índices para acelerar as pesquisas
CREATE INDEX idx_ocorrencia_categoria_id ON ocorrencia (categoria_id);
CREATE INDEX idx_ocorrencia_usuario_id ON ocorrencia (usuario_id);