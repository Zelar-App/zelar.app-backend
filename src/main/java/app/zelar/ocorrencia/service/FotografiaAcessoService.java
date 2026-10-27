package app.zelar.ocorrencia.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FotografiaAcessoService {

    private final OcorrenciaService ocorrenciaService;

    public FotografiaAcessoService(OcorrenciaService ocorrenciaService) {
        this.ocorrenciaService = ocorrenciaService;
    }

    public void autorizarEnvio(UUID usuarioId, UUID ocorrenciaId) {
        var ocorrencia = ocorrenciaService.consultar(usuarioId, ocorrenciaId);
        if (!usuarioId.equals(ocorrencia.usuarioId())) {
            throw new AccessDeniedException("Somente o autor pode enviar a fotografia da ocorrência.");
        }
    }

    public void autorizarConsulta(UUID usuarioId, UUID ocorrenciaId) {
        ocorrenciaService.consultar(usuarioId, ocorrenciaId);
    }
}
