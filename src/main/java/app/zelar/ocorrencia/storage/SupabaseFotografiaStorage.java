package app.zelar.ocorrencia.storage;

import app.zelar.ocorrencia.dto.FotografiaValidada;
import app.zelar.ocorrencia.exception.FotografiaStorageException;
import app.zelar.shared.exception.RecursoNaoEncontradoException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class SupabaseFotografiaStorage implements FotografiaStorage {

    private static final Pattern CHAVE_VALIDA = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");

    private final RestClient client;
    private final String bucket;

    public SupabaseFotografiaStorage(RestClient.Builder builder, URI url, String secretKey, String bucket) {
        Assert.notNull(url, "A URL do armazenamento é obrigatória.");
        Assert.isTrue("https".equals(url.getScheme()) && url.getHost() != null
                        && url.getUserInfo() == null && url.getQuery() == null && url.getFragment() == null
                        && (url.getPath().isEmpty() || "/".equals(url.getPath())),
                "Configure somente a origem HTTPS do projeto Supabase.");
        Assert.isTrue(secretKey != null && secretKey.matches("sb_secret_[A-Za-z0-9_-]+"),
                "Configure uma secret key do Supabase exclusiva do servidor.");
        Assert.isTrue(bucket != null && bucket.matches("[a-z0-9][a-z0-9-]{0,99}"),
                "Configure um nome de bucket válido.");
        this.bucket = bucket;
        this.client = builder.clone().baseUrl(url.toString()).defaultHeader("apikey", secretKey).build();
    }

    @Override
    public void salvar(String chave, FotografiaValidada fotografia) {
        validarChave(chave);
        Objects.requireNonNull(fotografia, "A fotografia validada é obrigatória.");
        Assert.isTrue(chave.endsWith("." + fotografia.extensao()),
                "A extensão da chave deve corresponder à fotografia validada.");
        try {
            client.post().uri("/storage/v1/object/{bucket}/{chave}", bucket, chave)
                    .contentType(MediaType.parseMediaType(fotografia.tipoConteudo()))
                    .header("x-upsert", "false")
                    .body(fotografia.conteudo())
                    .exchange((requisicao, resposta) -> {
                        if (!resposta.getStatusCode().is2xxSuccessful()) {
                            throw new FotografiaStorageException();
                        }
                        return null;
                    });
        } catch (RestClientException exception) {
            throw new FotografiaStorageException();
        }
    }

    @Override
    public byte[] ler(String chave) {
        validarChave(chave);
        try {
            return client.get().uri("/storage/v1/object/authenticated/{bucket}/{chave}", bucket, chave)
                    .exchange((requisicao, resposta) -> {
                        if (resposta.getStatusCode().value() == 404) {
                            throw new RecursoNaoEncontradoException("Fotografia não encontrada.");
                        }
                        if (!resposta.getStatusCode().is2xxSuccessful()) {
                            throw new FotografiaStorageException();
                        }
                        byte[] conteudo = resposta.getBody().readNBytes(FotografiaValidada.TAMANHO_MAXIMO_BYTES + 1);
                        if (conteudo.length == 0 || conteudo.length > FotografiaValidada.TAMANHO_MAXIMO_BYTES) {
                            throw new FotografiaStorageException();
                        }
                        return conteudo;
                    });
        } catch (RestClientException exception) {
            throw new FotografiaStorageException();
        }
    }

    @Override
    public void excluir(String chave) {
        validarChave(chave);
        try {
            client.method(HttpMethod.DELETE).uri("/storage/v1/object/{bucket}", bucket)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefixes", List.of(chave)))
                    .exchange((requisicao, resposta) -> {
                        if (!resposta.getStatusCode().is2xxSuccessful()) {
                            throw new FotografiaStorageException();
                        }
                        return null;
                    });
        } catch (RestClientException exception) {
            throw new FotografiaStorageException();
        }
    }

    private void validarChave(String chave) {
        Assert.isTrue(chave != null && CHAVE_VALIDA.matcher(chave).matches(),
                "A chave da fotografia deve conter somente um UUID e a extensão JPEG ou PNG.");
    }
}
