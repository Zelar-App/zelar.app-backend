package app.zelar.ocorrencia;

import app.zelar.autenticacao.service.TokenService;
import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import app.zelar.ocorrencia.entity.Localizacao;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.repository.OcorrenciaRepository;
import app.zelar.ocorrencia.service.FotografiaService;
import app.zelar.ocorrencia.service.FotografiaPersistenciaService;
import app.zelar.ocorrencia.storage.FotografiaStorage;
import app.zelar.ocorrencia.exception.FotografiaStorageException;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:zelar-fotografia;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FotografiaIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private OcorrenciaRepository ocorrencias;
    @Autowired private TokenService tokens;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FotografiaService service;
    @MockitoBean private FotografiaStorage storage;
    @MockitoSpyBean private FotografiaPersistenciaService persistencia;
    @LocalServerPort private int porta;

    private Usuario autor;
    private Usuario outro;
    private Usuario admin;
    private UUID id;
    private byte[] png;

    @DynamicPropertySource
    static void configurarPostgresOpcional(DynamicPropertyRegistry registry) {
        String url = System.getenv("BE13_TEST_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv("BE13_TEST_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv("BE13_TEST_PASSWORD"));
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
    }

    @BeforeEach
    void preparar() throws Exception {
        autor = usuario(PerfilUsuario.CIDADAO);
        outro = usuario(PerfilUsuario.CIDADAO);
        admin = usuario(PerfilUsuario.ADMINISTRADOR);
        id = ocorrencias.save(new Ocorrencia(autor, CategoriaOcorrencia.BURACO,
                "Buraco de teste", new Localizacao(0.0, 0.0, null))).getId();
        var saida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 8, BufferedImage.TYPE_INT_RGB), "png", saida);
        png = saida.toByteArray();
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM anexo WHERE ocorrencia_id = ?", id);
        ocorrencias.deleteById(id);
        usuarios.deleteAllById(List.of(autor.getId(), outro.getId(), admin.getId()));
    }

    @Test
    void deveEnviarEConsultarFotografiaSemExporChaveDoStorage() throws Exception {
        mvc.perform(multipart(caminho()).file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.LOCATION, caminho()))
                .andExpect(jsonPath("$.tipoConteudo").value("image/png"))
                .andExpect(jsonPath("$.bucket").doesNotExist()).andExpect(jsonPath("$.chaveObjeto").doesNotExist());
        String chave = jdbc.queryForObject("SELECT chave_objeto FROM anexo WHERE ocorrencia_id = ?", String.class, id);
        assertEquals("DISPONIVEL", estado());
        assertTrue(chave.matches("[0-9a-f-]{36}\\.png"));
        when(storage.ler(chave)).thenReturn(png);
        mvc.perform(get(caminho()).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png)).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"));
        mvc.perform(get(caminho()).header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isOk());
    }

    @Test
    void deveRefletirPresencaDaFotografiaNoDetalheENaListagem() throws Exception {
        mvc.perform(get("/api/v1/ocorrencias/" + id).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(jsonPath("$.possuiFotografia").value(false));
        enviar();
        mvc.perform(get("/api/v1/ocorrencias/" + id).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(jsonPath("$.possuiFotografia").value(true));
        mvc.perform(get("/api/v1/ocorrencias").header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(jsonPath("$.conteudo[0].possuiFotografia").value(true));
    }

    @Test
    void deveRecusarOutroCidadaoSemRevelarOcorrenciaOuAcessarStorage() throws Exception {
        mvc.perform(multipart(caminho()).file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(outro)))
                .andExpect(status().isNotFound());
        mvc.perform(get(caminho()).header(HttpHeaders.AUTHORIZATION, bearer(outro))).andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }

    @Test
    void administradorNaoPodeEnviarFotografiaEmNomeDoAutor() throws Exception {
        mvc.perform(multipart(caminho()).file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(storage);
    }

    @Test
    void deveExigirAutenticacaoEmEnvioEConsulta() throws Exception {
        mvc.perform(multipart(caminho()).file(foto())).andExpect(status().isUnauthorized());
        mvc.perform(get(caminho())).andExpect(status().isUnauthorized());
        verifyNoInteractions(storage);
    }

    @Test
    void deveTratarFotoAusenteConteudoInvalidoEExcessoSemPersistirAnexo() throws Exception {
        mvc.perform(multipart(caminho()).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(multipart(caminho()).file(new MockMultipartFile("foto", "a.png", "image/png", new byte[]{1, 2}))
                .header(HttpHeaders.AUTHORIZATION, bearer(autor))).andExpect(status().isBadRequest());
        mvc.perform(multipart(caminho()).file(new MockMultipartFile("foto", "a.png", "image/png", new byte[5242881]))
                .header(HttpHeaders.AUTHORIZATION, bearer(autor))).andExpect(status().isBadRequest());
        assertEquals(0, quantidade());
        verifyNoInteractions(storage);
    }

    @Test
    void deveRecusarSegundaFotoSemSobrescreverPrimeira() throws Exception {
        enviar();
        mvc.perform(multipart(caminho()).file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(status().isConflict());
        assertEquals(1, quantidade());
        verify(storage, times(1)).salvar(anyString(), any());
    }

    @Test
    void deveManterIntentoDuravelQuandoStorageFalhaSemApresentarFotoDisponivel() throws Exception {
        doThrow(new FotografiaStorageException()).when(storage).salvar(anyString(), any());
        mvc.perform(multipart(caminho()).file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503));
        assertEquals("LIMPEZA_PENDENTE", estado());
        assertEquals(1, quantidade());
        mvc.perform(get(caminho()).header(HttpHeaders.AUTHORIZATION, bearer(autor))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/ocorrencias/" + id).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(jsonPath("$.possuiFotografia").value(false));
        verify(storage, never()).excluir(anyString());
    }

    @Test
    void deveRecuperarFalhaSomenteAposCarenciaEPermitirReenvioNaMesmaOcorrencia() throws Exception {
        doThrow(new FotografiaStorageException()).when(storage).salvar(anyString(), any());
        mvc.perform(multipart(caminho()).file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(status().isServiceUnavailable());
        service.recuperarPendentes();
        verify(storage, never()).excluir(anyString());
        envelhecer();
        service.recuperarPendentes();
        assertEquals(0, quantidade());
        verify(storage).excluir(anyString());
        doNothing().when(storage).salvar(anyString(), any());
        enviar();
        assertEquals("DISPONIVEL", estado());
        assertTrue(ocorrencias.existsById(id));
    }

    @Test
    void falhaNaLimpezaMantemChaveParaNovaTentativa() throws Exception {
        doThrow(new FotografiaStorageException()).when(storage).salvar(anyString(), any());
        mvc.perform(multipart(caminho()).file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(status().isServiceUnavailable());
        envelhecer();
        doThrow(new FotografiaStorageException()).when(storage).excluir(anyString());
        service.recuperarPendentes();
        assertEquals(1, quantidade());
        assertEquals("LIMPEZA_PENDENTE", estado());
    }

    @Test
    void deveRecuperarReservaAbandonadaMasPreservarFotografiaDisponivel() throws Exception {
        enviar();
        envelhecer();
        service.recuperarPendentes();
        verify(storage, never()).excluir(anyString());
        jdbc.update("UPDATE anexo SET estado_upload = 'PENDENTE' WHERE ocorrencia_id = ?", id);
        service.recuperarPendentes();
        assertEquals(0, quantidade());
    }

    @Test
    void devePersistirReservaAntesDaChamadaExterna() throws Exception {
        doAnswer(invocacao -> {
            assertEquals("PENDENTE", estado());
            assertEquals(1, quantidade());
            return null;
        }).when(storage).salvar(anyString(), any());
        enviar();
    }

    @Test
    void deveRecusarContaRemovidaEIdentificadorInvalido() throws Exception {
        Usuario removido = usuario(PerfilUsuario.CIDADAO);
        String token = bearer(removido);
        usuarios.deleteById(removido.getId());
        mvc.perform(get(caminho()).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ocorrencias/invalido/fotografia").header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRecusarDoisEnviosSimultaneosSemDuplicarUpload() throws Exception {
        CountDownLatch iniciou = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        doAnswer(invocacao -> {
            iniciou.countDown();
            assertTrue(liberar.await(10, TimeUnit.SECONDS));
            return null;
        }).when(storage).salvar(anyString(), any());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var primeiro = executor.submit(() -> {
                enviar();
                return true;
            });
            try {
                assertTrue(iniciou.await(10, TimeUnit.SECONDS));
                mvc.perform(multipart(caminho()).file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                        .andExpect(status().isConflict());
            } finally {
                liberar.countDown();
            }
            assertTrue(primeiro.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, quantidade());
        verify(storage, times(1)).salvar(anyString(), any());
    }

    @Test
    void falhaAoConfirmarUploadMantemChaveParaCompensacao() throws Exception {
        doThrow(new IllegalStateException("falha interna que não deve ser exposta"))
                .when(persistencia).disponibilizar(any());
        mvc.perform(multipart(caminho()).file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagem").value("Não foi possível acessar o armazenamento de fotografias."));
        assertEquals("LIMPEZA_PENDENTE", estado());
        envelhecer();
        service.recuperarPendentes();
        verify(storage).excluir(anyString());
        assertEquals(0, quantidade());
    }

    @Test
    void reservaPermaneceRecuperavelMesmoSeNaoForPossivelSinalizarFalha() throws Exception {
        doThrow(new FotografiaStorageException()).when(storage).salvar(anyString(), any());
        doThrow(new IllegalStateException("banco indisponível")).when(persistencia).sinalizarFalha(any());
        mvc.perform(multipart(caminho()).file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(status().isServiceUnavailable());
        assertEquals("PENDENTE", estado());
        envelhecer();
        service.recuperarPendentes();
        assertEquals(0, quantidade());
    }

    @Test
    void devePreservarIndicadorDeFotoNasAtualizacoesAdministrativas() throws Exception {
        enviar();
        mvc.perform(patch("/api/v1/ocorrencias/" + id + "/prioridade")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)).contentType("application/json")
                .content("{\"prioridade\":\"ALTA\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.possuiFotografia").value(true));
        mvc.perform(patch("/api/v1/ocorrencias/" + id + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)).contentType("application/json")
                .content("{\"status\":\"EM_ANALISE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.possuiFotografia").value(true));
    }

    @Test
    void servidorDeveRejeitarMultipartAcimaDeCincoMiBSemReservarAnexo() throws Exception {
        String limite = "limite-foto-zelar";
        var corpo = new ByteArrayOutputStream();
        corpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"foto\"; filename=\"foto.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        corpo.write(new byte[5242881]);
        corpo.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + caminho()))
                .timeout(Duration.ofSeconds(15)).header(HttpHeaders.AUTHORIZATION, bearer(autor))
                .header("Content-Type", "multipart/form-data; boundary=" + limite)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray())).build();
        try (var client = HttpClient.newHttpClient()) {
            var resposta = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(413, resposta.statusCode());
            assertTrue(resposta.body().contains("\"status\":413"));
        }
        assertEquals(0, quantidade());
        verifyNoInteractions(storage);
    }

    private void enviar() throws Exception {
        mvc.perform(multipart(caminho()).file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(autor)))
                .andExpect(status().isCreated());
    }

    private Usuario usuario(PerfilUsuario perfil) {
        return usuarios.save(new Usuario("Usuário de teste", UUID.randomUUID() + "@example.com", "hash", perfil, null));
    }

    private MockMultipartFile foto() { return new MockMultipartFile("foto", "../../ignorado.png", "image/png", png); }
    private String caminho() { return "/api/v1/ocorrencias/" + id + "/fotografia"; }
    private String bearer(Usuario usuario) { return "Bearer " + tokens.gerar(usuario); }
    private int quantidade() { return jdbc.queryForObject("SELECT COUNT(*) FROM anexo WHERE ocorrencia_id = ?", Integer.class, id); }
    private String estado() { return jdbc.queryForObject("SELECT estado_upload FROM anexo WHERE ocorrencia_id = ?", String.class, id); }
    private void envelhecer() { jdbc.update("UPDATE anexo SET data_atualizacao = ? WHERE ocorrencia_id = ?", Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(20)), id); }
}
