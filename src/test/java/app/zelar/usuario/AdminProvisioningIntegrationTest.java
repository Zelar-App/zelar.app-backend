package app.zelar.usuario;

import app.zelar.usuario.bootstrap.AdminProvisioner;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelar-admin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "admin.email=admin@email.com",
        "admin.password=SenhaForte123",
        "admin.nome=Admin Demo"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(AdminProvisioningIntegrationTest.RotaAdministrativaDeTeste.class)
class AdminProvisioningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private AdminProvisioner adminProvisioner;

    @Test
    void deveProvisionarAdminNoStartupComSenhaHasheada() {
        Usuario admin = usuarioRepository.findByEmail("admin@email.com").orElseThrow();

        assertEquals("Admin Demo", admin.getNome());
        assertEquals(PerfilUsuario.ADMINISTRADOR, admin.getPerfil());
        assertNotEquals("SenhaForte123", admin.getSenha());
        assertTrue(passwordEncoder.matches("SenhaForte123", admin.getSenha()));
        assertEquals(1, usuarioRepository.findAll().stream()
                .filter(u -> u.getEmail().equals("admin@email.com")).count());
    }

    @Test
    void deveAutenticarAdminProvisionadoEAutorizarRotaAdministrativa() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "admin@email.com", "senha", "SenhaForte123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("ADMINISTRADOR"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = response.get("accessToken").asString();
        assertEquals("ADMINISTRADOR", jwtDecoder.decode(token).getClaimAsString("perfil"));

        mockMvc.perform(get("/teste/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("permitido"));
    }

    @Test
    void deveSerIdempotenteAoExecutarNovamente() throws Exception {
        adminProvisioner.run(new DefaultApplicationArguments());

        assertEquals(1, usuarioRepository.findAll().stream()
                .filter(u -> u.getEmail().equals("admin@email.com")).count());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    static class RotaAdministrativaDeTeste {

        @GetMapping("/teste/admin")
        @PreAuthorize("hasRole('ADMINISTRADOR')")
        public String consultar() {
            return "permitido";
        }
    }
}
