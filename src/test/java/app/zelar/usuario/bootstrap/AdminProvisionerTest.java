package app.zelar.usuario.bootstrap;

import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminProvisionerTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private void executar(AdminProperties properties) throws Exception {
        new AdminProvisioner(properties, usuarioRepository, passwordEncoder)
                .run(new DefaultApplicationArguments());
    }

    @Test
    void naoDeveFazerNadaSemCredenciaisConfiguradas() throws Exception {
        executar(new AdminProperties(null, null, null));
        executar(new AdminProperties("  ", "  ", null));

        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void deveProvisionarAdminComSenhaHasheadaENomePadrao() throws Exception {
        when(usuarioRepository.existsByEmail("admin@email.com")).thenReturn(false);

        executar(new AdminProperties("  ADMIN@EMAIL.COM  ", "SenhaForte123", null));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario admin = captor.getValue();
        assertEquals("Administrador", admin.getNome());
        assertEquals("admin@email.com", admin.getEmail());
        assertEquals(PerfilUsuario.ADMINISTRADOR, admin.getPerfil());
        assertNotEquals("SenhaForte123", admin.getSenha());
        assertTrue(passwordEncoder.matches("SenhaForte123", admin.getSenha()));
    }

    @Test
    void deveUsarNomeInformadoQuandoPresente() throws Exception {
        when(usuarioRepository.existsByEmail("admin@email.com")).thenReturn(false);

        executar(new AdminProperties("admin@email.com", "SenhaForte123", "Admin Demo"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals("Admin Demo", captor.getValue().getNome());
    }

    @Test
    void naoDeveDuplicarOuAlterarContaExistente() throws Exception {
        when(usuarioRepository.existsByEmail("admin@email.com")).thenReturn(true);

        executar(new AdminProperties("admin@email.com", "SenhaForte123", null));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveExigirEmailQuandoSenhaInformada() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> executar(new AdminProperties(null, "SenhaForte123", null)));

        assertEquals("ADMIN_EMAIL deve ser informado junto de ADMIN_PASSWORD.", erro.getMessage());
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void deveExigirSenhaQuandoEmailInformado() {
        assertThrows(IllegalArgumentException.class,
                () -> executar(new AdminProperties("admin@email.com", null, null)));

        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void deveRejeitarSenhaForaDosLimitesDoCadastro() {
        for (String senha : new String[]{"curta", "s".repeat(73)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> executar(new AdminProperties("admin@email.com", senha, null)));
        }
        verifyNoInteractions(usuarioRepository);
    }
}
