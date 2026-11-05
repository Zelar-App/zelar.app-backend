package app.zelar.ocorrencia.service;

import app.zelar.ocorrencia.dto.FotografiaValidada;
import app.zelar.ocorrencia.entity.Anexo;
import app.zelar.ocorrencia.exception.FotografiaConflitoException;
import app.zelar.ocorrencia.exception.FotografiaInvalidaException;
import app.zelar.ocorrencia.exception.FotografiaStorageException;
import app.zelar.ocorrencia.storage.FotografiaStorage;
import app.zelar.shared.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

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
class FotografiaServiceTest {

    @Mock private FotografiaAcessoService acesso;
    @Mock private ValidadorFotografia validador;
    @Mock private FotografiaPersistenciaService persistencia;
    @Mock private ObjectProvider<FotografiaStorage> provider;
    @Mock private FotografiaStorage storage;
    @Mock private MultipartFile arquivo;
    private FotografiaService service;
    private final UUID autorId = UUID.randomUUID();
    private final UUID ocorrenciaId = UUID.randomUUID();
    private final UUID anexoId = UUID.randomUUID();
    private final String bucket = "ocorrencias-fotografias";
    private final LocalDateTime agora = LocalDateTime.of(2026, 11, 5, 18, 0);
    private final LocalDateTime corte = agora.minusMinutes(10);
    private final FotografiaValidada foto = new FotografiaValidada(new byte[]{1, 2}, "image/png", "png", 10, 8);

    @BeforeEach
    void configurar() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-05T18:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        service = new FotografiaService(acesso, validador, persistencia, provider, clock, bucket);
    }

    @Test
    void deveAutorizarValidarReservarEnviarEConfirmarNestaOrdem() {
        Anexo anexo = prepararEnvio();
        when(persistencia.disponibilizar(anexoId)).thenReturn(anexo);

        var resposta = service.enviar(autorId, ocorrenciaId, arquivo);

        assertEquals(anexoId, resposta.id());
        assertEquals("image/png", resposta.tipoConteudo());
        assertEquals(2, resposta.tamanhoBytes());
        assertEquals(10, resposta.largura());
        assertEquals(8, resposta.altura());
        var ordem = inOrder(acesso, validador, provider, persistencia, storage);
        ordem.verify(acesso).autorizarEnvio(autorId, ocorrenciaId);
        ordem.verify(validador).validar(arquivo);
        ordem.verify(provider).getIfAvailable();
        ordem.verify(persistencia).reservar(ocorrenciaId, bucket, foto);
        ordem.verify(storage).salvar(anexo.getChaveObjeto(), foto);
        ordem.verify(persistencia).disponibilizar(anexoId);
        verify(persistencia, never()).sinalizarFalha(any());
    }

    @Test
    void deveRecusarEnvioSemPermissaoAntesDeValidarOuReservar() {
        doThrow(new AccessDeniedException("Negado")).when(acesso).autorizarEnvio(autorId, ocorrenciaId);
        assertThrows(AccessDeniedException.class, () -> service.enviar(autorId, ocorrenciaId, arquivo));
        verifyNoInteractions(validador, persistencia, provider, storage);
    }

    @Test
    void deveRecusarArquivoInvalidoAntesDeReservarOuAcessarStorage() {
        when(validador.validar(arquivo)).thenThrow(new FotografiaInvalidaException("Inválida"));
        assertThrows(FotografiaInvalidaException.class, () -> service.enviar(autorId, ocorrenciaId, arquivo));
        verifyNoInteractions(persistencia, provider, storage);
    }

    @Test
    void deveRecusarStorageDesabilitadoSemReservarVinculo() {
        when(validador.validar(arquivo)).thenReturn(foto);
        assertThrows(FotografiaStorageException.class, () -> service.enviar(autorId, ocorrenciaId, arquivo));
        verifyNoInteractions(persistencia, storage);
    }

    @Test
    void devePreservarConflitoDaReservaSemEnviarOuCompensarOutroUpload() {
        when(validador.validar(arquivo)).thenReturn(foto);
        when(provider.getIfAvailable()).thenReturn(storage);
        when(persistencia.reservar(ocorrenciaId, bucket, foto)).thenThrow(new FotografiaConflitoException());
        assertThrows(FotografiaConflitoException.class, () -> service.enviar(autorId, ocorrenciaId, arquivo));
        verifyNoInteractions(storage);
        verify(persistencia, never()).sinalizarFalha(any());
    }

    @Test
    void deveSinalizarFalhaDeUploadSemPublicarReserva() {
        Anexo anexo = prepararEnvio();
        doThrow(new IllegalStateException("Falha externa")).when(storage).salvar(anexo.getChaveObjeto(), foto);
        assertThrows(FotografiaStorageException.class, () -> service.enviar(autorId, ocorrenciaId, arquivo));
        verify(persistencia).sinalizarFalha(anexoId);
        verify(persistencia, never()).disponibilizar(any());
    }

    @Test
    void deveSinalizarFalhaNaConfirmacaoDeUploadJaEnviado() {
        prepararEnvio();
        when(persistencia.disponibilizar(anexoId)).thenThrow(new IllegalStateException("Falha no banco"));
        assertThrows(FotografiaStorageException.class, () -> service.enviar(autorId, ocorrenciaId, arquivo));
        verify(persistencia).sinalizarFalha(anexoId);
    }

    @Test
    void deveManterErroPadronizadoMesmoSeSinalizacaoDaFalhaTambemFalhar() {
        Anexo anexo = prepararEnvio();
        doThrow(new IllegalStateException("Falha externa")).when(storage).salvar(anexo.getChaveObjeto(), foto);
        doThrow(new IllegalStateException("Falha no banco")).when(persistencia).sinalizarFalha(anexoId);
        assertThrows(FotografiaStorageException.class, () -> service.enviar(autorId, ocorrenciaId, arquivo));
        verify(persistencia, never()).disponibilizar(any());
    }

    @Test
    void deveAutorizarConsultaAntesDeLerBytesDoBucketConfigurado() {
        Anexo anexo = anexo(bucket);
        when(persistencia.consultarDisponivel(ocorrenciaId)).thenReturn(anexo);
        when(provider.getIfAvailable()).thenReturn(storage);
        when(storage.ler(anexo.getChaveObjeto())).thenReturn(new byte[]{1, 2});
        var resposta = service.consultar(autorId, ocorrenciaId);
        assertEquals("image/png", resposta.tipoConteudo());
        assertArrayEquals(new byte[]{1, 2}, resposta.conteudo());
        var ordem = inOrder(acesso, persistencia, storage);
        ordem.verify(acesso).autorizarConsulta(autorId, ocorrenciaId);
        ordem.verify(persistencia).consultarDisponivel(ocorrenciaId);
        ordem.verify(storage).ler(anexo.getChaveObjeto());
    }

    @Test
    void deveRecusarConsultaNaoAutorizadaSemConsultarAnexo() {
        doThrow(new RecursoNaoEncontradoException("Ausente")).when(acesso).autorizarConsulta(autorId, ocorrenciaId);
        assertThrows(RecursoNaoEncontradoException.class, () -> service.consultar(autorId, ocorrenciaId));
        verifyNoInteractions(persistencia, provider, storage);
    }

    @Test
    void deveRecusarFotografiaDeOutroBucketSemLerObjeto() {
        when(persistencia.consultarDisponivel(ocorrenciaId)).thenReturn(anexo("outro-bucket"));
        assertThrows(FotografiaStorageException.class, () -> service.consultar(autorId, ocorrenciaId));
        verifyNoInteractions(provider, storage);
    }

    @Test
    void devePreservarNaoEncontradoQuandoFotoNaoEstaDisponivel() {
        when(persistencia.consultarDisponivel(ocorrenciaId)).thenThrow(new RecursoNaoEncontradoException("Ausente"));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.consultar(autorId, ocorrenciaId));
        verifyNoInteractions(provider, storage);
    }

    @Test
    void deveCalcularCorteEmUtcEIgnorarReservaNaoReivindicada() {
        when(persistencia.buscarInterrompidos(corte)).thenReturn(List.of(anexoId));
        when(persistencia.reivindicarLimpeza(anexoId, corte)).thenReturn(Optional.empty());
        service.recuperarPendentes();
        verifyNoInteractions(provider, storage);
        verify(persistencia, never()).confirmarLimpeza(any());
    }

    @Test
    void deveExcluirObjetoAntesDeConfirmarLimpezaDoMetadado() {
        Anexo anexo = prepararRecuperacao();
        service.recuperarPendentes();
        var ordem = inOrder(storage, persistencia);
        ordem.verify(storage).excluir(anexo.getChaveObjeto());
        ordem.verify(persistencia).confirmarLimpeza(anexoId);
    }

    @Test
    void devePreservarVinculoSeExclusaoDoObjetoFalhar() {
        Anexo anexo = prepararRecuperacao();
        doThrow(new FotografiaStorageException()).when(storage).excluir(anexo.getChaveObjeto());
        assertDoesNotThrow(service::recuperarPendentes);
        verify(persistencia, never()).confirmarLimpeza(any());
    }

    @Test
    void deveContinuarLoteAposFalhaDeUmaReserva() {
        UUID proximoId = UUID.randomUUID();
        Anexo proximo = anexo(bucket);
        when(persistencia.buscarInterrompidos(corte)).thenReturn(List.of(anexoId, proximoId));
        when(persistencia.reivindicarLimpeza(anexoId, corte)).thenThrow(new IllegalStateException("Falha no banco"));
        when(persistencia.reivindicarLimpeza(proximoId, corte)).thenReturn(Optional.of(proximo));
        when(provider.getIfAvailable()).thenReturn(storage);
        service.recuperarPendentes();
        verify(storage).excluir(proximo.getChaveObjeto());
        verify(persistencia).confirmarLimpeza(proximoId);
        verify(persistencia, never()).confirmarLimpeza(anexoId);
    }

    @Test
    void devePreservarReservaDeOutroBucketDuranteRecuperacao() {
        when(persistencia.buscarInterrompidos(corte)).thenReturn(List.of(anexoId));
        when(persistencia.reivindicarLimpeza(anexoId, corte)).thenReturn(Optional.of(anexo("outro-bucket")));
        service.recuperarPendentes();
        verifyNoInteractions(provider, storage);
        verify(persistencia, never()).confirmarLimpeza(any());
    }

    @Test
    void deveManterRecuperacaoRepetivelSeConfirmacaoDaLimpezaFalhar() {
        Anexo anexo = prepararRecuperacao();
        doThrow(new IllegalStateException("Falha no banco")).when(persistencia).confirmarLimpeza(anexoId);
        assertDoesNotThrow(service::recuperarPendentes);
        verify(storage).excluir(anexo.getChaveObjeto());
        verify(persistencia).confirmarLimpeza(anexoId);
    }

    private Anexo prepararEnvio() {
        Anexo anexo = anexo(bucket);
        when(validador.validar(arquivo)).thenReturn(foto);
        when(provider.getIfAvailable()).thenReturn(storage);
        when(persistencia.reservar(ocorrenciaId, bucket, foto)).thenReturn(anexo);
        return anexo;
    }

    private Anexo prepararRecuperacao() {
        Anexo anexo = anexo(bucket);
        when(persistencia.buscarInterrompidos(corte)).thenReturn(List.of(anexoId));
        when(persistencia.reivindicarLimpeza(anexoId, corte)).thenReturn(Optional.of(anexo));
        when(provider.getIfAvailable()).thenReturn(storage);
        return anexo;
    }

    private Anexo anexo(String nomeBucket) {
        Anexo anexo = new Anexo(ocorrenciaId, nomeBucket, foto, agora.minusMinutes(20));
        ReflectionTestUtils.setField(anexo, "id", anexoId);
        return anexo;
    }
}
