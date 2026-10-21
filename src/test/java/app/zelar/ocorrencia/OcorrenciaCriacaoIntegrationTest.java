package app.zelar.ocorrencia;

import app.zelar.autenticacao.service.TokenService;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.entity.StatusOcorrencia;
import app.zelar.ocorrencia.repository.OcorrenciaRepository;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelar-criacao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OcorrenciaCriacaoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private EntityManager entityManager;

    @Test
    void devePersistirOcorrenciaDoUsuarioDoTokenSemExporDadosPessoais() throws Exception {
        Usuario usuario = criarUsuario("joao@email.com");
        Map<String, Object> dados = dadosValidos();
        dados.put("enderecoAproximado", "Praça central");

        var result = mockMvc.perform(post("/api/v1/ocorrencias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.gerar(usuario))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dados)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(usuario.getId().toString()))
                .andExpect(jsonPath("$.categoria").value("BURACO"))
                .andExpect(jsonPath("$.descricao").value("Buraco na via"))
                .andExpect(jsonPath("$.latitude").value(-12.97))
                .andExpect(jsonPath("$.longitude").value(-38.50))
                .andExpect(jsonPath("$.enderecoAproximado").value("Praça central"))
                .andExpect(jsonPath("$.status").value("ENVIADA"))
                .andExpect(jsonPath("$.prioridade").isEmpty())
                .andExpect(jsonPath("$.dataCriacao").isNotEmpty())
                .andExpect(jsonPath("$.dataAtualizacao").isEmpty())
                .andReturn();

        var response = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(response.get("id").asString());
        entityManager.flush();
        entityManager.clear();
        Ocorrencia salva = ocorrenciaRepository.findById(id).orElseThrow();
        assertEquals(usuario.getId(), salva.getUsuario().getId());
        assertEquals(StatusOcorrencia.ENVIADA, salva.getStatus());
        assertEquals("Praça central", salva.getLocalizacao().getEnderecoAproximado());
        assertEquals(1, ocorrenciaRepository.count());
        for (String campo : new String[]{"usuario", "nome", "email", "telefone", "senha"}) {
            assertFalse(response.has(campo));
        }
    }

    @Test
    void deveImpedirQueCorpoDefinaAutorStatusOuPrioridade() throws Exception {
        Usuario autenticado = criarUsuario("joao@email.com");
        Usuario outroUsuario = criarUsuario("outro@email.com");
        Map<String, Object> dados = dadosValidos();
        dados.put("usuarioId", outroUsuario.getId().toString());
        dados.put("status", "RESOLVIDA");
        dados.put("prioridade", "ALTA");

        var result = mockMvc.perform(post("/api/v1/ocorrencias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.gerar(autenticado))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dados)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(autenticado.getId().toString()))
                .andExpect(jsonPath("$.status").value("ENVIADA"))
                .andExpect(jsonPath("$.prioridade").isEmpty())
                .andExpect(jsonPath("$.enderecoAproximado").isEmpty())
                .andReturn();

        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
        entityManager.flush();
        entityManager.clear();
        Ocorrencia salva = ocorrenciaRepository.findById(id).orElseThrow();
        assertEquals(autenticado.getId(), salva.getUsuario().getId());
        assertNull(salva.getPrioridade());
        assertEquals(StatusOcorrencia.ENVIADA, salva.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "{\"categoria\":\"DESCONHECIDA\"}",
            "{\"categoria\":\"BURACO\",\"descricao\":\" \",\"latitude\":0,\"longitude\":0}",
            "{\"categoria\":\"BURACO\",\"descricao\":\"Buraco\",\"latitude\":91,\"longitude\":0}",
            "{\"categoria\":\"BURACO\",\"descricao\":\"Buraco\",\"latitude\":0,\"longitude\":181}"})
    void deveRecusarDadosInvalidosSemPersistir(String body) throws Exception {
        Usuario usuario = criarUsuario("joao@email.com");

        mockMvc.perform(post("/api/v1/ocorrencias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.gerar(usuario))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.caminho").value("/api/v1/ocorrencias"));

        assertEquals(0, ocorrenciaRepository.count());
    }

    @Test
    void deveExigirAutenticacaoParaCriarOcorrencia() throws Exception {
        mockMvc.perform(post("/api/v1/ocorrencias").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dadosValidos())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        assertEquals(0, ocorrenciaRepository.count());
    }

    @Test
    void deveRecusarTokenDeUsuarioRemovidoSemPersistir() throws Exception {
        Usuario usuario = criarUsuario("joao@email.com");
        String token = tokenService.gerar(usuario);
        usuarioRepository.delete(usuario);
        usuarioRepository.flush();

        mockMvc.perform(post("/api/v1/ocorrencias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dadosValidos())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        assertEquals(0, ocorrenciaRepository.count());
    }

    private Usuario criarUsuario(String email) {
        return usuarioRepository.saveAndFlush(new Usuario("João", email, "hash",
                PerfilUsuario.CIDADAO, "71999999999"));
    }

    private Map<String, Object> dadosValidos() {
        return new LinkedHashMap<>(Map.of("categoria", "BURACO", "descricao", "Buraco na via",
                "latitude", -12.97, "longitude", -38.50));
    }
}
