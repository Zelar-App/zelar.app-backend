-- Criação da tabela de ocorrências alinhada ao DER, Diagrama de Classes e BE-06
CREATE TABLE ocorrencia (
    id UUID PRIMARY KEY,
    descricao TEXT NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('ENVIADA', 'EM_ANALISE', 'RESOLVIDA')),
    
    -- Categoria definida como Enum/VARCHAR diretamente na tabela (conforme o DER)
    categoria VARCHAR(50) NOT NULL,
    
    -- Geolocalização
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    endereco_aproximado VARCHAR(255),
    
    -- Prioridade e Datas
    prioridade VARCHAR(20) CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA')),
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP,
    
    -- Relacionamento com Usuário
    usuario_id UUID NOT NULL,
    
    -- Chave Estrangeira
    CONSTRAINT fk_ocorrencia_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);
