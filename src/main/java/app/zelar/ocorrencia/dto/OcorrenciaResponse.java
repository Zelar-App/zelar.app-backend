package app.zelar.ocorrencia.dto;

import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.entity.PrioridadeOcorrencia;
import app.zelar.ocorrencia.entity.StatusOcorrencia;

import java.time.LocalDateTime;
import java.util.UUID;

public record OcorrenciaResponse(
        UUID id,
        UUID usuarioId,
        CategoriaOcorrencia categoria,
        String descricao,
        Double latitude,
        Double longitude,
        String enderecoAproximado,
        StatusOcorrencia status,
        PrioridadeOcorrencia prioridade,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao,
        boolean possuiFotografia
) {

    public OcorrenciaResponse(UUID id, UUID usuarioId, CategoriaOcorrencia categoria, String descricao,
                              Double latitude, Double longitude, String enderecoAproximado,
                              StatusOcorrencia status, PrioridadeOcorrencia prioridade,
                              LocalDateTime dataCriacao, LocalDateTime dataAtualizacao) {
        this(id, usuarioId, categoria, descricao, latitude, longitude, enderecoAproximado,
                status, prioridade, dataCriacao, dataAtualizacao, false);
    }

    public OcorrenciaResponse comFotografia(boolean possuiFotografia) {
        return new OcorrenciaResponse(id, usuarioId, categoria, descricao, latitude, longitude,
                enderecoAproximado, status, prioridade, dataCriacao, dataAtualizacao, possuiFotografia);
    }

    public static OcorrenciaResponse from(Ocorrencia ocorrencia) {
        return new OcorrenciaResponse(
                ocorrencia.getId(),
                ocorrencia.getUsuario().getId(),
                ocorrencia.getCategoria(),
                ocorrencia.getDescricao(),
                ocorrencia.getLocalizacao().getLatitude(),
                ocorrencia.getLocalizacao().getLongitude(),
                ocorrencia.getLocalizacao().getEnderecoAproximado(),
                ocorrencia.getStatus(),
                ocorrencia.getPrioridade(),
                ocorrencia.getDataCriacao(),
                ocorrencia.getDataAtualizacao()
        );
    }
}
