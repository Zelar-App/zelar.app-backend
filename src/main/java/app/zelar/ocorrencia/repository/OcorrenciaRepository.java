package app.zelar.ocorrencia.repository;

import app.zelar.ocorrencia.dto.OcorrenciaIndicadoresResponse;
import app.zelar.ocorrencia.entity.Ocorrencia;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, UUID>,
        JpaSpecificationExecutor<Ocorrencia> {

    @Query("""
            select new app.zelar.ocorrencia.dto.OcorrenciaIndicadoresResponse(
                coalesce(sum(case when o.status = app.zelar.ocorrencia.entity.StatusOcorrencia.ENVIADA
                    then 1L else 0L end), 0L),
                coalesce(sum(case when o.status = app.zelar.ocorrencia.entity.StatusOcorrencia.EM_ANALISE
                    then 1L else 0L end), 0L),
                coalesce(sum(case when o.status = app.zelar.ocorrencia.entity.StatusOcorrencia.RESOLVIDA
                    then 1L else 0L end), 0L)
            )
            from Ocorrencia o
            """)
    OcorrenciaIndicadoresResponse contarPorStatus();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ocorrencia from Ocorrencia ocorrencia where ocorrencia.id = :id")
    Optional<Ocorrencia> findByIdParaAtualizacao(@Param("id") UUID id);
}
