package app.zelar.ocorrencia;

import app.zelar.autenticacao.service.TokenService;
import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import app.zelar.ocorrencia.entity.Localizacao;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.entity.StatusOcorrencia;
import app.zelar.ocorrencia.repository.OcorrenciaRepository;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelar-status;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OcorrenciaStatusIntegrationTest {

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
    @CsvSource({"ENVIADA,EM_ANALISE", "EM_ANALISE,RESOLVIDA"})
    void deveAtualizarStatusEPersistirSemAlterarOutrosDados(StatusOcorrencia origem,
                                                          StatusOcorrencia destino) throws Exception {
        Usuario administrador = criarUsuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        Ocorrencia ocorrencia = criarOcorrencia(cidadao, origem);
        UUID id = ocorrencia.getId();
        UUID autor = cidadao.getId();
        LocalDateTime dataCriacao = ocorrencia.getDataCriacao();

        mockMvc.perform(patch("/api/v1/ocorrencias/{id}/status", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrador))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(destino)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.usuarioId").value(autor.toString()))
                .andExpect(jsonPath("$.status").value(destino.name()))
                .andExpect(jsonPath("$.dataAtualizacao").isNotEmpty())
                .andExpect(jsonPath("$.prioridade").isEmpty())
                .andExpect(jsonPath("$.usuario").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());

        entityManager.flush();
        entityManager.clear();
        Ocorrencia salva = ocorrenciaRepository.findById(id).orElseThrow();
        assertEquals(destino, salva.getStatus());
        assertNotNull(salva.getDataAtualizacao());
        assertEquals(autor, salva.getUsuario().getId());
        assertEquals("Buraco na via", salva.getDescricao());
        assertEquals(CategoriaOcorrencia.BURACO, salva.getCategoria());
        assertEquals(-12.97, salva.getLocalizacao().getLatitude());
        assertEquals(dataCriacao, salva.getDataCriacao());

        mockMvc.perform(get("/api/v1/ocorrencias").param("status", destino.name())
                        .header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].status").value(destino.name()));
    }

    @Test
    void deveIgnorarTentativaDeAlterarAutorPrioridadeOuDescricao() throws Exception {
        Usuario administrador = criarUsuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        Ocorrencia ocorrencia = criarOcorrencia(cidadao, StatusOcorrencia.ENVIADA);
        String body = """
                {"status":"EM_ANALISE","usuarioId":"%s","prioridade":"ALTA",
                 "descricao":"Alterada","categoria":"LIXO","latitude":0,"longitude":0}
                """.formatted(administrador.getId());

        mockMvc.perform(patch("/api/v1/ocorrencias/{id}/status", ocorrencia.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrador))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(cidadao.getId().toString()))
                .andExpect(jsonPath("$.descricao").value("Buraco na via"))
                .andExpect(jsonPath("$.categoria").value("BURACO"))
                .andExpect(jsonPath("$.latitude").value(-12.97))
                .andExpect(jsonPath("$.prioridade").isEmpty());

        entityManager.flush();
        entityManager.clear();
        assertEquals(cidadao.getId(), ocorrenciaRepository.findById(ocorrencia.getId())
                .orElseThrow().getUsuario().getId());
    }

    @ParameterizedTest
    @CsvSource({"ENVIADA,ENVIADA", "ENVIADA,RESOLVIDA", "EM_ANALISE,ENVIADA",
            "EM_ANALISE,EM_ANALISE", "RESOLVIDA,ENVIADA", "RESOLVIDA,EM_ANALISE", "RESOLVIDA,RESOLVIDA"})
    void deveRetornar409ParaTransicaoNaoPermitidaSemAlterarEstado(StatusOcorrencia origem,
                                                                 StatusOcorrencia destino) throws Exception {
        Usuario administrador = criarUsuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Ocorrencia ocorrencia = criarOcorrencia(administrador, origem);
        UUID id = ocorrencia.getId();
        LocalDateTime atualizacaoAnterior = ocorrencia.getDataAtualizacao();

        mockMvc.perform(patch("/api/v1/ocorrencias/{id}/status", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrador))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(destino)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem").value("Transição de status inválida."));

        entityManager.clear();
        Ocorrencia salva = ocorrenciaRepository.findById(id).orElseThrow();
        assertEquals(origem, salva.getStatus());
        assertEquals(atualizacaoAnterior, salva.getDataAtualizacao());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveRetornar403ParaCidadaoMesmoQuandoForAutor(boolean propria) throws Exception {
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        Usuario autor = propria ? cidadao : criarUsuario("outro@email.com", PerfilUsuario.CIDADAO);
        Ocorrencia ocorrencia = criarOcorrencia(autor, StatusOcorrencia.ENVIADA);

        mockMvc.perform(patch("/api/v1/ocorrencias/{id}/status", ocorrencia.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(cidadao))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(StatusOcorrencia.EM_ANALISE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        entityManager.clear();
        assertEquals(StatusOcorrencia.ENVIADA, ocorrenciaRepository.findById(ocorrencia.getId())
                .orElseThrow().getStatus());
    }

    @Test
    void deveRetornar403AntesDeBuscarOcorrenciaInexistenteParaCidadao() throws Exception {
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);

        mockMvc.perform(patch("/api/v1/ocorrencias/{id}/status", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(cidadao))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(StatusOcorrencia.EM_ANALISE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deveRetornar404ParaAdministradorQuandoOcorrenciaNaoExistir() throws Exception {
        Usuario administrador = criarUsuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);

        mockMvc.perform(patch("/api/v1/ocorrencias/{id}/status", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrador))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(StatusOcorrencia.EM_ANALISE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "{\"status\":null}", "{\"status\":\"\"}", "{\"status\":\"INVALIDO\"}"})
    void deveRetornar400ParaCorpoInvalidoSemAtualizar(String body) throws Exception {
        Usuario administrador = criarUsuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Ocorrencia ocorrencia = criarOcorrencia(administrador, StatusOcorrencia.ENVIADA);

        mockMvc.perform(patch("/api/v1/ocorrencias/{id}/status", ocorrencia.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrador))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertEquals(StatusOcorrencia.ENVIADA, ocorrencia.getStatus());
        assertNull(ocorrencia.getDataAtualizacao());
    }

    @Test
    void deveRetornar400PadronizadoParaIdentificadorInvalido() throws Exception {
        Usuario administrador = criarUsuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);

        mockMvc.perform(patch("/api/v1/ocorrencias/nao-e-uuid/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrador))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(StatusOcorrencia.EM_ANALISE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void deveExigirAutenticacaoParaAtualizar() throws Exception {
        mockMvc.perform(patch("/api/v1/ocorrencias/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(StatusOcorrencia.EM_ANALISE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveRecusarTokenDeUsuarioRemovido() throws Exception {
        Usuario administrador = criarUsuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        String token = bearer(administrador);
        usuarioRepository.delete(administrador);
        usuarioRepository.flush();

        mockMvc.perform(patch("/api/v1/ocorrencias/{id}/status", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(StatusOcorrencia.EM_ANALISE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveRecusarTokenAdministrativoQuandoPerfilAtualNaoForAdministrador() throws Exception {
        Usuario administrador = criarUsuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Ocorrencia ocorrencia = criarOcorrencia(administrador, StatusOcorrencia.ENVIADA);
        String token = bearer(administrador);
        ReflectionTestUtils.setField(administrador, "perfil", PerfilUsuario.CIDADAO);
        usuarioRepository.flush();
        entityManager.clear();

        mockMvc.perform(patch("/api/v1/ocorrencias/{id}/status", ocorrencia.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(StatusOcorrencia.EM_ANALISE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private Usuario criarUsuario(String email, PerfilUsuario perfil) {
        return usuarioRepository.saveAndFlush(new Usuario("Pessoa Teste", email, "hash", perfil, null));
    }

    private Ocorrencia criarOcorrencia(Usuario usuario, StatusOcorrencia status) {
        Ocorrencia ocorrencia = new Ocorrencia(usuario, CategoriaOcorrencia.BURACO,
                "Buraco na via", new Localizacao(-12.97, -38.50, null));
        ReflectionTestUtils.setField(ocorrencia, "status", status);
        if (status != StatusOcorrencia.ENVIADA) {
            ReflectionTestUtils.setField(ocorrencia, "dataAtualizacao", LocalDateTime.of(2026, 1, 2, 12, 0));
        }
        ReflectionTestUtils.setField(ocorrencia, "dataCriacao", LocalDateTime.of(2026, 1, 1, 12, 0));
        return ocorrenciaRepository.saveAndFlush(ocorrencia);
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(usuario);
    }

    private String corpo(StatusOcorrencia status) {
        return "{\"status\":\"" + status.name() + "\"}";
    }
}
