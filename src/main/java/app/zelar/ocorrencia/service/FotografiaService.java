package app.zelar.ocorrencia.service;

import app.zelar.ocorrencia.dto.FotografiaConteudo;
import app.zelar.ocorrencia.dto.FotografiaResponse;
import app.zelar.ocorrencia.entity.Anexo;
import app.zelar.ocorrencia.exception.FotografiaStorageException;
import app.zelar.ocorrencia.storage.FotografiaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class FotografiaService {

    private static final Logger log = LoggerFactory.getLogger(FotografiaService.class);
    private final FotografiaAcessoService acesso;
    private final ValidadorFotografia validador;
    private final FotografiaPersistenciaService persistencia;
    private final ObjectProvider<FotografiaStorage> storageProvider;
    private final Clock clock;
    private final String bucket;

    public FotografiaService(FotografiaAcessoService acesso, ValidadorFotografia validador,
                             FotografiaPersistenciaService persistencia, ObjectProvider<FotografiaStorage> storageProvider,
                             Clock clock, @Value("${app.fotografias.bucket:ocorrencias-fotografias}") String bucket) {
        this.acesso = acesso;
        this.validador = validador;
        this.persistencia = persistencia;
        this.storageProvider = storageProvider;
        this.clock = clock;
        this.bucket = bucket;
    }

    public FotografiaResponse enviar(UUID usuarioId, UUID ocorrenciaId, MultipartFile arquivo) {
        acesso.autorizarEnvio(usuarioId, ocorrenciaId);
        var fotografia = validador.validar(arquivo);
        FotografiaStorage storage = storage();
        Anexo reserva = persistencia.reservar(ocorrenciaId, bucket, fotografia);
        try {
            storage.salvar(reserva.getChaveObjeto(), fotografia);
            return FotografiaResponse.from(persistencia.disponibilizar(reserva.getId()));
        } catch (RuntimeException exception) {
            try {
                persistencia.sinalizarFalha(reserva.getId());
            } catch (RuntimeException falhaPersistencia) {
                log.warn("Não foi possível sinalizar o anexo {}. A reserva permanece para recuperação.", reserva.getId());
            }
            throw new FotografiaStorageException();
        }
    }

    public FotografiaConteudo consultar(UUID usuarioId, UUID ocorrenciaId) {
        acesso.autorizarConsulta(usuarioId, ocorrenciaId);
        Anexo anexo = persistencia.consultarDisponivel(ocorrenciaId);
        validarBucket(anexo);
        return new FotografiaConteudo(anexo.getTipoConteudo(), storage().ler(anexo.getChaveObjeto()));
    }

    public void recuperarPendentes() {
        LocalDateTime corte = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusMinutes(10);
        for (UUID id : persistencia.buscarInterrompidos(corte)) {
            try {
                var reserva = persistencia.reivindicarLimpeza(id, corte);
                if (reserva.isEmpty()) {
                    continue;
                }
                Anexo anexo = reserva.get();
                validarBucket(anexo);
                storage().excluir(anexo.getChaveObjeto());
                persistencia.confirmarLimpeza(id);
            } catch (RuntimeException exception) {
                log.warn("A recuperação do anexo {} permanece pendente.", id);
            }
        }
    }

    private FotografiaStorage storage() {
        FotografiaStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new FotografiaStorageException();
        }
        return storage;
    }

    private void validarBucket(Anexo anexo) {
        if (!bucket.equals(anexo.getBucket())) {
            throw new FotografiaStorageException();
        }
    }
}
