package app.zelar.ocorrencia.repository;

import app.zelar.ocorrencia.entity.Ocorrencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, UUID> {
}
