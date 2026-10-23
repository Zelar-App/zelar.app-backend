-- Criação da tabela de ocorrências alinhada ao DER e à BE-06
CREATE TABLE ocorrencia (
    id UUID PRIMARY KEY,
    descricao TEXT NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('ENVIADA', 'EM_ANALISE', 'RESOLVIDA')),
    
    -- Categoria mapeada como Enum com validação CHECK (alinhada ao enum CategoriaOcorrencia)
    categoria VARCHAR(50) NOT NULL CHECK (categoria IN (
        'BURACO',
        'ILUMINACAO',
        'LIXO',
        'ALAGAMENTO',
        'CALCADA',
        'SINALIZACAO',
        'AMBIENTAL'
    )),
    
    -- Localização
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    endereco_aproximado VARCHAR(255),
    
    -- Prioridade e Datas
    prioridade VARCHAR(20) CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA')),
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP,
    
    -- Chave estrangeira para Usuário
    usuario_id UUID NOT NULL,
    
    -- Definição da Foreign Key
    CONSTRAINT fk_ocorrencia_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);
