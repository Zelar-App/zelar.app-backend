-- Criação da tabela de anexos para ocorrências (RNF-S04)
CREATE TABLE anexo (
    id UUID PRIMARY KEY,
    ocorrencia_id UUID NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    tipo_mime VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    caminho_armazenamento VARCHAR(500) NOT NULL,

    -- Chave Estrangeira (FK) vinculando ao id da ocorrencia
    CONSTRAINT fk_anexo_ocorrencia FOREIGN KEY (ocorrencia_id) REFERENCES ocorrencia (id) ON DELETE CASCADE
);

-- Índice para agilizar a busca de anexos vinculados a uma ocorrência
CREATE INDEX idx_anexo_ocorrencia_id ON anexo(ocorrencia_id);
