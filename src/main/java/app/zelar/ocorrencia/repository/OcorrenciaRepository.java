package app.zelar.ocorrencia.repository;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ocorrencia from Ocorrencia ocorrencia where ocorrencia.id = :id")
    Optional<Ocorrencia> findByIdParaAtualizacao(@Param("id") UUID id);
}
