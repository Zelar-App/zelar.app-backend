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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelar-listagem;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OcorrenciaListagemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;
    @Autowired
    private TokenService tokenService;

    @Test
    void deveListarSomenteOcorrenciasDoCidadaoSemExporDadosPessoais() throws Exception {
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        Usuario outro = criarUsuario("outro@email.com", PerfilUsuario.CIDADAO);
        Ocorrencia propria = criarOcorrencia(cidadao, StatusOcorrencia.ENVIADA, null, 1);
        criarOcorrencia(outro, StatusOcorrencia.ENVIADA, null, 2);

        mockMvc.perform(get("/api/v1/ocorrencias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.conteudo.length()").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(propria.getId().toString()))
                .andExpect(jsonPath("$.conteudo[0].usuarioId").value(cidadao.getId().toString()))
                .andExpect(jsonPath("$.conteudo[0].categoria").value("BURACO"))
                .andExpect(jsonPath("$.conteudo[0].descricao").value("Buraco na via"))
                .andExpect(jsonPath("$.conteudo[0].latitude").value(-12.97))
                .andExpect(jsonPath("$.conteudo[0].longitude").value(-38.50))
                .andExpect(jsonPath("$.conteudo[0].status").value("ENVIADA"))
                .andExpect(jsonPath("$.conteudo[0].dataCriacao").isNotEmpty())
                .andExpect(jsonPath("$.conteudo[0].usuario").doesNotExist())
                .andExpect(jsonPath("$.conteudo[0].nome").doesNotExist())
                .andExpect(jsonPath("$.conteudo[0].email").doesNotExist())
                .andExpect(jsonPath("$.conteudo[0].telefone").doesNotExist())
                .andExpect(jsonPath("$.conteudo[0].senha").doesNotExist());
    }

    @Test
    void devePermitirQueAdministradorListeOcorrenciasDeTodosOsUsuarios() throws Exception {
        Usuario administrador = criarUsuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        criarOcorrencia(administrador, StatusOcorrencia.ENVIADA, null, 1);
        criarOcorrencia(cidadao, StatusOcorrencia.EM_ANALISE, PrioridadeOcorrencia.ALTA, 2);

        mockMvc.perform(get("/api/v1/ocorrencias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[0].usuarioId").value(cidadao.getId().toString()))
                .andExpect(jsonPath("$.conteudo[1].usuarioId").value(administrador.getId().toString()));
    }

    @Test
    void devePaginarPorDataDecrescenteComDesempatePorId() throws Exception {
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        Ocorrencia antiga = criarOcorrencia(cidadao, StatusOcorrencia.ENVIADA, null, 1);
        Ocorrencia primeira = criarOcorrencia(cidadao, StatusOcorrencia.ENVIADA, null, 2);
        Ocorrencia segunda = criarOcorrencia(cidadao, StatusOcorrencia.ENVIADA, null, 2);
        List<String> idsRecentes = List.of(primeira.getId().toString(), segunda.getId().toString())
                .stream().sorted(Comparator.reverseOrder()).toList();

        for (int pagina = 0; pagina < 3; pagina++) {
            String esperado = pagina < 2 ? idsRecentes.get(pagina) : antiga.getId().toString();
            mockMvc.perform(get("/api/v1/ocorrencias").param("pagina", String.valueOf(pagina))
                            .param("tamanho", "1").header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pagina").value(pagina))
                    .andExpect(jsonPath("$.tamanho").value(1))
                    .andExpect(jsonPath("$.totalElementos").value(3))
                    .andExpect(jsonPath("$.totalPaginas").value(3))
                    .andExpect(jsonPath("$.conteudo.length()").value(1))
                    .andExpect(jsonPath("$.conteudo[0].id").value(esperado));
        }
    }

    @Test
    void deveFiltrarStatusEPrioridadeEmConjuntoParaAdministrador() throws Exception {
        Usuario administrador = criarUsuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        Ocorrencia esperada = criarOcorrencia(cidadao, StatusOcorrencia.EM_ANALISE, PrioridadeOcorrencia.ALTA, 1);
        criarOcorrencia(cidadao, StatusOcorrencia.EM_ANALISE, PrioridadeOcorrencia.BAIXA, 2);
        criarOcorrencia(cidadao, StatusOcorrencia.RESOLVIDA, PrioridadeOcorrencia.ALTA, 3);

        mockMvc.perform(get("/api/v1/ocorrencias").param("status", "EM_ANALISE")
                        .param("prioridade", "ALTA").header(HttpHeaders.AUTHORIZATION, bearer(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(esperada.getId().toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"status=ENVIADA", "prioridade=ALTA", "status=ENVIADA&prioridade=ALTA"})
    void deveAplicarFiltrosSemPermitirConsultaDeOutroCidadao(String consulta) throws Exception {
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        Usuario outro = criarUsuario("outro@email.com", PerfilUsuario.CIDADAO);
        Ocorrencia propria = criarOcorrencia(cidadao, StatusOcorrencia.ENVIADA, PrioridadeOcorrencia.ALTA, 1);
        criarOcorrencia(outro, StatusOcorrencia.ENVIADA, PrioridadeOcorrencia.ALTA, 2);

        mockMvc.perform(get("/api/v1/ocorrencias?" + consulta + "&usuarioId=" + outro.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(propria.getId().toString()));
    }

    @Test
    void deveRetornarPaginaVaziaParaCidadaoSemOcorrencias() throws Exception {
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        Usuario outro = criarUsuario("outro@email.com", PerfilUsuario.CIDADAO);
        criarOcorrencia(outro, StatusOcorrencia.ENVIADA, null, 1);

        mockMvc.perform(get("/api/v1/ocorrencias").header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo").isArray())
                .andExpect(jsonPath("$.conteudo.length()").value(0))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @Test
    void deveRetornarPaginaVaziaQuandoPaginaSolicitadaNaoTiverResultados() throws Exception {
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        criarOcorrencia(cidadao, StatusOcorrencia.ENVIADA, null, 1);

        mockMvc.perform(get("/api/v1/ocorrencias").param("pagina", "1").param("tamanho", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(0))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void deveAceitarTamanhoMaximoDeCemRegistros() throws Exception {
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);

        mockMvc.perform(get("/api/v1/ocorrencias").param("tamanho", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanho").value(100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pagina=-1", "tamanho=0", "tamanho=101", "pagina=abc", "tamanho=abc",
            "pagina=2147483648", "status=INVALIDO", "prioridade=URGENTE"})
    void deveRecusarParametrosInvalidosComErroPadronizado(String consulta) throws Exception {
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);

        mockMvc.perform(get("/api/v1/ocorrencias?" + consulta)
                        .header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.caminho").value("/api/v1/ocorrencias"));
    }

    @Test
    void deveExigirAutenticacaoParaListar() throws Exception {
        mockMvc.perform(get("/api/v1/ocorrencias"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveRecusarListagemDeUsuarioRemovido() throws Exception {
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        String token = bearer(cidadao);
        usuarioRepository.delete(cidadao);
        usuarioRepository.flush();

        mockMvc.perform(get("/api/v1/ocorrencias").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    private Usuario criarUsuario(String email, PerfilUsuario perfil) {
        return usuarioRepository.saveAndFlush(new Usuario("Pessoa Teste", email, "hash", perfil, null));
    }

    private Ocorrencia criarOcorrencia(Usuario usuario, StatusOcorrencia status,
                                      PrioridadeOcorrencia prioridade, int dia) {
        Ocorrencia ocorrencia = new Ocorrencia(usuario, CategoriaOcorrencia.BURACO,
                "Buraco na via", new Localizacao(-12.97, -38.50, null));
        ReflectionTestUtils.setField(ocorrencia, "status", status);
        ReflectionTestUtils.setField(ocorrencia, "prioridade", prioridade);
        ReflectionTestUtils.setField(ocorrencia, "dataCriacao", LocalDateTime.of(2026, 10, dia, 12, 0));
        return ocorrenciaRepository.saveAndFlush(ocorrencia);
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(usuario);
    }
}
