package app.zelar.ocorrencia.storage;

import app.zelar.ocorrencia.dto.FotografiaValidada;
import app.zelar.ocorrencia.exception.FotografiaStorageException;
import app.zelar.shared.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SupabaseFotografiaStorageTest {

    private static final String BASE = "https://example.supabase.co";
    private static final String BUCKET = "ocorrencias-fotografias";
    private static final String SECRET = "sb_secret_ficticio_apenas_para_teste";
    private static final String CHAVE = "12345678-1234-4234-8234-123456789abc.png";
    private static final byte[] CONTEUDO = {1, 2, 3};
    private MockRestServiceServer servidor;
    private RestClient.Builder builder;
    private FotografiaStorage storage;

    @BeforeEach
    void configurar() {
        builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        storage = new SupabaseFotografiaStorage(builder, URI.create(BASE), SECRET, BUCKET);
    }

    @Test
    void aceitaUrlDoProjetoComBarraFinalSemDuplicarCaminho() {
        storage = new SupabaseFotografiaStorage(builder, URI.create(BASE + "/"), SECRET, BUCKET);
        servidor.expect(requestTo(BASE + "/storage/v1/object/authenticated/" + BUCKET + "/" + CHAVE))
                .andRespond(withSuccess(CONTEUDO, MediaType.IMAGE_PNG));
        assertArrayEquals(CONTEUDO, storage.ler(CHAVE));
        servidor.verify();
    }

    @Test
    void enviaConteudoValidadoSemSobrescreverNemExporCredencialComoBearer() {
        servidor.expect(requestTo(BASE + "/storage/v1/object/" + BUCKET + "/" + CHAVE))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", SECRET))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(header("x-upsert", "false"))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(CONTEUDO))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        storage.salvar(CHAVE, fotografia());
        servidor.verify();
    }

    @Test
    void consultaObjetoPeloCaminhoAutenticadoDoBucketPrivado() {
        servidor.expect(requestTo(BASE + "/storage/v1/object/authenticated/" + BUCKET + "/" + CHAVE))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", SECRET))
                .andRespond(withSuccess(CONTEUDO, MediaType.IMAGE_PNG));
        assertArrayEquals(CONTEUDO, storage.ler(CHAVE));
        servidor.verify();
    }

    @Test
    void exclusaoParaCompensacaoEnviaSomenteAChaveConhecida() {
        servidor.expect(requestTo(BASE + "/storage/v1/object/" + BUCKET))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("apikey", SECRET))
                .andExpect(content().json("{\"prefixes\":[\"" + CHAVE + "\"]}"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        storage.excluir(CHAVE);
        servidor.verify();
    }

    @Test
    void objetoAusenteRetornaErroDeRecursoNaoEncontrado() {
        servidor.expect(anything()).andRespond(withResourceNotFound());
        assertThrows(RecursoNaoEncontradoException.class, () -> storage.ler(CHAVE));
        servidor.verify();
    }

    @Test
    void falhaDoProvedorNaoExpoeCorpoNemCredencial() {
        servidor.expect(anything()).andRespond(withServerError().body("segredo-interno " + SECRET));
        var erro = assertThrows(FotografiaStorageException.class, () -> storage.salvar(CHAVE, fotografia()));
        assertEquals("Não foi possível acessar o armazenamento de fotografias.", erro.getMessage());
        assertNull(erro.getCause());
        servidor.verify();
    }

    @Test
    void conflitoNaoSobrescreveObjetoExistente() {
        servidor.expect(anything()).andRespond(withStatus(HttpStatus.CONFLICT));
        assertThrows(FotografiaStorageException.class, () -> storage.salvar(CHAVE, fotografia()));
        servidor.verify();
    }

    @Test
    void falhaDeRedeTemErroControladoEPermiteCompensarChaveJaConhecida() {
        servidor.expect(anything()).andRespond(withException(new IOException("detalhe privado")));
        assertThrows(FotografiaStorageException.class, () -> storage.salvar(CHAVE, fotografia()));
        servidor.verify();
    }

    @Test
    void falhaNaExclusaoNaoEhIgnorada() {
        servidor.expect(anything()).andRespond(withServerError());
        assertThrows(FotografiaStorageException.class, () -> storage.excluir(CHAVE));
        servidor.verify();
    }

    @Test
    void leituraComFalhaDeAutorizacaoDoProvedorEhFalhaDeInfraestrutura() {
        servidor.expect(anything()).andRespond(withUnauthorizedRequest());
        assertThrows(FotografiaStorageException.class, () -> storage.ler(CHAVE));
        servidor.verify();
    }

    @Test
    void leituraNaoAceitaRespostaVazia() {
        servidor.expect(anything()).andRespond(withSuccess(new byte[0], MediaType.IMAGE_PNG));
        assertThrows(FotografiaStorageException.class, () -> storage.ler(CHAVE));
        servidor.verify();
    }

    @Test
    void leituraTemLimiteMesmoSemContentLengthConfiavel() {
        servidor.expect(anything()).andRespond(withSuccess(
                new byte[FotografiaValidada.TAMANHO_MAXIMO_BYTES + 1], MediaType.IMAGE_PNG));
        assertThrows(FotografiaStorageException.class, () -> storage.ler(CHAVE));
        servidor.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../foto.png", "pasta/foto.png", "https://outro/foto.png", "foto.png",
            "12345678-1234-4234-8234-123456789abc.svg", "12345678-1234-4234-8234-123456789abc.png?x=1",
            "12345678-1234-4234-8234-123456789abc%2epng"})
    void rejeitaChaveInseguraAntesDeQualquerRequisicao(String chave) {
        assertThrows(IllegalArgumentException.class, () -> storage.ler(chave));
        assertThrows(IllegalArgumentException.class, () -> storage.excluir(chave));
        assertThrows(IllegalArgumentException.class, () -> storage.salvar(chave, fotografia()));
        servidor.verify();
    }

    @Test
    void extensaoDaChaveDeveCorresponderAoConteudoValidado() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.salvar(CHAVE.replace(".png", ".jpg"), fotografia()));
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.supabase.co", "https://usuario:senha@example.supabase.co",
            "https://example.supabase.co/storage", "https://example.supabase.co?x=1",
            "https://example.supabase.co#fragmento"})
    void rejeitaUrlQuePodeExporCredencialOuAlterarRota(String url) {
        assertThrows(IllegalArgumentException.class, () -> new SupabaseFotografiaStorage(
                RestClient.builder(), URI.create(url), SECRET, BUCKET));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"sb_publishable_publica", "jwt-do-zelar", "sb_secret_\nvalor"})
    void exigeSecretKeyDoServidor(String secret) {
        assertThrows(IllegalArgumentException.class, () -> new SupabaseFotografiaStorage(
                RestClient.builder(), URI.create(BASE), secret, BUCKET));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../bucket", "bucket/outro", "bucket?public=true", "bucket com espaco"})
    void rejeitaNomeDeBucketInseguro(String bucket) {
        assertThrows(IllegalArgumentException.class, () -> new SupabaseFotografiaStorage(
                RestClient.builder(), URI.create(BASE), SECRET, bucket));
    }

    private FotografiaValidada fotografia() {
        return new FotografiaValidada(CONTEUDO, "image/png", "png", 1, 1);
    }
}
