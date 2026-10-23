package app.zelar.autenticacao.service;

import app.zelar.autenticacao.config.JwtConfig;
import app.zelar.autenticacao.config.JwtProperties;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private final JwtConfig config = new JwtConfig();
    private final JwtProperties properties = new JwtProperties(SECRET, "zelar-test", Duration.ofHours(1));
    private JwtEncoder encoder;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        SecretKey key = config.jwtSecretKey(properties);
        encoder = config.jwtEncoder(key);
        decoder = config.jwtDecoder(key, properties);
    }

    @Test
    void deveEmitirTokenAssinadoComIdPerfilEmissaoEExpiracaoSemDadosPessoais() {
        UUID id = UUID.randomUUID();
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(id);
        when(usuario.getPerfil()).thenReturn(PerfilUsuario.ADMINISTRADOR);
        Instant agora = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        TokenService service = new TokenService(encoder, properties, Clock.fixed(agora, ZoneOffset.UTC));

        Jwt jwt = decoder.decode(service.gerar(usuario));

        assertEquals("HS256", jwt.getHeaders().get("alg"));
        assertEquals(id.toString(), jwt.getSubject());
        assertEquals("ADMINISTRADOR", jwt.getClaimAsString("perfil"));
        assertEquals("zelar-test", jwt.getClaimAsString("iss"));
        assertEquals(agora, jwt.getIssuedAt());
        assertEquals(agora.plusSeconds(3600), jwt.getExpiresAt());
        assertEquals(3600, service.getDuracaoEmSegundos());
        assertFalse(jwt.hasClaim("senha"));
        assertFalse(jwt.hasClaim("email"));
        assertFalse(jwt.hasClaim("telefone"));
    }

    @Test
    void deveRejeitarTokenExpirado() {
        assertThrows(JwtException.class, () -> decoder.decode(assinar(claims()
                .issuedAt(Instant.now().minusSeconds(300))
                .expiresAt(Instant.now().minusSeconds(1)).build())));
    }

    @Test
    void deveRejeitarTokenSemExpiracao() {
        assertThrows(JwtException.class, () -> decoder.decode(assinar(claims().build())));
    }

    @Test
    void deveRejeitarEmissorDiferente() {
        assertThrows(JwtException.class, () -> decoder.decode(assinar(claims()
                .issuer("outro-emissor").expiresAt(Instant.now().plusSeconds(300)).build())));
    }

    @Test
    void deveRejeitarTokenAssinadoComOutraChave() {
        String outraChave = Base64.getEncoder().encodeToString("outra-chave-aleatoria-para-teste-32-bytes".getBytes());
        JwtProperties outraConfig = new JwtProperties(outraChave, "zelar-test", Duration.ofHours(1));
        JwtEncoder outroEncoder = config.jwtEncoder(config.jwtSecretKey(outraConfig));
        String token = outroEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims().expiresAt(Instant.now().plusSeconds(300)).build())).getTokenValue();

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"sem-uuid", "1-1-1-1-1"})
    void deveRejeitarIdentificadorInvalido(String id) {
        JwtClaimsSet.Builder builder = claims().expiresAt(Instant.now().plusSeconds(300));
        if (id == null) {
            builder.claims(valores -> valores.remove("sub"));
        } else {
            builder.subject(id);
        }
        assertThrows(JwtException.class, () -> decoder.decode(assinar(builder.build())));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ADMIN", "ROLE_ADMINISTRADOR"})
    void deveRejeitarPerfilInvalido(String perfil) {
        JwtClaimsSet.Builder builder = claims().expiresAt(Instant.now().plusSeconds(300));
        if (perfil == null) {
            builder.claims(valores -> valores.remove("perfil"));
        } else {
            builder.claim("perfil", perfil);
        }
        assertThrows(JwtException.class, () -> decoder.decode(assinar(builder.build())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalido!", "Y3VydGE="})
    void deveRejeitarChaveInvalidaOuCurta(String secret) {
        JwtProperties invalida = new JwtProperties(secret, "zelar-test", Duration.ofHours(1));
        assertThrows(IllegalArgumentException.class, () -> config.jwtSecretKey(invalida));
    }

    @Test
    void deveRejeitarDuracaoNulaZeroNegativaOuInferiorAUmSegundo() {
        for (Duration duracao : new Duration[]{null, Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMillis(500)}) {
            assertThrows(IllegalArgumentException.class, () -> new JwtProperties(SECRET, "zelar-test", duracao));
        }
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().issuer("zelar-test")
                .subject(UUID.randomUUID().toString()).claim("perfil", "CIDADAO");
    }

    private String assinar(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
