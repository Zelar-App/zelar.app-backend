package app.zelar.ocorrencia.service;

import app.zelar.ocorrencia.dto.CriarOcorrenciaRequest;
import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.entity.StatusOcorrencia;
import app.zelar.ocorrencia.repository.OcorrenciaRepository;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcorrenciaServiceTest {

    @Mock
    private OcorrenciaRepository ocorrenciaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private OcorrenciaService ocorrenciaService;

    @BeforeEach
    void setUp() {
        ocorrenciaService = new OcorrenciaService(ocorrenciaRepository, usuarioRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "Praça central")
    void deveCriarOcorrenciaComUsuarioAutenticadoEStatusInicial(String endereco) {
        UUID usuarioId = UUID.randomUUID();
        Usuario usuario = new Usuario("João", "joao@email.com", "hash", PerfilUsuario.CIDADAO, null);
        CriarOcorrenciaRequest request = new CriarOcorrenciaRequest(CategoriaOcorrencia.BURACO,
                "Buraco na via", -12.97, -38.50, endereco);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(ocorrenciaRepository.save(any(Ocorrencia.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ocorrencia ocorrencia = ocorrenciaService.criar(usuarioId, request);

        assertSame(usuario, ocorrencia.getUsuario());
        assertEquals(CategoriaOcorrencia.BURACO, ocorrencia.getCategoria());
        assertEquals("Buraco na via", ocorrencia.getDescricao());
        assertEquals(-12.97, ocorrencia.getLocalizacao().getLatitude());
        assertEquals(-38.50, ocorrencia.getLocalizacao().getLongitude());
        assertEquals(endereco, ocorrencia.getLocalizacao().getEnderecoAproximado());
        assertEquals(StatusOcorrencia.ENVIADA, ocorrencia.getStatus());
        assertNull(ocorrencia.getPrioridade());
        assertNull(ocorrencia.getDataAtualizacao());
        assertNotNull(ocorrencia.getDataCriacao());
        verify(ocorrenciaRepository).save(ocorrencia);
    }

    @Test
    void deveRejeitarUsuarioQueNaoExisteMaisSemPersistirOcorrencia() {
        UUID usuarioId = UUID.randomUUID();
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());
        CriarOcorrenciaRequest request = new CriarOcorrenciaRequest(CategoriaOcorrencia.LIXO,
                "Lixo acumulado", 0.0, 0.0, null);

        assertThrows(BadCredentialsException.class, () -> ocorrenciaService.criar(usuarioId, request));

        verifyNoInteractions(ocorrenciaRepository);
    }
}
