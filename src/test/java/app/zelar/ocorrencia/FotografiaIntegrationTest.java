package app.zelar.ocorrencia;

import app.zelar.autenticacao.service.TokenService;
import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import app.zelar.ocorrencia.entity.Localizacao;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.repository.OcorrenciaRepository;
import app.zelar.ocorrencia.service.FotografiaService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
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

    private Usuario autor;
    private Usuario outro;
    private Usuario admin;
    private UUID id;
    private byte[] png;

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
