package app.zelar.ocorrencia.dto;

public record OcorrenciaIndicadoresResponse(
        long enviadas,
        long emAnalise,
        long resolvidas
) {
}
