package app.zelar.ocorrencia.entity;

import app.zelar.ocorrencia.dto.FotografiaValidada;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnexoTest {

    private final LocalDateTime instante = LocalDateTime.of(2026, 10, 26, 12, 0);
    private final FotografiaValidada foto = new FotografiaValidada(new byte[]{1}, "image/png", "png", 1, 1);

    @Test
    void deveReservarObjetoComChaveGeradaSemNomeFornecidoPeloCliente() {
        UUID ocorrencia = UUID.randomUUID();
        var anexo = new Anexo(ocorrencia, "fotografias", foto, instante);
        assertEquals(EstadoUpload.PENDENTE, anexo.getEstadoUpload());
        assertEquals(ocorrencia, anexo.getOcorrenciaId());
        assertTrue(anexo.getChaveObjeto().matches("[0-9a-f-]{36}\\.png"));
        assertEquals(instante, anexo.getDataAtualizacao());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void deveRecusarBucketAusente(String bucket) {
        assertThrows(IllegalArgumentException.class, () -> new Anexo(UUID.randomUUID(), bucket, foto, instante));
    }

    @Test
    void deveExigirOcorrenciaFotografiaEInstante() {
        assertThrows(NullPointerException.class, () -> new Anexo(null, "fotografias", foto, instante));
        assertThrows(NullPointerException.class, () -> new Anexo(UUID.randomUUID(), "fotografias", null, instante));
        assertThrows(NullPointerException.class, () -> new Anexo(UUID.randomUUID(), "fotografias", foto, null));
    }

    @Test
    void fotografiaDisponivelNaoPodeSerCompensadaOuConfirmadaNovamente() {
        var anexo = new Anexo(UUID.randomUUID(), "fotografias", foto, instante);
        anexo.disponibilizar(instante.plusSeconds(1));
        assertThrows(IllegalStateException.class, () -> anexo.aguardarLimpeza(instante.plusMinutes(1)));
        assertThrows(IllegalStateException.class, () -> anexo.disponibilizar(instante.plusMinutes(1)));
        assertEquals(EstadoUpload.DISPONIVEL, anexo.getEstadoUpload());
    }

    @Test
    void reservaEmLimpezaNaoPodePublicarConteudoComoDisponivel() {
        var anexo = new Anexo(UUID.randomUUID(), "fotografias", foto, instante);
        anexo.aguardarLimpeza(instante.plusSeconds(1));
        assertThrows(IllegalStateException.class, () -> anexo.disponibilizar(instante.plusMinutes(1)));
        assertEquals(EstadoUpload.LIMPEZA_PENDENTE, anexo.getEstadoUpload());
    }

    @Test
    void falhaPorInstanteNuloNaoPodeAlterarEstadoDaReserva() {
        var anexo = new Anexo(UUID.randomUUID(), "fotografias", foto, instante);
        assertThrows(NullPointerException.class, () -> anexo.disponibilizar(null));
        assertThrows(NullPointerException.class, () -> anexo.aguardarLimpeza(null));
        assertEquals(EstadoUpload.PENDENTE, anexo.getEstadoUpload());
    }
}
