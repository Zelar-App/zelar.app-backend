package app.zelar.autenticacao.service;

import app.zelar.autenticacao.dto.LoginRequest;
import app.zelar.autenticacao.dto.LoginResponse;
import app.zelar.usuario.dto.UsuarioResponse;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AutenticacaoService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final String senhaInexistenteHash;

    public AutenticacaoService(UsuarioRepository usuarioRepository,
                              PasswordEncoder passwordEncoder,
                              TokenService tokenService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.senhaInexistenteHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public LoginResponse autenticar(LoginRequest request) {
        String emailNormalizado = request.email().trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.findByEmail(emailNormalizado).orElse(null);

        // Executa a comparação de hash também para contas inexistentes.
        String hash = usuario == null ? senhaInexistenteHash : usuario.getSenha();
        boolean senhaValida = passwordEncoder.matches(request.senha(), hash);
        if (usuario == null || !senhaValida) {
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        }

        return new LoginResponse(tokenService.gerar(usuario), "Bearer",
                tokenService.getDuracaoEmSegundos(), UsuarioResponse.from(usuario));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse consultarUsuario(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new BadCredentialsException("Usuário não autenticado."));
        return UsuarioResponse.from(usuario);
    }
}
