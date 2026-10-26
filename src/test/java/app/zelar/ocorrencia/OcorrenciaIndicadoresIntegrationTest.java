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
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelar-indicadores;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OcorrenciaIndicadoresIntegrationTest {

    private static final String URL = "/api/v1/ocorrencias/indicadores";

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
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveRetornarZerosComBancoSemOcorrencias() throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        conferir(bearer(admin), 0, 0, 0);
    }

    @ParameterizedTest
    @EnumSource(StatusOcorrencia.class)
    void deveContarStatusExistenteERetornarZeroParaOsDemais(StatusOcorrencia statusAtual) throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        ocorrencia(admin, statusAtual);
        conferir(bearer(admin), statusAtual == StatusOcorrencia.ENVIADA ? 1 : 0,
                statusAtual == StatusOcorrencia.EM_ANALISE ? 1 : 0,
                statusAtual == StatusOcorrencia.RESOLVIDA ? 1 : 0);
    }

    @Test
    void deveContarTodosOsAutoresSemExporDadosOuAplicarParametrosDaListagem() throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Usuario primeiro = usuario("primeiro@email.com", PerfilUsuario.CIDADAO);
        Usuario segundo = usuario("segundo@email.com", PerfilUsuario.CIDADAO);
        ocorrencia(primeiro, StatusOcorrencia.ENVIADA);
        ocorrencia(segundo, StatusOcorrencia.ENVIADA);
        ocorrencia(primeiro, StatusOcorrencia.EM_ANALISE);
        ocorrencia(segundo, StatusOcorrencia.RESOLVIDA);
        ocorrencia(admin, StatusOcorrencia.RESOLVIDA);
        ocorrencia(admin, StatusOcorrencia.RESOLVIDA);
        entityManager.clear();

        var resposta = mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("usuarioId", primeiro.getId().toString()).param("status", "ENVIADA")
                        .param("prioridade", "ALTA").param("pagina", "99").param("tamanho", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviadas").value(2))
                .andExpect(jsonPath("$.emAnalise").value(1))
                .andExpect(jsonPath("$.resolvidas").value(3))
                .andExpect(jsonPath("$.conteudo").doesNotExist())
                .andExpect(jsonPath("$.usuarioId").doesNotExist())
                .andExpect(jsonPath("$.nome").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist()).andReturn();
        assertEquals(3, objectMapper.readTree(resposta.getResponse().getContentAsString()).size());
        assertEquals(6, ocorrenciaRepository.count());
    }

    @Test
    void deveRefletirCriacaoEAlteracaoPersistidaDeStatusNasContagens() throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Usuario cidadao = usuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        String tokenAdmin = bearer(admin);
        conferir(tokenAdmin, 0, 0, 0);
        var resposta = mockMvc.perform(post("/api/v1/ocorrencias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cidadao)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"BURACO\",\"descricao\":\"Buraco na via\",\"latitude\":0,\"longitude\":0}"))
                .andExpect(status().isCreated()).andReturn();
        var id = java.util.UUID.fromString(objectMapper.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
        conferir(tokenAdmin, 1, 0, 0);
        for (StatusOcorrencia novoStatus : new StatusOcorrencia[]{StatusOcorrencia.EM_ANALISE, StatusOcorrencia.RESOLVIDA}) {
            entityManager.createQuery("update Ocorrencia o set o.status = :status where o.id = :id")
                    .setParameter("status", novoStatus).setParameter("id", id).executeUpdate();
            entityManager.clear();
            conferir(tokenAdmin, 0, novoStatus == StatusOcorrencia.EM_ANALISE ? 1 : 0,
                    novoStatus == StatusOcorrencia.RESOLVIDA ? 1 : 0);
        }
    }

    @Test
    void deveRecusarCidadaoMesmoSemOcorrencias() throws Exception {
        Usuario cidadao = usuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.caminho").value(URL));
    }

    @Test
    void deveExigirAutenticacao() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveRecusarTokenInvalido() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer invalido"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveRecusarUsuarioRemovido() throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        String token = bearer(admin);
        usuarioRepository.delete(admin);
        usuarioRepository.flush();
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveRecusarAdministradorRebaixadoMesmoComTokenAntigo() throws Exception {
        Usuario admin = usuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        String token = bearer(admin);
        ReflectionTestUtils.setField(admin, "perfil", PerfilUsuario.CIDADAO);
        usuarioRepository.flush();
        entityManager.clear();
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void devePermitirAdministradorAtualMesmoComTokenAnteriorDeCidadao() throws Exception {
        Usuario usuario = usuario("admin@email.com", PerfilUsuario.CIDADAO);
        String token = bearer(usuario);
        ReflectionTestUtils.setField(usuario, "perfil", PerfilUsuario.ADMINISTRADOR);
        usuarioRepository.flush();
        entityManager.clear();
        conferir(token, 0, 0, 0);
    }

    private void conferir(String token, long enviadas, long emAnalise, long resolvidas) throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviadas").value((int) enviadas))
                .andExpect(jsonPath("$.emAnalise").value((int) emAnalise))
                .andExpect(jsonPath("$.resolvidas").value((int) resolvidas));
    }

    private Usuario usuario(String email, PerfilUsuario perfil) {
        return usuarioRepository.saveAndFlush(new Usuario("Pessoa Teste", email, "hash", perfil, "71999999999"));
    }

    private void ocorrencia(Usuario autor, StatusOcorrencia statusAtual) {
        Ocorrencia ocorrencia = new Ocorrencia(autor, CategoriaOcorrencia.BURACO,
                "Buraco na via", new Localizacao(0.0, 0.0, null));
        ReflectionTestUtils.setField(ocorrencia, "status", statusAtual);
        ocorrenciaRepository.saveAndFlush(ocorrencia);
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(usuario);
    }
}
