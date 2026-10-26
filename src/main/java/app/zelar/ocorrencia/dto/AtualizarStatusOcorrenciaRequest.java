package app.zelar.ocorrencia.dto;

import app.zelar.ocorrencia.entity.StatusOcorrencia;
import jakarta.validation.constraints.NotNull;

public record AtualizarStatusOcorrenciaRequest(
        @NotNull(message = "O status é obrigatório")
        StatusOcorrencia status
) {
}
