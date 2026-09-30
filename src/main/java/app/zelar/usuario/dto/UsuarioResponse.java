package app.zelar.usuario.dto;

import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        PerfilUsuario perfil,
        LocalDateTime criadoEm
) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil(),
                usuario.getCriadoEm()
        );
    }
}