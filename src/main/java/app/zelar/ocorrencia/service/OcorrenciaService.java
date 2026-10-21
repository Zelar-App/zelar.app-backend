package app.zelar.ocorrencia.service;

import app.zelar.ocorrencia.dto.CriarOcorrenciaRequest;
import app.zelar.ocorrencia.entity.Localizacao;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.repository.OcorrenciaRepository;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OcorrenciaService {

    private final OcorrenciaRepository ocorrenciaRepository;
    private final UsuarioRepository usuarioRepository;

    public OcorrenciaService(OcorrenciaRepository ocorrenciaRepository,
                             UsuarioRepository usuarioRepository) {
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public Ocorrencia criar(UUID usuarioId, CriarOcorrenciaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BadCredentialsException("Usuário não autenticado."));

        Localizacao localizacao = new Localizacao(request.latitude(), request.longitude(),
                request.enderecoAproximado());
        Ocorrencia ocorrencia = new Ocorrencia(usuario, request.categoria(), request.descricao(), localizacao);

        return ocorrenciaRepository.save(ocorrencia);
    }
}
