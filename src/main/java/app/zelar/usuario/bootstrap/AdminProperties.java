package app.zelar.usuario.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais opcionais da conta administrativa, recebidas por configuração externa
 * (ADMIN_EMAIL, ADMIN_PASSWORD e ADMIN_NOME). Sem valores, nada é provisionado.
 */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(String email, String password, String nome) {
}
