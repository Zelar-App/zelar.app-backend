package app.zelar.autenticacao.dto;

import app.zelar.usuario.dto.UsuarioResponse;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UsuarioResponse usuario
) {
}
