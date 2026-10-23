-- Criação de índices para otimizar as buscas de ocorrências por usuário
CREATE INDEX idx_ocorrencia_usuario_id ON ocorrencia (usuario_id);
