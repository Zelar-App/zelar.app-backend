package app.zelar.usuario.service;

import app.zelar.usuario.dto.CriarUsuarioRequest;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import app.zelar.usuario.exception.EmailJaCadastradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(
                usuarioRepository,
                passwordEncoder
        );
    }

    @Test
    void deveCadastrarUsuarioComoCidadao() {

        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "João Silva",
                "joao@email.com",
                "Senha123"
        );

        when(usuarioRepository.existsByEmail("joao@email.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("Senha123"))
                .thenReturn("senha-criptografada");

        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Usuario usuario = usuarioService.criar(request);

        assertNotNull(usuario);
        assertEquals("João Silva", usuario.getNome());
        assertEquals("joao@email.com", usuario.getEmail());
        assertEquals("senha-criptografada", usuario.getSenha());
        assertEquals(PerfilUsuario.CIDADAO, usuario.getPerfil());
        assertNotNull(usuario.getCriadoEm());

        verify(usuarioRepository).save(any(Usuario.class));
    }
    @Test
    void deveNormalizarEmailAntesDoCadastro() {

        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "João Silva",
                "  JOAO@EMAIL.COM  ",
                "Senha123"
        );

        when(usuarioRepository.existsByEmail("joao@email.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("Senha123"))
                .thenReturn("senha-criptografada");

        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Usuario usuario = usuarioService.criar(request);

        assertEquals("joao@email.com", usuario.getEmail());

        verify(usuarioRepository)
                .existsByEmail("joao@email.com");
    }
    @Test
    void deveCriptografarSenhaAntesDeSalvar() {

        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "João Silva",
                "joao@email.com",
                "Senha123"
        );

        when(usuarioRepository.existsByEmail("joao@email.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("Senha123"))
                .thenReturn("hash-da-senha");

        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Usuario usuario = usuarioService.criar(request);

        assertEquals("hash-da-senha", usuario.getSenha());
        assertNotEquals("Senha123", usuario.getSenha());

        verify(passwordEncoder).encode("Senha123");
    }
    @Test
    void deveRejeitarEmailJaCadastrado() {

        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "João Silva",
                "joao@email.com",
                "Senha123"
        );

        when(usuarioRepository.existsByEmail("joao@email.com"))
                .thenReturn(true);

        assertThrows(
                EmailJaCadastradoException.class,
                () -> usuarioService.criar(request)
        );

        verify(usuarioRepository, never())
                .save(any(Usuario.class));

        verify(passwordEncoder, never())
                .encode(anyString());
    }
}

