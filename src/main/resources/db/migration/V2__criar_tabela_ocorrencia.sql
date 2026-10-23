-- =====================================================================
-- Migração V2: Criação da tabela de Ocorrências (DB-05)
-- Alinhado estritamente com o enum CategoriaOcorrencia do backend Java
-- =====================================================================

CREATE TABLE ocorrencia (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Descrição detalhada da ocorrência informada pelo utilizador
    descricao TEXT NOT NULL,
    
    -- Restrições de Status permitidos no sistema
    status VARCHAR(30) NOT NULL 
        CHECK (status IN ('ENVIADA', 'EM_ANALISE', 'RESOLVIDA')),
    
    -- Restrições de Categoria sincronizadas com o domínio Java (BE-06)
    categoria VARCHAR(50) NOT NULL 
        CHECK (categoria IN ('BURACO', 'ILUMINACAO', 'LIXO', 'ALAGAMENTO', 'CALCADA', 'SINALIZACAO', 'AMBIENTAL')),
    
    -- Restrições de Prioridade permitidas no sistema
    prioridade VARCHAR(20) NOT NULL 
        CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA')),
    
    -- Coordenadas geográficas para geolocalização da ocorrência
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    
    -- Endereço textual aproximado (opcional)
    endereco_aproximado VARCHAR(255),
    
    -- Datas de controlo de registo
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Chave estrangeira ligada à tabela de utilizadores (V1)
    usuario_id UUID NOT NULL,
    CONSTRAINT fk_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
);
