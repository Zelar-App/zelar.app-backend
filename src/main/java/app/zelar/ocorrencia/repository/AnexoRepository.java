package app.zelar.ocorrencia.repository;

import app.zelar.ocorrencia.entity.Anexo;
import app.zelar.ocorrencia.entity.EstadoUpload;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnexoRepository extends JpaRepository<Anexo, UUID> {

    boolean existsByOcorrenciaId(UUID ocorrenciaId);

    boolean existsByOcorrenciaIdAndEstadoUpload(UUID ocorrenciaId, EstadoUpload estado);

    Optional<Anexo> findByOcorrenciaIdAndEstadoUpload(UUID ocorrenciaId, EstadoUpload estado);

    List<Anexo> findByOcorrenciaIdInAndEstadoUpload(Collection<UUID> ids, EstadoUpload estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Anexo a where a.id = :id")
    Optional<Anexo> buscarParaAtualizacao(@Param("id") UUID id);

    @Query("select a.id from Anexo a where a.estadoUpload <> :disponivel and a.dataAtualizacao <= :corte order by a.dataAtualizacao, a.id")
    List<UUID> buscarInterrompidos(@Param("disponivel") EstadoUpload disponivel,
                                 @Param("corte") LocalDateTime corte, Pageable pagina);
}
