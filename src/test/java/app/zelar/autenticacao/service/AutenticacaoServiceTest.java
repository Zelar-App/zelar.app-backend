package app.zelar.autenticacao.service;

import app.zelar.autenticacao.dto.LoginRequest;
import app.zelar.autenticacao.dto.LoginResponse;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutenticacaoServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;

    private AutenticacaoService autenticacaoService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash-inexistente");
        autenticacaoService = new AutenticacaoService(usuarioRepository, passwordEncoder, tokenService);
    }

    @Test
    void deveAutenticarComEmailNormalizadoESenhaConferida() {
        Usuario usuario = new Usuario("João", "joao@email.com", "hash-armazenado",
                PerfilUsuario.CIDADAO, null);
        when(usuarioRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha123", "hash-armazenado")).thenReturn(true);
        when(tokenService.gerar(usuario)).thenReturn("token-assinado");
        when(tokenService.getDuracaoEmSegundos()).thenReturn(3600L);

        LoginResponse response = autenticacaoService.autenticar(
                new LoginRequest("  JOAO@EMAIL.COM  ", "Senha123"));

        assertEquals("token-assinado", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600, response.expiresIn());
        assertEquals(PerfilUsuario.CIDADAO, response.usuario().perfil());
        verify(passwordEncoder).matches("Senha123", "hash-armazenado");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveRejeitarSenhaIncorretaSemGerarToken() {
        Usuario usuario = new Usuario("João", "joao@email.com", "hash-armazenado",
                PerfilUsuario.CIDADAO, null);
        when(usuarioRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(usuario));

        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> autenticacaoService.autenticar(new LoginRequest("joao@email.com", "incorreta")));

        assertEquals("E-mail ou senha inválidos.", exception.getMessage());
        verifyNoInteractions(tokenService);
    }

    @Test
    void deveCompararHashParaEmailInexistenteERetornarMesmoErro() {
        when(usuarioRepository.findByEmail("ausente@email.com")).thenReturn(Optional.empty());

        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> autenticacaoService.autenticar(new LoginRequest("ausente@email.com", "Senha123")));

        assertEquals("E-mail ou senha inválidos.", exception.getMessage());
        verify(passwordEncoder).matches("Senha123", "hash-inexistente");
        verifyNoInteractions(tokenService);
    }

    @Test
    void deveConsultarUsuarioAutenticadoPorId() {
        UUID id = UUID.randomUUID();
        Usuario usuario = new Usuario("João", "joao@email.com", "hash", PerfilUsuario.CIDADAO, null);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));

        assertEquals("joao@email.com", autenticacaoService.consultarUsuario(id).email());
    }

    @Test
    void deveRejeitarUsuarioAutenticadoQueNaoExisteMais() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> autenticacaoService.consultarUsuario(id));
    }
}
