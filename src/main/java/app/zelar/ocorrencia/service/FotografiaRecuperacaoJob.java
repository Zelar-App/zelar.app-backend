package app.zelar.ocorrencia.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.fotografias.habilitado", havingValue = "true")
public class FotografiaRecuperacaoJob {

    private final FotografiaService fotografias;

    public FotografiaRecuperacaoJob(FotografiaService fotografias) {
        this.fotografias = fotografias;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void recuperar() {
        fotografias.recuperarPendentes();
    }
}
