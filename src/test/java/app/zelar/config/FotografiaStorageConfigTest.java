package app.zelar.config;

import app.zelar.ocorrencia.storage.FotografiaStorage;
import app.zelar.ocorrencia.storage.SupabaseFotografiaStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class FotografiaStorageConfigTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(FotografiaStorageConfig.class);

    @Test
    void deveIniciarSemCredenciaisQuandoFotosNaoForemHabilitadas() {
        contexto.run(application -> {
            assertNull(application.getStartupFailure());
            assertTrue(application.getBeansOfType(FotografiaStorage.class).isEmpty());
        });
    }

    @Test
    void deveIgnorarConfiguracaoIncompletaQuandoFotosEstiveremDesabilitadas() {
        contexto.withPropertyValues("app.fotografias.habilitado=false", "app.fotografias.url=invalida")
                .run(application -> {
                    assertNull(application.getStartupFailure());
                    assertTrue(application.getBeansOfType(FotografiaStorage.class).isEmpty());
                });
    }

    @Test
    void deveCriarAdaptadorSomenteComFotosHabilitadasEConfiguracaoValida() {
        configurado().run(application -> {
            assertNull(application.getStartupFailure());
            assertEquals(1, application.getBeansOfType(FotografiaStorage.class).size());
            assertInstanceOf(SupabaseFotografiaStorage.class, application.getBean(FotografiaStorage.class));
        });
    }

    @Test
    void deveRecusarHabilitacaoSemCredenciaisEmVezDeCriarClienteInvalido() {
        contexto.withPropertyValues("app.fotografias.habilitado=true").run(application -> {
            assertNotNull(application.getStartupFailure());
            assertInstanceOf(IllegalArgumentException.class, causaRaiz(application.getStartupFailure()));
        });
    }

    @ParameterizedTest
    @CsvSource({"app.fotografias.url,http://example.supabase.co",
            "app.fotografias.secret-key,sb_publishable_invalida",
            "app.fotografias.bucket,Bucket-Invalido"})
    void deveRecusarConfiguracaoInvalidaAntesDeQualquerChamadaDeRede(String chave, String valor) {
        configurado().withPropertyValues(chave + "=" + valor).run(application -> {
            assertNotNull(application.getStartupFailure());
            assertInstanceOf(IllegalArgumentException.class, causaRaiz(application.getStartupFailure()));
        });
    }

    private ApplicationContextRunner configurado() {
        return contexto.withPropertyValues("app.fotografias.habilitado=true",
                "app.fotografias.url=https://example.supabase.co",
                "app.fotografias.secret-key=sb_secret_ficticio_apenas_teste");
    }

    private Throwable causaRaiz(Throwable falha) {
        while (falha.getCause() != null) {
            falha = falha.getCause();
        }
        return falha;
    }
}
