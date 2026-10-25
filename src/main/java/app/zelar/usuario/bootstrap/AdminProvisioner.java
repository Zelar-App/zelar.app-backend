package app.zelar.usuario.bootstrap;

import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Provisiona a conta administrativa no startup quando ADMIN_EMAIL e ADMIN_PASSWORD
 * são configurados. Idempotente: se o e-mail já existir, nenhuma conta é criada ou
 * alterada. Nunca registra a senha em log.
 */
@Component
@EnableConfigurationProperties(AdminProperties.class)
public class AdminProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminProvisioner.class);
    private static final String NOME_PADRAO = "Administrador";

    private final AdminProperties properties;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminProvisioner(AdminProperties properties, UsuarioRepository usuarioRepository,
                            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email()) && !StringUtils.hasText(properties.password())) {
            log.info("Provisionamento de conta administrativa desativado: defina ADMIN_EMAIL e ADMIN_PASSWORD para habilitar.");
            return;
        }
        Assert.hasText(properties.email(), "ADMIN_EMAIL deve ser informado junto de ADMIN_PASSWORD.");
        Assert.hasText(properties.password(), "ADMIN_PASSWORD deve ser informada junto de ADMIN_EMAIL.");
        Assert.isTrue(properties.password().length() >= 8 && properties.password().length() <= 72,
                "ADMIN_PASSWORD deve possuir entre 8 e 72 caracteres.");

        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByEmail(email)) {
            log.warn("Conta administrativa não provisionada: já existe usuário com e-mail {}. Nenhuma alteração foi feita.", email);
            return;
        }

        String nome = StringUtils.hasText(properties.nome()) ? properties.nome().trim() : NOME_PADRAO;
        usuarioRepository.save(new Usuario(nome, email, passwordEncoder.encode(properties.password()),
                PerfilUsuario.ADMINISTRADOR, null));
        log.info("Conta administrativa provisionada para {}.", email);
    }
}
