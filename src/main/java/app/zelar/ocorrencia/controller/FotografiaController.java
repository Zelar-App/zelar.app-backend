package app.zelar.ocorrencia.controller;

import app.zelar.ocorrencia.dto.FotografiaResponse;
import app.zelar.ocorrencia.service.FotografiaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ocorrencias/{id}/fotografia")
public class FotografiaController {

    private final FotografiaService fotografias;

    public FotografiaController(FotografiaService fotografias) {
        this.fotografias = fotografias;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotografiaResponse> enviar(@PathVariable UUID id, @RequestPart("foto") MultipartFile foto,
                                                    @AuthenticationPrincipal Jwt jwt) {
        var resposta = fotografias.enviar(UUID.fromString(jwt.getSubject()), id, foto);
        return ResponseEntity.created(URI.create("/api/v1/ocorrencias/" + id + "/fotografia")).body(resposta);
    }

    @GetMapping
    public ResponseEntity<byte[]> consultar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        var fotografia = fotografias.consultar(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(fotografia.tipoConteudo()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .header("X-Content-Type-Options", "nosniff").body(fotografia.conteudo());
    }
}
