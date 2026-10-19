package app.zelar.ocorrencia.dto;

import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarOcorrenciaRequest(
        @NotNull(message = "A categoria é obrigatória")
        CategoriaOcorrencia categoria,

        @NotBlank(message = "A descrição é obrigatória")
        String descricao,

        @NotNull(message = "A latitude é obrigatória")
        @DecimalMin(value = "-90", message = "A latitude deve estar entre -90 e 90")
        @DecimalMax(value = "90", message = "A latitude deve estar entre -90 e 90")
        Double latitude,

        @NotNull(message = "A longitude é obrigatória")
        @DecimalMin(value = "-180", message = "A longitude deve estar entre -180 e 180")
        @DecimalMax(value = "180", message = "A longitude deve estar entre -180 e 180")
        Double longitude,

        @Size(max = 255, message = "O endereço aproximado deve possuir no máximo 255 caracteres")
        String enderecoAproximado
) {
}
