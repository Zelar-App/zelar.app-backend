package app.zelar.ocorrencia.service;

import app.zelar.ocorrencia.dto.AtualizarPrioridadeOcorrenciaRequest;
import app.zelar.ocorrencia.dto.AtualizarStatusOcorrenciaRequest;
import app.zelar.ocorrencia.dto.CriarOcorrenciaRequest;
import app.zelar.ocorrencia.dto.OcorrenciaIndicadoresResponse;
import app.zelar.ocorrencia.dto.ListarOcorrenciasRequest;
import app.zelar.ocorrencia.dto.OcorrenciaPaginaResponse;
import app.zelar.ocorrencia.dto.OcorrenciaResponse;
import app.zelar.ocorrencia.entity.Localizacao;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.entity.EstadoUpload;
import app.zelar.ocorrencia.entity.Anexo;
import app.zelar.ocorrencia.repository.AnexoRepository;
import app.zelar.ocorrencia.repository.OcorrenciaRepository;
import app.zelar.shared.exception.RecursoNaoEncontradoException;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OcorrenciaService {

    private final OcorrenciaRepository ocorrenciaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AnexoRepository anexoRepository;

    public OcorrenciaService(OcorrenciaRepository ocorrenciaRepository,
                             UsuarioRepository usuarioRepository, AnexoRepository anexoRepository) {
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.usuarioRepository = usuarioRepository;
        this.anexoRepository = anexoRepository;
    }

    @Transactional(readOnly = true)
    public OcorrenciaIndicadoresResponse consultarIndicadores(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BadCredentialsException("Usuário não autenticado."));
        if (usuario.getPerfil() != PerfilUsuario.ADMINISTRADOR) {
            throw new AccessDeniedException("Você não possui permissão para acessar este recurso.");
        }
        return ocorrenciaRepository.contarPorStatus();
    }

    @Transactional(readOnly = true)
    public OcorrenciaPaginaResponse listar(UUID usuarioId, ListarOcorrenciasRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BadCredentialsException("Usuário não autenticado."));

        Specification<Ocorrencia> filtros = visiveisPara(usuario, usuarioId);
        if (request.status() != null) {
            filtros = filtros.and((root, query, builder) -> builder.equal(root.get("status"), request.status()));
        }
        if (request.prioridade() != null) {
            filtros = filtros.and((root, query, builder) ->
                    builder.equal(root.get("prioridade"), request.prioridade()));
        }

        PageRequest pageable = PageRequest.of(request.pagina(), request.tamanho(),
                Sort.by(Sort.Direction.DESC, "dataCriacao", "id"));
        Page<Ocorrencia> registros = ocorrenciaRepository.findAll(filtros, pageable);
        var ids = registros.getContent().stream().map(Ocorrencia::getId).toList();
        var comFotografia = anexoRepository.findByOcorrenciaIdInAndEstadoUpload(ids, EstadoUpload.DISPONIVEL)
                .stream().map(Anexo::getOcorrenciaId).collect(Collectors.toSet());
        Page<OcorrenciaResponse> pagina = registros.map(ocorrencia -> OcorrenciaResponse.from(ocorrencia)
                .comFotografia(comFotografia.contains(ocorrencia.getId())));
        return OcorrenciaPaginaResponse.from(pagina);
    }

    @Transactional(readOnly = true)
    public OcorrenciaResponse consultar(UUID usuarioId, UUID ocorrenciaId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BadCredentialsException("Usuário não autenticado."));
        Specification<Ocorrencia> filtros = visiveisPara(usuario, usuarioId)
                .and((root, query, builder) -> builder.equal(root.get("id"), ocorrenciaId));

        Ocorrencia ocorrencia = ocorrenciaRepository.findOne(filtros)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ocorrência não encontrada."));
        return responder(ocorrencia);
    }

    private OcorrenciaResponse responder(Ocorrencia ocorrencia) {
        return OcorrenciaResponse.from(ocorrencia).comFotografia(anexoRepository
                .existsByOcorrenciaIdAndEstadoUpload(ocorrencia.getId(), EstadoUpload.DISPONIVEL));
    }

    private Specification<Ocorrencia> visiveisPara(Usuario usuario, UUID usuarioId) {
        return (root, query, builder) -> usuario.getPerfil() == PerfilUsuario.ADMINISTRADOR
                ? builder.conjunction()
                : builder.equal(root.get("usuario").get("id"), usuarioId);
    }

    @Transactional
    public OcorrenciaResponse atualizarStatus(UUID usuarioId, UUID ocorrenciaId,
                                               AtualizarStatusOcorrenciaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BadCredentialsException("Usuário não autenticado."));
        if (usuario.getPerfil() != PerfilUsuario.ADMINISTRADOR) {
            throw new AccessDeniedException("Você não possui permissão para acessar este recurso.");
        }

        Ocorrencia ocorrencia = ocorrenciaRepository.findByIdParaAtualizacao(ocorrenciaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ocorrência não encontrada."));
        ocorrencia.atualizarStatus(request.status());
        return responder(ocorrencia);
    }

    @Transactional
    public OcorrenciaResponse atualizarPrioridade(UUID usuarioId, UUID ocorrenciaId,
                                                   AtualizarPrioridadeOcorrenciaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BadCredentialsException("Usuário não autenticado."));
        if (usuario.getPerfil() != PerfilUsuario.ADMINISTRADOR) {
            throw new AccessDeniedException("Você não possui permissão para acessar este recurso.");
        }

        Ocorrencia ocorrencia = ocorrenciaRepository.findByIdParaAtualizacao(ocorrenciaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ocorrência não encontrada."));
        ocorrencia.atualizarPrioridade(request.prioridade());
        return responder(ocorrencia);
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
