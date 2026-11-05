package app.zelar.ocorrencia.service;

import app.zelar.ocorrencia.dto.FotografiaValidada;
import app.zelar.ocorrencia.entity.Anexo;
import app.zelar.ocorrencia.entity.EstadoUpload;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.exception.FotografiaConflitoException;
import app.zelar.ocorrencia.repository.AnexoRepository;
import app.zelar.ocorrencia.repository.OcorrenciaRepository;
import app.zelar.shared.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FotografiaPersistenciaServiceTest {

    @Mock private AnexoRepository anexos;
    @Mock private OcorrenciaRepository ocorrencias;
    private FotografiaPersistenciaService service;
    private final UUID ocorrenciaId = UUID.randomUUID();
    private final UUID anexoId = UUID.randomUUID();
    private final LocalDateTime agora = LocalDateTime.of(2026, 11, 5, 18, 0);
    private final FotografiaValidada foto = new FotografiaValidada(new byte[]{1}, "image/png", "png", 10, 8);

    @BeforeEach
    void configurar() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-05T18:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        service = new FotografiaPersistenciaService(anexos, ocorrencias, clock);
    }

    @Test
    void deveReservarAposBloquearOcorrenciaComInstanteUtc() {
        when(ocorrencias.findByIdParaAtualizacao(ocorrenciaId)).thenReturn(Optional.of(mock(Ocorrencia.class)));
        when(anexos.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Anexo reserva = service.reservar(ocorrenciaId, "ocorrencias-fotografias", foto);

        assertEquals(ocorrenciaId, reserva.getOcorrenciaId());
        assertEquals(EstadoUpload.PENDENTE, reserva.getEstadoUpload());
        assertEquals(agora, reserva.getDataAtualizacao());
        var ordem = inOrder(ocorrencias, anexos);
        ordem.verify(ocorrencias).findByIdParaAtualizacao(ocorrenciaId);
        ordem.verify(anexos).existsByOcorrenciaId(ocorrenciaId);
        ordem.verify(anexos).saveAndFlush(reserva);
    }

    @Test
    void deveRecusarReservaDeOcorrenciaAusenteAntesDeConsultarAnexos() {
        when(ocorrencias.findByIdParaAtualizacao(ocorrenciaId)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.reservar(ocorrenciaId, "bucket", foto));
        verifyNoInteractions(anexos);
    }

    @Test
    void deveRecusarReservaDuplicadaSemPersistirNovoVinculo() {
        when(ocorrencias.findByIdParaAtualizacao(ocorrenciaId)).thenReturn(Optional.of(mock(Ocorrencia.class)));
        when(anexos.existsByOcorrenciaId(ocorrenciaId)).thenReturn(true);
        assertThrows(FotografiaConflitoException.class, () -> service.reservar(ocorrenciaId, "bucket", foto));
        verify(anexos, never()).saveAndFlush(any());
    }

    @Test
    void deveDisponibilizarReservaPendenteAtualizandoInstanteUtc() {
        Anexo anexo = anexo(EstadoUpload.PENDENTE, agora.minusMinutes(20));
        when(anexos.buscarParaAtualizacao(anexoId)).thenReturn(Optional.of(anexo));
        assertSame(anexo, service.disponibilizar(anexoId));
        assertEquals(EstadoUpload.DISPONIVEL, anexo.getEstadoUpload());
        assertEquals(agora, anexo.getDataAtualizacao());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoUpload.class, names = {"DISPONIVEL", "LIMPEZA_PENDENTE"})
    void deveRecusarPublicacaoForaDoEstadoPendente(EstadoUpload estado) {
        Anexo anexo = anexo(estado, agora.minusMinutes(20));
        when(anexos.buscarParaAtualizacao(anexoId)).thenReturn(Optional.of(anexo));
        assertThrows(FotografiaConflitoException.class, () -> service.disponibilizar(anexoId));
        assertEquals(estado, anexo.getEstadoUpload());
        assertEquals(agora.minusMinutes(20), anexo.getDataAtualizacao());
    }

    @Test
    void deveInformarReservaAusenteNaPublicacao() {
        assertThrows(RecursoNaoEncontradoException.class, () -> service.disponibilizar(anexoId));
    }

    @ParameterizedTest
    @EnumSource(EstadoUpload.class)
    void deveSinalizarFalhaSomenteParaReservaPendente(EstadoUpload estado) {
        LocalDateTime anterior = agora.minusMinutes(20);
        Anexo anexo = anexo(estado, anterior);
        when(anexos.buscarParaAtualizacao(anexoId)).thenReturn(Optional.of(anexo));
        service.sinalizarFalha(anexoId);
        assertEquals(estado == EstadoUpload.PENDENTE ? EstadoUpload.LIMPEZA_PENDENTE : estado,
                anexo.getEstadoUpload());
        assertEquals(estado == EstadoUpload.PENDENTE ? agora : anterior, anexo.getDataAtualizacao());
    }

    @Test
    void deveIgnorarSinalizacaoDeReservaAusente() {
        assertDoesNotThrow(() -> service.sinalizarFalha(anexoId));
        verify(anexos, never()).delete(any());
    }

    @Test
    void deveConsultarApenasFotografiaDisponivel() {
        Anexo anexo = anexo(EstadoUpload.DISPONIVEL, agora);
        when(anexos.findByOcorrenciaIdAndEstadoUpload(ocorrenciaId, EstadoUpload.DISPONIVEL))
                .thenReturn(Optional.of(anexo));
        assertSame(anexo, service.consultarDisponivel(ocorrenciaId));
    }

    @Test
    void deveInformarFotografiaIndisponivelSemGravarAlteracoes() {
        assertThrows(RecursoNaoEncontradoException.class, () -> service.consultarDisponivel(ocorrenciaId));
        verify(anexos, never()).saveAndFlush(any());
    }

    @Test
    void deveLimitarLoteDeRecuperacaoAVinteRegistros() {
        LocalDateTime corte = agora.minusMinutes(10);
        when(anexos.buscarInterrompidos(EstadoUpload.DISPONIVEL, corte, PageRequest.of(0, 20)))
                .thenReturn(List.of(anexoId));
        assertEquals(List.of(anexoId), service.buscarInterrompidos(corte));
    }

    @Test
    void deveIgnorarReservaRemovidaAntesDaReivindicacao() {
        assertTrue(service.reivindicarLimpeza(anexoId, agora.minusMinutes(10)).isEmpty());
    }

    @Test
    void devePreservarFotoDisponivelMesmoQuandoAntiga() {
        Anexo anexo = anexo(EstadoUpload.DISPONIVEL, agora.minusHours(1));
        when(anexos.buscarParaAtualizacao(anexoId)).thenReturn(Optional.of(anexo));
        assertTrue(service.reivindicarLimpeza(anexoId, agora.minusMinutes(10)).isEmpty());
        assertEquals(EstadoUpload.DISPONIVEL, anexo.getEstadoUpload());
    }

    @Test
    void devePreservarReservaUmNanossegundoMaisRecenteQueOCorte() {
        LocalDateTime corte = agora.minusMinutes(10);
        Anexo anexo = anexo(EstadoUpload.PENDENTE, corte.plusNanos(1));
        when(anexos.buscarParaAtualizacao(anexoId)).thenReturn(Optional.of(anexo));
        assertTrue(service.reivindicarLimpeza(anexoId, corte).isEmpty());
        assertEquals(corte.plusNanos(1), anexo.getDataAtualizacao());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoUpload.class, names = {"PENDENTE", "LIMPEZA_PENDENTE"})
    void deveReivindicarReservaExatamenteNoLimiteDaCarencia(EstadoUpload estado) {
        LocalDateTime corte = agora.minusMinutes(10);
        Anexo anexo = anexo(estado, corte);
        when(anexos.buscarParaAtualizacao(anexoId)).thenReturn(Optional.of(anexo));
        assertSame(anexo, service.reivindicarLimpeza(anexoId, corte).orElseThrow());
        assertEquals(EstadoUpload.LIMPEZA_PENDENTE, anexo.getEstadoUpload());
        assertEquals(agora, anexo.getDataAtualizacao());
    }

    @ParameterizedTest
    @EnumSource(EstadoUpload.class)
    void deveRemoverMetadadoSomenteEmLimpezaPendente(EstadoUpload estado) {
        Anexo anexo = anexo(estado, agora);
        when(anexos.buscarParaAtualizacao(anexoId)).thenReturn(Optional.of(anexo));
        service.confirmarLimpeza(anexoId);
        if (estado == EstadoUpload.LIMPEZA_PENDENTE) {
            verify(anexos).delete(anexo);
        } else {
            verify(anexos, never()).delete(any());
        }
    }

    @Test
    void deveIgnorarLimpezaJaConcluidaSemExcluirOutroRegistro() {
        service.confirmarLimpeza(anexoId);
        verify(anexos, never()).delete(any());
    }

    private Anexo anexo(EstadoUpload estado, LocalDateTime instante) {
        Anexo anexo = new Anexo(ocorrenciaId, "ocorrencias-fotografias", foto, instante);
        if (estado == EstadoUpload.DISPONIVEL) {
            anexo.disponibilizar(instante);
        } else if (estado == EstadoUpload.LIMPEZA_PENDENTE) {
            anexo.aguardarLimpeza(instante);
        }
        return anexo;
    }
}
