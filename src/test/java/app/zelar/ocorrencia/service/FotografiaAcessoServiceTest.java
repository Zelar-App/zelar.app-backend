package app.zelar.ocorrencia.service;

import app.zelar.ocorrencia.dto.OcorrenciaResponse;
import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import app.zelar.ocorrencia.entity.StatusOcorrencia;
import app.zelar.shared.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FotografiaAcessoServiceTest {

    @Mock
    private OcorrenciaService ocorrenciaService;
    private FotografiaAcessoService service;
    private final UUID autorId = UUID.randomUUID();
    private final UUID ocorrenciaId = UUID.randomUUID();

    @BeforeEach
    void configurar() {
        service = new FotografiaAcessoService(ocorrenciaService);
    }

    @Test
    void autorPodeEnviarFotografia() {
        when(ocorrenciaService.consultar(autorId, ocorrenciaId)).thenReturn(ocorrencia());
        assertDoesNotThrow(() -> service.autorizarEnvio(autorId, ocorrenciaId));
        verify(ocorrenciaService).consultar(autorId, ocorrenciaId);
    }

    @Test
    void administradorQuePodeConsultarNaoPodeEnviarEmNomeDoAutor() {
        UUID administradorId = UUID.randomUUID();
        when(ocorrenciaService.consultar(administradorId, ocorrenciaId)).thenReturn(ocorrencia());
        assertThrows(AccessDeniedException.class,
                () -> service.autorizarEnvio(administradorId, ocorrenciaId));
    }

    @Test
    void consultaDoAutorUsaVisibilidadeDaOcorrencia() {
        when(ocorrenciaService.consultar(autorId, ocorrenciaId)).thenReturn(ocorrencia());
        assertDoesNotThrow(() -> service.autorizarConsulta(autorId, ocorrenciaId));
        verify(ocorrenciaService).consultar(autorId, ocorrenciaId);
    }

    @Test
    void administradorAutorizadoPodeConsultarFotografiaDeOutroAutor() {
        UUID administradorId = UUID.randomUUID();
        when(ocorrenciaService.consultar(administradorId, ocorrenciaId)).thenReturn(ocorrencia());
        assertDoesNotThrow(() -> service.autorizarConsulta(administradorId, ocorrenciaId));
        verify(ocorrenciaService).consultar(administradorId, ocorrenciaId);
    }

    @Test
    void consultaInexistenteOuForaDoEscopoMantemErroOriginal() {
        var erro = new RecursoNaoEncontradoException("Ocorrência não encontrada.");
        when(ocorrenciaService.consultar(autorId, ocorrenciaId)).thenThrow(erro);
        assertSame(erro, assertThrows(RecursoNaoEncontradoException.class,
                () -> service.autorizarConsulta(autorId, ocorrenciaId)));
        assertSame(erro, assertThrows(RecursoNaoEncontradoException.class,
                () -> service.autorizarEnvio(autorId, ocorrenciaId)));
    }

    @Test
    void contaRemovidaMantemErroDeAutenticacao() {
        var erro = new BadCredentialsException("Usuário não encontrado.");
        when(ocorrenciaService.consultar(autorId, ocorrenciaId)).thenThrow(erro);
        assertSame(erro, assertThrows(BadCredentialsException.class,
                () -> service.autorizarEnvio(autorId, ocorrenciaId)));
        assertSame(erro, assertThrows(BadCredentialsException.class,
                () -> service.autorizarConsulta(autorId, ocorrenciaId)));
    }

    private OcorrenciaResponse ocorrencia() {
        return new OcorrenciaResponse(ocorrenciaId, autorId, CategoriaOcorrencia.BURACO,
                "Buraco na via", -19.9, -43.9, null, StatusOcorrencia.ENVIADA,
                null, LocalDateTime.of(2026, 10, 26, 12, 0), null);
    }
}
