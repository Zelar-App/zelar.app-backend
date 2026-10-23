package app.zelar.autenticacao.service;

import app.zelar.autenticacao.config.JwtProperties;
import app.zelar.usuario.entity.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String gerar(Usuario usuario) {
        Instant emitidoEm = clock.instant();
        
JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(properties.issuer())
        .subject(usuario.getId().toString())
        .issuedAt(emitidoEm)
        .expiresAt(emitidoEm.plus(properties.tokenDuration()))
        .claim("perfil", usuario.getPerfil().name())
        .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getDuracaoEmSegundos() {
        return properties.tokenDuration().toSeconds();
    }
}
