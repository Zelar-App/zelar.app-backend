package app.zelar.autenticacao;

import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelar-auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(AutenticacaoIntegrationTest.RotasAdministrativasDeTeste.class)
class AutenticacaoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void deveCadastrarAutenticarEConsultarUsuarioComTokenRealSemCriarSessao() throws Exception {
        String cadastro = objectMapper.writeValueAsString(Map.of(
                "nome", "João Silva", "email", "joao@email.com", "senha", "Senha123", "telefone", "71999999999"));
        mockMvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON).content(cadastro))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("CIDADAO"))
                .andExpect(jsonPath("$.senha").doesNotExist());

        Usuario usuario = usuarioRepository.findByEmail("joao@email.com").orElseThrow();
        assertNotEquals("Senha123", usuario.getSenha());
        assertTrue(passwordEncoder.matches("Senha123", usuario.getSenha()));

        String token = login("JOAO@EMAIL.COM", "Senha123");
        var result = mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.id").value(usuario.getId().toString()))
                .andExpect(jsonPath("$.email").value("joao@email.com"))
                .andExpect(jsonPath("$.perfil").value("CIDADAO"))
                .andExpect(jsonPath("$.telefone").value("71999999999"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andReturn();
        assertNull(result.getRequest().getSession(false));
        assertNull(result.getResponse().getCookie("JSESSIONID"));
    }

    @Test
    void deveRetornarMesmoErroParaSenhaIncorretaEUsuarioInexistente() throws Exception {
        criarUsuario(PerfilUsuario.CIDADAO);
        for (String email : new String[]{"joao@email.com", "ausente@email.com"}) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", email, "senha", "incorreta"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos."))
                    .andExpect(jsonPath("$.caminho").value("/api/v1/auth/login"))
                    .andExpect(jsonPath("$.accessToken").doesNotExist())
                    .andExpect(jsonPath("$.senha").doesNotExist());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "{\"email\":\"invalido\",\"senha\":\"teste\"}",
            "{\"email\":\"joao@email.com\",\"senha\":\"\"}"})
    void deveValidarCorpoDoLogin(String body) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void deveRejeitarSenhaAcimaDoLimiteSemExporConteudo() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "joao@email.com", "senha", "s".repeat(73)))))
                .andExpect(status().isBadRequest()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("s".repeat(73)));
    }

    @Test
    void deveExigirTokenParaConsultarUsuario() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.caminho").value("/api/v1/auth/me"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalido", "eyJhbGciOiJub25lIn0.e30.", "a.b.c"})
    void deveRejeitarBearerInvalido(String token) throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void deveRejeitarTokenRealExpirado() throws Exception {
        Usuario usuario = criarUsuario(PerfilUsuario.CIDADAO);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("zelar-test")
                .subject(usuario.getId().toString()).claim("perfil", "CIDADAO")
                .issuedAt(Instant.now().minusSeconds(300)).expiresAt(Instant.now().minusSeconds(1)).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveRejeitarTokenComAssinaturaAlterada() throws Exception {
        criarUsuario(PerfilUsuario.CIDADAO);
        String token = login("joao@email.com", "Senha123");
        String[] partes = token.split("\\.");
        String assinatura = partes[2];
        partes[2] = (assinatura.charAt(0) == 'A' ? "B" : "A") + assinatura.substring(1);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + String.join(".", partes)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRejeitarConsultaDeUsuarioRemovido() throws Exception {
        Usuario usuario = criarUsuario(PerfilUsuario.CIDADAO);
        String token = login("joao@email.com", "Senha123");
        usuarioRepository.delete(usuario);
        usuarioRepository.flush();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveManterOutrosMetodosDaRotaDeCadastroProtegidos() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRecusarCidadaoEmOperacaoAdministrativa() throws Exception {
        criarUsuario(PerfilUsuario.CIDADAO);
        String token = login("joao@email.com", "Senha123");

        mockMvc.perform(get("/teste/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.caminho").value("/teste/admin"));
    }

    @Test
    void devePermitirAdministradorEmOperacaoAdministrativa() throws Exception {
        criarUsuario(PerfilUsuario.ADMINISTRADOR);
        String token = login("joao@email.com", "Senha123");

        mockMvc.perform(get("/teste/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("permitido"));
    }

    private Usuario criarUsuario(PerfilUsuario perfil) {
        return usuarioRepository.saveAndFlush(new Usuario("João", "joao@email.com",
                passwordEncoder.encode("Senha123"), perfil, null));
    }

    private String login(String email, String senha) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "senha", senha))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.usuario.senha").doesNotExist())
                .andReturn();
        assertNull(result.getRequest().getSession(false));
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("accessToken").asString();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    static class RotasAdministrativasDeTeste {

        @GetMapping("/teste/admin")
        @PreAuthorize("hasRole('ADMINISTRADOR')")
        public String consultar() {
            return "permitido";
        }
    }
}
