package app.zelar.ocorrencia.dto;

import app.zelar.ocorrencia.entity.PrioridadeOcorrencia;
import app.zelar.ocorrencia.entity.StatusOcorrencia;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ListarOcorrenciasRequest(
        @Min(value = 0, message = "A página deve ser maior ou igual a zero")
        Integer pagina,

        @Min(value = 1, message = "O tamanho da página deve ser pelo menos 1")
        @Max(value = 100, message = "O tamanho da página deve ser no máximo 100")
        Integer tamanho,

        StatusOcorrencia status,
        PrioridadeOcorrencia prioridade
) {

    public ListarOcorrenciasRequest {
        pagina = pagina == null ? 0 : pagina;
        tamanho = tamanho == null ? 20 : tamanho;
    }
}
