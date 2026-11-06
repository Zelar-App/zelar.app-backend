package app.zelar.ocorrencia.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FotografiaRecuperacaoJobTest {

    private final FotografiaService fotografias = mock(FotografiaService.class);
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(FotografiaService.class, () -> fotografias)
            .withUserConfiguration(FotografiaRecuperacaoJob.class);

    @Test
    void deveManterRecuperacaoDesativadaQuandoConfiguracaoEstiverAusente() {
        contexto.run(application -> {
            assertNull(application.getStartupFailure());
            assertTrue(application.getBeansOfType(FotografiaRecuperacaoJob.class).isEmpty());
            verifyNoInteractions(fotografias);
        });
    }

    @Test
    void deveManterRecuperacaoDesativadaQuandoFotosEstiveremDesabilitadas() {
        contexto.withPropertyValues("app.fotografias.habilitado=false").run(application -> {
            assertNull(application.getStartupFailure());
            assertTrue(application.getBeansOfType(FotografiaRecuperacaoJob.class).isEmpty());
            verifyNoInteractions(fotografias);
        });
    }

    @Test
    void deveDisponibilizarJobFuncionalSomenteComFotosHabilitadas() {
        contexto.withPropertyValues("app.fotografias.habilitado=true").run(application -> {
            assertNull(application.getStartupFailure());
            assertEquals(1, application.getBeansOfType(FotografiaRecuperacaoJob.class).size());
            application.getBean(FotografiaRecuperacaoJob.class).recuperar();
            verify(fotografias).recuperarPendentes();
            verifyNoMoreInteractions(fotografias);
        });
    }
}
