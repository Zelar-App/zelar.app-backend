-- DB-09: metadados de uma fotografia por ocorrencia, conforme o contrato da BE-13.
-- O arquivo permanece em bucket privado. A chave e reservada antes do upload.
CREATE TABLE anexo (
    id UUID PRIMARY KEY,
    ocorrencia_id UUID NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    chave_objeto VARCHAR(255) NOT NULL,
    tipo_conteudo VARCHAR(50) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    largura INTEGER NOT NULL,
    altura INTEGER NOT NULL,
    estado_upload VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_anexo_ocorrencia FOREIGN KEY (ocorrencia_id)
        REFERENCES ocorrencia (id) ON DELETE RESTRICT,
    CONSTRAINT uk_anexo_ocorrencia UNIQUE (ocorrencia_id),
    CONSTRAINT uk_anexo_objeto UNIQUE (bucket, chave_objeto),
    CONSTRAINT ck_anexo_bucket CHECK (REGEXP_LIKE(bucket, '^[a-z0-9][a-z0-9-]{0,99}$')),
    CONSTRAINT ck_anexo_chave CHECK (REGEXP_LIKE(chave_objeto,
        '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}[.](jpg|png)$')),
    CONSTRAINT ck_anexo_tipo_extensao CHECK (
        (tipo_conteudo = 'image/jpeg' AND chave_objeto LIKE '%.jpg')
        OR (tipo_conteudo = 'image/png' AND chave_objeto LIKE '%.png')
    ),
    CONSTRAINT ck_anexo_tamanho CHECK (tamanho_bytes BETWEEN 1 AND 5242880),
    CONSTRAINT ck_anexo_dimensoes CHECK (
        largura BETWEEN 1 AND 8192 AND altura BETWEEN 1 AND 8192
        AND CAST(largura AS BIGINT) * altura <= 16000000
    ),
    CONSTRAINT ck_anexo_estado CHECK (estado_upload IN ('PENDENTE', 'DISPONIVEL', 'LIMPEZA_PENDENTE'))
);

-- As constraints UNIQUE ja criam indices para o vinculo e para a identidade do objeto.
-- Este indice apoia a futura reconciliacao de uploads/limpezas interrompidos na BE-13.
CREATE INDEX idx_anexo_estado_atualizacao ON anexo (estado_upload, data_atualizacao);
