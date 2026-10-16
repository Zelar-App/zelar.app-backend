package app.zelar.autenticacao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, String issuer, Duration tokenDuration) {

    public JwtProperties {
        Assert.hasText(secret, "JWT_SECRET deve ser configurado em Base64.");
        Assert.hasText(issuer, "JWT_ISSUER deve ser informado.");
        Assert.isTrue(tokenDuration != null && tokenDuration.compareTo(Duration.ofSeconds(1)) >= 0,
                "JWT_TOKEN_DURATION deve ser de pelo menos um segundo.");
    }
}
