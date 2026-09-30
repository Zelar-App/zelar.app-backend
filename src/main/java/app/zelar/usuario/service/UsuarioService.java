package app.zelar.usuario.service;

import app.zelar.usuario.dto.CriarUsuarioRequest;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.exception.EmailJaCadastradoException;
import app.zelar.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario criar(CriarUsuarioRequest request) {

        String emailNormalizado = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new EmailJaCadastradoException();
        }

        String senhaCriptografada =
                passwordEncoder.encode(request.senha());

        Usuario usuario = new Usuario(
                request.nome().trim(),
                emailNormalizado,
                senhaCriptografada,
                PerfilUsuario.CIDADAO
        );

        return usuarioRepository.save(usuario);
    }
}