package app.zelar.usuario.controller;

import app.zelar.usuario.dto.CriarUsuarioRequest;
import app.zelar.usuario.dto.UsuarioResponse;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/criar")
    public ResponseEntity<UsuarioResponse> criar(
            @Valid @RequestBody CriarUsuarioRequest request
    ) {

        Usuario usuario = usuarioService.criar(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UsuarioResponse.from(usuario));
    }
}