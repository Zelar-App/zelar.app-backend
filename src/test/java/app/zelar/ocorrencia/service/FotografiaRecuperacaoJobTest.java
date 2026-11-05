package app.zelar.ocorrencia.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class FotografiaRecuperacaoJobTest {

    @Test
    void deveDelegarRecuperacaoAoServicoEmCadaExecucao() {
        FotografiaService fotografias = mock(FotografiaService.class);
        FotografiaRecuperacaoJob job = new FotografiaRecuperacaoJob(fotografias);

        job.recuperar();
        job.recuperar();

        verify(fotografias, times(2)).recuperarPendentes();
        verifyNoMoreInteractions(fotografias);
    }
}
