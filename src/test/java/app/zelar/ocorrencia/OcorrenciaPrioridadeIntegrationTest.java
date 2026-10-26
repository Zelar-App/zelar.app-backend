package app.zelar.ocorrencia;

import app.zelar.autenticacao.service.TokenService;
import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import app.zelar.ocorrencia.entity.Localizacao;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.entity.PrioridadeOcorrencia;
import app.zelar.ocorrencia.entity.StatusOcorrencia;
import app.zelar.ocorrencia.repository.OcorrenciaRepository;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelar-prioridade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OcorrenciaPrioridadeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(PrioridadeOcorrencia.class)
    void deveAtribuirPrioridadePersistirERefletirNasConsultas(PrioridadeOcorrencia prioridade) throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Usuario autor = usuario("autor@email.com", PerfilUsuario.CIDADAO);
        Ocorrencia ocorrencia = ocorrencia(autor);
        entityManager.refresh(ocorrencia);
        UUID id = ocorrencia.getId();
        UUID autorId = autor.getId();
        LocalDateTime criacao = ocorrencia.getDataCriacao();
        String tokenAutor = bearer(autor);
        String tokenAdmin = bearer(admin);
        entityManager.clear();

        mockMvc.perform(patch(url(id)).header(HttpHeaders.AUTHORIZATION, tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(prioridade)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.prioridade").value(prioridade.name()))
                .andExpect(jsonPath("$.dataAtualizacao").isNotEmpty())
                .andExpect(jsonPath("$.usuario").doesNotExist())
                .andExpect(jsonPath("$.nome").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());
        entityManager.flush();
        entityManager.clear();
        Ocorrencia persistida = ocorrenciaRepository.findById(id).orElseThrow();
        assertEquals(prioridade, persistida.getPrioridade());
        assertEquals(autorId, persistida.getUsuario().getId());
        assertEquals(criacao, persistida.getDataCriacao());
        assertEquals(StatusOcorrencia.ENVIADA, persistida.getStatus());
        assertEquals(CategoriaOcorrencia.BURACO, persistida.getCategoria());
        assertEquals("Buraco na via", persistida.getDescricao());
        assertEquals(-12.97, persistida.getLocalizacao().getLatitude());
        assertEquals(-38.50, persistida.getLocalizacao().getLongitude());
        assertEquals("Praça central", persistida.getLocalizacao().getEnderecoAproximado());
        assertNotNull(persistida.getDataAtualizacao());

        mockMvc.perform(get("/api/v1/ocorrencias/{id}", id).header(HttpHeaders.AUTHORIZATION, tokenAutor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prioridade").value(prioridade.name()));
        mockMvc.perform(get("/api/v1/ocorrencias").param("prioridade", prioridade.name())
                        .header(HttpHeaders.AUTHORIZATION, tokenAutor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(id.toString()));
    }

    @ParameterizedTest
    @EnumSource(StatusOcorrencia.class)
    void devePermitirAlterarPrioridadeEmQualquerStatusSemMudarStatus(StatusOcorrencia statusAtual) throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Ocorrencia ocorrencia = ocorrencia(usuario("autor@email.com", PerfilUsuario.CIDADAO));
        ReflectionTestUtils.setField(ocorrencia, "status", statusAtual);
        ocorrenciaRepository.flush();
        for (PrioridadeOcorrencia prioridade : PrioridadeOcorrencia.values()) {
            mockMvc.perform(patch(url(ocorrencia.getId())).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(corpo(prioridade)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.prioridade").value(prioridade.name()))
                    .andExpect(jsonPath("$.status").value(statusAtual.name()));
        }
        entityManager.flush();
        entityManager.clear();
        assertEquals(statusAtual, ocorrenciaRepository.findById(ocorrencia.getId()).orElseThrow().getStatus());
    }

    @Test
    void devePreservarDataQuandoPrioridadeJaForAMesma() throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Ocorrencia ocorrencia = ocorrencia(admin);
        LocalDateTime atualizacao = LocalDateTime.of(2026, 10, 20, 12, 0);
        ReflectionTestUtils.setField(ocorrencia, "prioridade", PrioridadeOcorrencia.ALTA);
        ReflectionTestUtils.setField(ocorrencia, "dataAtualizacao", atualizacao);
        ocorrenciaRepository.flush();
        mockMvc.perform(patch(url(ocorrencia.getId())).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(PrioridadeOcorrencia.ALTA)))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertEquals(atualizacao, ocorrenciaRepository.findById(ocorrencia.getId()).orElseThrow().getDataAtualizacao());
    }

    @Test
    void deveIgnorarCamposExtrasSemAlterarDadosControlados() throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Usuario autor = usuario("autor@email.com", PerfilUsuario.CIDADAO);
        Ocorrencia ocorrencia = ocorrencia(autor);
        mockMvc.perform(patch(url(ocorrencia.getId())).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prioridade\":\"ALTA\",\"status\":\"RESOLVIDA\",\"usuarioId\":\"" + admin.getId()
                                + "\",\"descricao\":\"Alterada\",\"categoria\":\"LIXO\",\"latitude\":0,\"dataCriacao\":\"2000-01-01T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(autor.getId().toString()))
                .andExpect(jsonPath("$.descricao").value("Buraco na via"))
                .andExpect(jsonPath("$.categoria").value("BURACO"))
                .andExpect(jsonPath("$.latitude").value(-12.97))
                .andExpect(jsonPath("$.status").value("ENVIADA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "{\"prioridade\":null}", "{\"prioridade\":\"URGENTE\"}",
            "{\"prioridade\":\"\"}", "{\"prioridade\":{}}"})
    void deveRejeitarCorpoInvalidoSemAlterarPrioridade(String body) throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Ocorrencia ocorrencia = ocorrencia(admin);
        mockMvc.perform(patch(url(ocorrencia.getId())).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertNull(ocorrencia.getPrioridade());
        assertNull(ocorrencia.getDataAtualizacao());
    }

    @ParameterizedTest
    @ValueSource(strings = {"propria", "outro", "ausente"})
    void deveRecusarCidadaoMesmoSendoAutorOuOcorrenciaAusente(String caso) throws Exception {
        Usuario cidadao = usuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        Usuario outro = usuario("outro@email.com", PerfilUsuario.CIDADAO);
        UUID id = caso.equals("ausente") ? UUID.randomUUID()
                : ocorrencia(caso.equals("propria") ? cidadao : outro).getId();
        mockMvc.perform(patch(url(id)).header(HttpHeaders.AUTHORIZATION, bearer(cidadao))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(PrioridadeOcorrencia.ALTA)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deveRetornar404ParaAdministradorQuandoOcorrenciaNaoExistir() throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        mockMvc.perform(patch(url(UUID.randomUUID())).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(PrioridadeOcorrencia.ALTA)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.mensagem").value("Ocorrência não encontrada."));
    }

    @Test
    void deveRetornar400ParaUuidInvalido() throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        mockMvc.perform(patch("/api/v1/ocorrencias/invalido/prioridade").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(PrioridadeOcorrencia.ALTA)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void deveExigirAutenticacao() throws Exception {
        mockMvc.perform(patch(url(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(PrioridadeOcorrencia.ALTA)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveRecusarTokenInvalido() throws Exception {
        mockMvc.perform(patch(url(UUID.randomUUID())).header(HttpHeaders.AUTHORIZATION, "Bearer invalido")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(PrioridadeOcorrencia.ALTA)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveRecusarUsuarioRemovido() throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        String token = bearer(admin);
        usuarioRepository.delete(admin);
        usuarioRepository.flush();
        mockMvc.perform(patch(url(UUID.randomUUID())).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(PrioridadeOcorrencia.ALTA)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveRecusarAdministradorRebaixadoMesmoComTokenAntigo() throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Ocorrencia ocorrencia = ocorrencia(admin);
        String token = bearer(admin);
        ReflectionTestUtils.setField(admin, "perfil", PerfilUsuario.CIDADAO);
        usuarioRepository.flush();
        entityManager.clear();
        mockMvc.perform(patch(url(ocorrencia.getId())).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(PrioridadeOcorrencia.ALTA)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    private Usuario usuario(String email, PerfilUsuario perfil) {
        return usuarioRepository.saveAndFlush(new Usuario("Pessoa Teste", email, "hash", perfil, "71999999999"));
    }

    private Ocorrencia ocorrencia(Usuario autor) {
        return ocorrenciaRepository.saveAndFlush(new Ocorrencia(autor, CategoriaOcorrencia.BURACO,
                "Buraco na via", new Localizacao(-12.97, -38.50, "Praça central")));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(usuario);
    }

    private String url(UUID id) {
        return "/api/v1/ocorrencias/" + id + "/prioridade";
    }

    private String corpo(PrioridadeOcorrencia prioridade) {
        return "{\"prioridade\":\"" + prioridade.name() + "\"}";
    }
}
