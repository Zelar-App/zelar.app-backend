package app.zelar.ocorrencia.service;

import app.zelar.ocorrencia.dto.FotografiaValidada;
import app.zelar.ocorrencia.entity.Anexo;
import app.zelar.ocorrencia.entity.EstadoUpload;
import app.zelar.ocorrencia.exception.FotografiaConflitoException;
import app.zelar.ocorrencia.repository.AnexoRepository;
import app.zelar.ocorrencia.repository.OcorrenciaRepository;
import app.zelar.shared.exception.RecursoNaoEncontradoException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FotografiaPersistenciaService {

    private final AnexoRepository anexos;
    private final OcorrenciaRepository ocorrencias;
    private final Clock clock;

    public FotografiaPersistenciaService(AnexoRepository anexos, OcorrenciaRepository ocorrencias, Clock clock) {
        this.anexos = anexos;
        this.ocorrencias = ocorrencias;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Anexo reservar(UUID ocorrenciaId, String bucket, FotografiaValidada fotografia) {
        ocorrencias.findByIdParaAtualizacao(ocorrenciaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ocorrência não encontrada."));
        if (anexos.existsByOcorrenciaId(ocorrenciaId)) {
            throw new FotografiaConflitoException();
        }
        return anexos.saveAndFlush(new Anexo(ocorrenciaId, bucket, fotografia, instante()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Anexo disponibilizar(UUID id) {
        Anexo anexo = buscarBloqueado(id);
        if (anexo.getEstadoUpload() != EstadoUpload.PENDENTE) {
            throw new FotografiaConflitoException();
        }
        anexo.disponibilizar(instante());
        return anexo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sinalizarFalha(UUID id) {
        anexos.buscarParaAtualizacao(id).filter(a -> a.getEstadoUpload() == EstadoUpload.PENDENTE)
                .ifPresent(a -> a.aguardarLimpeza(instante()));
    }

    @Transactional(readOnly = true)
    public Anexo consultarDisponivel(UUID ocorrenciaId) {
        return anexos.findByOcorrenciaIdAndEstadoUpload(ocorrenciaId, EstadoUpload.DISPONIVEL)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fotografia não encontrada."));
    }

    @Transactional(readOnly = true)
    public List<UUID> buscarInterrompidos(LocalDateTime corte) {
        return anexos.buscarInterrompidos(EstadoUpload.DISPONIVEL, corte, PageRequest.of(0, 20));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Anexo> reivindicarLimpeza(UUID id, LocalDateTime corte) {
        Optional<Anexo> registro = anexos.buscarParaAtualizacao(id);
        if (registro.isEmpty() || registro.get().getEstadoUpload() == EstadoUpload.DISPONIVEL
                || registro.get().getDataAtualizacao().isAfter(corte)) {
            return Optional.empty();
        }
        registro.get().aguardarLimpeza(instante());
        return registro;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmarLimpeza(UUID id) {
        anexos.buscarParaAtualizacao(id).filter(a -> a.getEstadoUpload() == EstadoUpload.LIMPEZA_PENDENTE)
                .ifPresent(anexos::delete);
    }

    private Anexo buscarBloqueado(UUID id) {
        return anexos.buscarParaAtualizacao(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fotografia não encontrada."));
    }

    private LocalDateTime instante() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
