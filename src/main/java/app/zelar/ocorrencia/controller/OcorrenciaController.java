package app.zelar.ocorrencia.controller;

import app.zelar.ocorrencia.dto.AtualizarStatusOcorrenciaRequest;
import app.zelar.ocorrencia.dto.CriarOcorrenciaRequest;
import app.zelar.ocorrencia.dto.OcorrenciaIndicadoresResponse;
import app.zelar.ocorrencia.dto.ListarOcorrenciasRequest;
import app.zelar.ocorrencia.dto.OcorrenciaPaginaResponse;
import app.zelar.ocorrencia.dto.OcorrenciaResponse;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.service.OcorrenciaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ocorrencias")
public class OcorrenciaController {

    private final OcorrenciaService ocorrenciaService;

    public OcorrenciaController(OcorrenciaService ocorrenciaService) {
        this.ocorrenciaService = ocorrenciaService;
    }

    @GetMapping
    public ResponseEntity<OcorrenciaPaginaResponse> listar(
            @Valid @ModelAttribute ListarOcorrenciasRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID usuarioId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ocorrenciaService.listar(usuarioId, request));
    }

    @GetMapping("/indicadores")
    public ResponseEntity<OcorrenciaIndicadoresResponse> consultarIndicadores(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID usuarioId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ocorrenciaService.consultarIndicadores(usuarioId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OcorrenciaResponse> consultar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID usuarioId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ocorrenciaService.consultar(usuarioId, id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OcorrenciaResponse> atualizarStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarStatusOcorrenciaRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID usuarioId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ocorrenciaService.atualizarStatus(usuarioId, id, request));
    }

    @PostMapping
    public ResponseEntity<OcorrenciaResponse> criar(
            @Valid @RequestBody CriarOcorrenciaRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID usuarioId = UUID.fromString(jwt.getSubject());
        Ocorrencia ocorrencia = ocorrenciaService.criar(usuarioId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(OcorrenciaResponse.from(ocorrencia));
    }
}
