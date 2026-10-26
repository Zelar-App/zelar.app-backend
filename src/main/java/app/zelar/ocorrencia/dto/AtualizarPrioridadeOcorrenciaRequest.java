package app.zelar.ocorrencia.dto;

import app.zelar.ocorrencia.entity.PrioridadeOcorrencia;
import jakarta.validation.constraints.NotNull;

public record AtualizarPrioridadeOcorrenciaRequest(
        @NotNull(message = "A prioridade é obrigatória") PrioridadeOcorrencia prioridade
) {
}
