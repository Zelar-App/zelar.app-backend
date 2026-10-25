package app.zelar.ocorrencia;

import app.zelar.autenticacao.service.TokenService;
import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import app.zelar.ocorrencia.entity.Localizacao;
import app.zelar.ocorrencia.entity.Ocorrencia;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zelar-consulta;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OcorrenciaConsultaIntegrationTest {

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

    @Test
    void deveConsultarPropriaOcorrenciaSemExporDadosPessoais() throws Exception {
        Usuario usuario = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        UUID usuarioId = usuario.getId();
        UUID ocorrenciaId = criarOcorrencia(usuario).getId();
        String token = bearer(usuario);
        entityManager.clear();

        mockMvc.perform(get("/api/v1/ocorrencias/{id}", ocorrenciaId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ocorrenciaId.toString()))
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.categoria").value("BURACO"))
                .andExpect(jsonPath("$.descricao").value("Buraco na via"))
                .andExpect(jsonPath("$.latitude").value(-12.97))
                .andExpect(jsonPath("$.longitude").value(-38.50))
                .andExpect(jsonPath("$.enderecoAproximado").value("Praça central"))
                .andExpect(jsonPath("$.status").value("ENVIADA"))
                .andExpect(jsonPath("$.prioridade").isEmpty())
                .andExpect(jsonPath("$.dataCriacao").isNotEmpty())
                .andExpect(jsonPath("$.dataAtualizacao").isEmpty())
                .andExpect(jsonPath("$.usuario").doesNotExist())
                .andExpect(jsonPath("$.nome").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void devePermitirConsultaDeOutroUsuarioParaAdministrador() throws Exception {
        Usuario administrador = criarUsuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        Ocorrencia ocorrencia = criarOcorrencia(cidadao);

        mockMvc.perform(get("/api/v1/ocorrencias/{id}", ocorrencia.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ocorrencia.getId().toString()))
                .andExpect(jsonPath("$.usuarioId").value(cidadao.getId().toString()));
    }

    @Test
    void deveOcultarOcorrenciaDeOutroCidadaoMesmoComAutorNaConsulta() throws Exception {
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        Usuario outro = criarUsuario("outro@email.com", PerfilUsuario.CIDADAO);
        Ocorrencia ocorrencia = criarOcorrencia(outro);

        mockMvc.perform(get("/api/v1/ocorrencias/{id}", ocorrencia.getId())
                        .param("usuarioId", outro.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensagem").value("Ocorrência não encontrada."))
                .andExpect(jsonPath("$.caminho").value("/api/v1/ocorrencias/" + ocorrencia.getId()))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void deveRetornar404PadronizadoQuandoOcorrenciaNaoExistir() throws Exception {
        Usuario usuario = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/ocorrencias/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensagem").value("Ocorrência não encontrada."))
                .andExpect(jsonPath("$.caminho").value("/api/v1/ocorrencias/" + id))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nao-e-uuid", "123", "00000000000000000000000000000000"})
    void deveRetornar400PadronizadoParaIdentificadorInvalido(String id) throws Exception {
        Usuario usuario = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);

        mockMvc.perform(get("/api/v1/ocorrencias/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value("Parâmetro 'id' inválido."))
                .andExpect(jsonPath("$.caminho").value("/api/v1/ocorrencias/" + id));
    }

    @Test
    void deveExigirAutenticacaoParaConsultar() throws Exception {
        mockMvc.perform(get("/api/v1/ocorrencias/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveRecusarTokenInvalidoNaConsulta() throws Exception {
        mockMvc.perform(get("/api/v1/ocorrencias/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveRecusarConsultaComTokenDeUsuarioRemovido() throws Exception {
        Usuario usuario = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        String token = bearer(usuario);
        usuarioRepository.delete(usuario);
        usuarioRepository.flush();

        mockMvc.perform(get("/api/v1/ocorrencias/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void deveAplicarPerfilAtualMesmoQuandoTokenAindaIndicarAdministrador() throws Exception {
        Usuario administrador = criarUsuario("admin@email.com", PerfilUsuario.ADMINISTRADOR);
        Usuario cidadao = criarUsuario("cidadao@email.com", PerfilUsuario.CIDADAO);
        UUID ocorrenciaId = criarOcorrencia(cidadao).getId();
        String token = bearer(administrador);
        ReflectionTestUtils.setField(administrador, "perfil", PerfilUsuario.CIDADAO);
        usuarioRepository.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/ocorrencias/{id}", ocorrenciaId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Ocorrência não encontrada."));
    }

    private Usuario criarUsuario(String email, PerfilUsuario perfil) {
        return usuarioRepository.saveAndFlush(new Usuario("Pessoa Teste", email, "hash", perfil, "71999999999"));
    }

    private Ocorrencia criarOcorrencia(Usuario usuario) {
        return ocorrenciaRepository.saveAndFlush(new Ocorrencia(usuario, CategoriaOcorrencia.BURACO,
                "Buraco na via", new Localizacao(-12.97, -38.50, "Praça central")));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerar(usuario);
    }
}
