package app.zelar.ocorrencia.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record OcorrenciaPaginaResponse(
        List<OcorrenciaResponse> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas
) {

    public static OcorrenciaPaginaResponse from(Page<OcorrenciaResponse> pagina) {
        return new OcorrenciaPaginaResponse(
                pagina.getContent(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages()
        );
    }
}
