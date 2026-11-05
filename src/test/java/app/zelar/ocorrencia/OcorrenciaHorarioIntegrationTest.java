package app.zelar.ocorrencia;

import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import app.zelar.ocorrencia.entity.Localizacao;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.entity.PrioridadeOcorrencia;
import app.zelar.ocorrencia.repository.OcorrenciaRepository;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelar-horario;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OcorrenciaHorarioIntegrationTest {

    private static final Instant INSTANTE = Instant.parse("2026-11-06T01:30:00Z");
    private static final LocalDateTime CRIACAO = LocalDateTime.of(2026, 11, 5, 21, 0);
    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private OcorrenciaRepository ocorrencias;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private Clock clock;
    private Usuario autor;
    private Usuario administrador;

    @DynamicPropertySource
    static void configurarPostgresOpcional(DynamicPropertyRegistry registry) {
        String url = System.getenv("UTC_TEST_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv("UTC_TEST_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv("UTC_TEST_PASSWORD"));
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
    }

    @BeforeEach
    void configurar() {
        when(clock.instant()).thenReturn(INSTANTE);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
        autor = usuarios.save(new Usuario("Autor", "autor-horario@example.com", "hash", PerfilUsuario.CIDADAO, null));
        administrador = usuarios.save(new Usuario("Admin", "admin-horario@example.com", "hash", PerfilUsuario.ADMINISTRADOR, null));
    }

    @Test
    void deveCriarEmUtcMesmoComClockDeOutroFusoEPermitirConversaoLocal() throws Exception {
        var resultado = mvc.perform(post("/api/v1/ocorrencias").with(jwt().jwt(j -> j.subject(autor.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"BURACO\",\"descricao\":\"Buraco na via\",\"latitude\":0,\"longitude\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataCriacao").value(INSTANTE.toString()))
                .andExpect(jsonPath("$.dataAtualizacao").isEmpty())
                .andReturn();

        var resposta = objectMapper.readTree(resultado.getResponse().getContentAsString());
        UUID id = UUID.fromString(resposta.get("id").asString());
        entityManager.flush();
        entityManager.clear();
        assertEquals(LocalDateTime.ofInstant(INSTANTE, ZoneOffset.UTC), ocorrencias.findById(id).orElseThrow().getDataCriacao());
        Instant recebido = Instant.parse(resposta.get("dataCriacao").asString());
        assertEquals(LocalDateTime.of(2026, 11, 5, 22, 30),
                LocalDateTime.ofInstant(recebido, ZoneId.of("America/Sao_Paulo")));
    }

    @Test
    void deveConsultarTimestampUtcExistenteSemSomarOuSubtrairHoras() throws Exception {
        Ocorrencia registro = registrar();
        mvc.perform(get("/api/v1/ocorrencias/{id}", registro.getId())
                        .with(jwt().jwt(j -> j.subject(autor.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataCriacao").value("2026-11-05T21:00:00Z"))
                .andExpect(jsonPath("$.dataAtualizacao").isEmpty());
        assertEquals(CRIACAO, registro.getDataCriacao());
    }

    @Test
    void deveManterMesmoInstanteNaListagem() throws Exception {
        registrar();
        mvc.perform(get("/api/v1/ocorrencias").with(jwt().jwt(j -> j.subject(autor.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].dataCriacao").value("2026-11-05T21:00:00Z"));
    }

    @Test
    void deveAtualizarStatusEmUtcPreservandoCriacao() throws Exception {
        Ocorrencia registro = registrar();
        mvc.perform(patch("/api/v1/ocorrencias/{id}/status", registro.getId())
                        .with(jwt().jwt(j -> j.subject(administrador.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_ANALISE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataCriacao").value("2026-11-05T21:00:00Z"))
                .andExpect(jsonPath("$.dataAtualizacao").value(INSTANTE.toString()));
        entityManager.flush();
        entityManager.clear();
        assertEquals(LocalDateTime.ofInstant(INSTANTE, ZoneOffset.UTC),
                ocorrencias.findById(registro.getId()).orElseThrow().getDataAtualizacao());
    }

    @Test
    void deveAtualizarPrioridadeEmUtcSemAlterarCriacao() throws Exception {
        Ocorrencia registro = registrar();
        mvc.perform(patch("/api/v1/ocorrencias/{id}/prioridade", registro.getId())
                        .with(jwt().jwt(j -> j.subject(administrador.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prioridade\":\"ALTA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataCriacao").value("2026-11-05T21:00:00Z"))
                .andExpect(jsonPath("$.dataAtualizacao").value(INSTANTE.toString()));
        assertEquals(LocalDateTime.ofInstant(INSTANTE, ZoneOffset.UTC), registro.getDataAtualizacao());
    }

    @Test
    void devePreservarInstanteAoRepetirPrioridade() throws Exception {
        Ocorrencia registro = registrar();
        ReflectionTestUtils.setField(registro, "prioridade", PrioridadeOcorrencia.ALTA);
        ReflectionTestUtils.setField(registro, "dataAtualizacao", CRIACAO.plusMinutes(10));
        mvc.perform(patch("/api/v1/ocorrencias/{id}/prioridade", registro.getId())
                        .with(jwt().jwt(j -> j.subject(administrador.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prioridade\":\"ALTA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataAtualizacao").value("2026-11-05T21:10:00Z"));
        assertEquals(CRIACAO.plusMinutes(10), registro.getDataAtualizacao());
    }

    @Test
    void devePreservarDatasQuandoTransicaoForRecusada() throws Exception {
        Ocorrencia registro = registrar();
        mvc.perform(patch("/api/v1/ocorrencias/{id}/status", registro.getId())
                        .with(jwt().jwt(j -> j.subject(administrador.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVIDA\"}"))
                .andExpect(status().isConflict());
        assertEquals(CRIACAO, registro.getDataCriacao());
        assertNull(registro.getDataAtualizacao());
    }

    private Ocorrencia registrar() {
        Ocorrencia registro = new Ocorrencia(autor, CategoriaOcorrencia.BURACO, "Buraco na via", new Localizacao(0.0, 0.0, null));
        ReflectionTestUtils.setField(registro, "dataCriacao", CRIACAO);
        return ocorrencias.saveAndFlush(registro);
    }
}
