package app.zelar.ocorrencia.service;

import app.zelar.ocorrencia.dto.AtualizarPrioridadeOcorrenciaRequest;
import app.zelar.ocorrencia.dto.AtualizarStatusOcorrenciaRequest;
import app.zelar.ocorrencia.dto.CriarOcorrenciaRequest;
import app.zelar.ocorrencia.dto.OcorrenciaIndicadoresResponse;
import app.zelar.ocorrencia.dto.ListarOcorrenciasRequest;
import app.zelar.ocorrencia.dto.OcorrenciaPaginaResponse;
import app.zelar.ocorrencia.dto.OcorrenciaResponse;
import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import app.zelar.ocorrencia.entity.Localizacao;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.entity.PrioridadeOcorrencia;
import app.zelar.ocorrencia.entity.StatusOcorrencia;
import app.zelar.ocorrencia.exception.TransicaoStatusInvalidaException;
import app.zelar.ocorrencia.repository.OcorrenciaRepository;
import app.zelar.shared.exception.RecursoNaoEncontradoException;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.repository.UsuarioRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
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
    void deveRetornarPaginaComMetadadosEOrdenacaoDeterministica() {
        UUID usuarioId = UUID.randomUUID();
        Usuario usuario = new Usuario("João", "joao@email.com", "hash", PerfilUsuario.CIDADAO, null);
        Ocorrencia ocorrencia = new Ocorrencia(usuario, CategoriaOcorrencia.BURACO,
                "Buraco na via", new Localizacao(-12.97, -38.50, null));
        PageRequest pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "dataCriacao", "id"));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(ocorrenciaRepository.findAll(ArgumentMatchers.<Specification<Ocorrencia>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ocorrencia), pageable, 5));

        OcorrenciaPaginaResponse response = ocorrenciaService.listar(usuarioId,
                new ListarOcorrenciasRequest(1, 2, StatusOcorrencia.ENVIADA, null));

        assertEquals(1, response.pagina());
        assertEquals(2, response.tamanho());
        assertEquals(5, response.totalElementos());
        assertEquals(3, response.totalPaginas());
        assertEquals("Buraco na via", response.conteudo().getFirst().descricao());
        verify(ocorrenciaRepository).findAll(ArgumentMatchers.<Specification<Ocorrencia>>any(), eq(pageable));
        verify(ocorrenciaRepository, never()).save(any());
    }

    @Test
    void deveRejeitarListagemDeUsuarioInexistenteSemConsultarOcorrencias() {
        UUID usuarioId = UUID.randomUUID();
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> ocorrenciaService.listar(usuarioId,
                new ListarOcorrenciasRequest(null, null, null, null)));

        verifyNoInteractions(ocorrenciaRepository);
    }

    @ParameterizedTest
    @CsvSource({"ENVIADA,EM_ANALISE", "EM_ANALISE,RESOLVIDA"})
    void deveAtualizarStatusComAdministradorERegistroBloqueado(StatusOcorrencia origem,
                                                              StatusOcorrencia destino) {
        UUID administradorId = UUID.randomUUID();
        UUID ocorrenciaId = UUID.randomUUID();
        Usuario administrador = new Usuario("Admin", "admin@email.com", "hash", PerfilUsuario.ADMINISTRADOR, null);
        Ocorrencia ocorrencia = new Ocorrencia(administrador, CategoriaOcorrencia.BURACO,
                "Buraco na via", new Localizacao(-12.97, -38.50, null));
        ReflectionTestUtils.setField(ocorrencia, "status", origem);
        when(usuarioRepository.findById(administradorId)).thenReturn(Optional.of(administrador));
        when(ocorrenciaRepository.findByIdParaAtualizacao(ocorrenciaId)).thenReturn(Optional.of(ocorrencia));

        var response = ocorrenciaService.atualizarStatus(administradorId, ocorrenciaId,
                new AtualizarStatusOcorrenciaRequest(destino));

        assertEquals(destino, response.status());
        assertNotNull(response.dataAtualizacao());
        verify(ocorrenciaRepository).findByIdParaAtualizacao(ocorrenciaId);
        verify(ocorrenciaRepository, never()).save(any());
    }

    @Test
    void deveRecusarAtualizacaoPorCidadaoAntesDeBuscarOcorrencia() {
        UUID usuarioId = UUID.randomUUID();
        Usuario cidadao = new Usuario("Pessoa", "cidadao@email.com", "hash", PerfilUsuario.CIDADAO, null);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(cidadao));

        assertThrows(AccessDeniedException.class, () -> ocorrenciaService.atualizarStatus(usuarioId,
                UUID.randomUUID(), new AtualizarStatusOcorrenciaRequest(StatusOcorrencia.EM_ANALISE)));

        verifyNoInteractions(ocorrenciaRepository);
    }

    @Test
    void deveRecusarAtualizacaoDeUsuarioRemovidoAntesDeBuscarOcorrencia() {
        UUID usuarioId = UUID.randomUUID();
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> ocorrenciaService.atualizarStatus(usuarioId,
                UUID.randomUUID(), new AtualizarStatusOcorrenciaRequest(StatusOcorrencia.EM_ANALISE)));

        verifyNoInteractions(ocorrenciaRepository);
    }

    @Test
    void deveInformarOcorrenciaInexistenteNaAtualizacaoAdministrativa() {
        UUID usuarioId = UUID.randomUUID();
        UUID ocorrenciaId = UUID.randomUUID();
        Usuario administrador = new Usuario("Admin", "admin@email.com", "hash", PerfilUsuario.ADMINISTRADOR, null);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(administrador));
        when(ocorrenciaRepository.findByIdParaAtualizacao(ocorrenciaId)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> ocorrenciaService.atualizarStatus(usuarioId,
                ocorrenciaId, new AtualizarStatusOcorrenciaRequest(StatusOcorrencia.EM_ANALISE)));
    }

    @Test
    void devePreservarEstadoQuandoServicoReceberTransicaoInvalida() {
        UUID usuarioId = UUID.randomUUID();
        UUID ocorrenciaId = UUID.randomUUID();
        Usuario administrador = new Usuario("Admin", "admin@email.com", "hash", PerfilUsuario.ADMINISTRADOR, null);
        Ocorrencia ocorrencia = new Ocorrencia(administrador, CategoriaOcorrencia.BURACO,
                "Buraco na via", new Localizacao(-12.97, -38.50, null));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(administrador));
        when(ocorrenciaRepository.findByIdParaAtualizacao(ocorrenciaId)).thenReturn(Optional.of(ocorrencia));

        assertThrows(TransicaoStatusInvalidaException.class, () -> ocorrenciaService.atualizarStatus(usuarioId,
                ocorrenciaId, new AtualizarStatusOcorrenciaRequest(StatusOcorrencia.RESOLVIDA)));

        assertEquals(StatusOcorrencia.ENVIADA, ocorrencia.getStatus());
        assertNull(ocorrencia.getDataAtualizacao());
    }

    @Test
    void deveConsultarOcorrenciaComoDtoSemGravarAlteracoes() {
        UUID usuarioId = UUID.randomUUID();
        UUID ocorrenciaId = UUID.randomUUID();
        Usuario usuario = new Usuario("João", "joao@email.com", "hash", PerfilUsuario.CIDADAO, null);
        ReflectionTestUtils.setField(usuario, "id", usuarioId);
        Ocorrencia ocorrencia = new Ocorrencia(usuario, CategoriaOcorrencia.BURACO,
                "Buraco na via", new Localizacao(-12.97, -38.50, null));
        ReflectionTestUtils.setField(ocorrencia, "id", ocorrenciaId);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(ocorrenciaRepository.findOne(ArgumentMatchers.<Specification<Ocorrencia>>any()))
                .thenReturn(Optional.of(ocorrencia));

        OcorrenciaResponse response = ocorrenciaService.consultar(usuarioId, ocorrenciaId);

        assertEquals(ocorrenciaId, response.id());
        assertEquals(usuarioId, response.usuarioId());
        assertEquals("Buraco na via", response.descricao());
        assertEquals(StatusOcorrencia.ENVIADA, response.status());
        verify(ocorrenciaRepository, never()).save(any());
    }

    @Test
    void deveRetornarNaoEncontradoQuandoConsultaVisivelNaoTiverResultado() {
        UUID usuarioId = UUID.randomUUID();
        Usuario usuario = new Usuario("João", "joao@email.com", "hash", PerfilUsuario.CIDADAO, null);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(ocorrenciaRepository.findOne(ArgumentMatchers.<Specification<Ocorrencia>>any()))
                .thenReturn(Optional.empty());

        RecursoNaoEncontradoException exception = assertThrows(RecursoNaoEncontradoException.class,
                () -> ocorrenciaService.consultar(usuarioId, UUID.randomUUID()));

        assertEquals("Ocorrência não encontrada.", exception.getMessage());
    }

    @Test
    void deveRecusarConsultaDeUsuarioInexistenteAntesDeBuscarOcorrencia() {
        UUID usuarioId = UUID.randomUUID();
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> ocorrenciaService.consultar(usuarioId, UUID.randomUUID()));

        verifyNoInteractions(ocorrenciaRepository);
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
    @ParameterizedTest
    @EnumSource(PrioridadeOcorrencia.class)
    void deveAtualizarPrioridadeComoAdministradorComLeituraBloqueada(PrioridadeOcorrencia prioridade) {
        UUID usuarioId = UUID.randomUUID();
        UUID ocorrenciaId = UUID.randomUUID();
        Usuario administrador = new Usuario("Admin", "admin@email.com", "hash", PerfilUsuario.ADMINISTRADOR, null);
        Ocorrencia ocorrencia = new Ocorrencia(administrador, CategoriaOcorrencia.BURACO,
                "Buraco na via", new Localizacao(0.0, 0.0, null));
        ReflectionTestUtils.setField(ocorrencia, "id", ocorrenciaId);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(administrador));
        when(ocorrenciaRepository.findByIdParaAtualizacao(ocorrenciaId)).thenReturn(Optional.of(ocorrencia));

        OcorrenciaResponse response = ocorrenciaService.atualizarPrioridade(usuarioId, ocorrenciaId,
                new AtualizarPrioridadeOcorrenciaRequest(prioridade));

        assertEquals(ocorrenciaId, response.id());
        assertEquals(prioridade, response.prioridade());
        assertEquals(StatusOcorrencia.ENVIADA, response.status());
        assertNotNull(response.dataAtualizacao());
        var ordem = inOrder(usuarioRepository, ocorrenciaRepository);
        ordem.verify(usuarioRepository).findById(usuarioId);
        ordem.verify(ocorrenciaRepository).findByIdParaAtualizacao(ocorrenciaId);
        verify(ocorrenciaRepository, never()).save(any());
    }

    @Test
    void deveRecusarPrioridadeParaCidadaoAntesDeConsultarOcorrencia() {
        UUID usuarioId = UUID.randomUUID();
        Usuario cidadao = new Usuario("João", "joao@email.com", "hash", PerfilUsuario.CIDADAO, null);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(cidadao));

        assertThrows(AccessDeniedException.class, () -> ocorrenciaService.atualizarPrioridade(usuarioId,
                UUID.randomUUID(), new AtualizarPrioridadeOcorrenciaRequest(PrioridadeOcorrencia.ALTA)));

        verifyNoInteractions(ocorrenciaRepository);
    }

    @Test
    void deveRecusarPrioridadeParaUsuarioRemovidoAntesDeConsultarOcorrencia() {
        UUID usuarioId = UUID.randomUUID();
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> ocorrenciaService.atualizarPrioridade(usuarioId,
                UUID.randomUUID(), new AtualizarPrioridadeOcorrenciaRequest(PrioridadeOcorrencia.ALTA)));

        verifyNoInteractions(ocorrenciaRepository);
    }

    @Test
    void deveRetornar404NaAtualizacaoDePrioridadeQuandoOcorrenciaNaoExistir() {
        UUID usuarioId = UUID.randomUUID();
        UUID ocorrenciaId = UUID.randomUUID();
        Usuario admin = new Usuario("Admin", "admin@email.com", "hash", PerfilUsuario.ADMINISTRADOR, null);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(admin));
        when(ocorrenciaRepository.findByIdParaAtualizacao(ocorrenciaId)).thenReturn(Optional.empty());

        RecursoNaoEncontradoException exception = assertThrows(RecursoNaoEncontradoException.class,
                () -> ocorrenciaService.atualizarPrioridade(usuarioId, ocorrenciaId,
                        new AtualizarPrioridadeOcorrenciaRequest(PrioridadeOcorrencia.ALTA)));

        assertEquals("Ocorrência não encontrada.", exception.getMessage());
        verify(ocorrenciaRepository, never()).save(any());
    }

    @Test
    void deveConsultarIndicadoresAposConfirmarPerfilAdministrativoAtual() {
        UUID usuarioId = UUID.randomUUID();
        Usuario admin = new Usuario("Admin", "admin@email.com", "hash", PerfilUsuario.ADMINISTRADOR, null);
        OcorrenciaIndicadoresResponse indicadores = new OcorrenciaIndicadoresResponse(3_000_000_000L, 2, 1);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(admin));
        when(ocorrenciaRepository.contarPorStatus()).thenReturn(indicadores);

        OcorrenciaIndicadoresResponse response = ocorrenciaService.consultarIndicadores(usuarioId);

        assertEquals(3_000_000_000L, response.enviadas());
        assertEquals(2, response.emAnalise());
        assertEquals(1, response.resolvidas());
        var ordem = inOrder(usuarioRepository, ocorrenciaRepository);
        ordem.verify(usuarioRepository).findById(usuarioId);
        ordem.verify(ocorrenciaRepository).contarPorStatus();
        verifyNoMoreInteractions(ocorrenciaRepository);
    }

    @Test
    void deveRecusarIndicadoresParaCidadaoAntesDeConsultarContagens() {
        UUID usuarioId = UUID.randomUUID();
        Usuario cidadao = new Usuario("João", "joao@email.com", "hash", PerfilUsuario.CIDADAO, null);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(cidadao));

        assertThrows(AccessDeniedException.class, () -> ocorrenciaService.consultarIndicadores(usuarioId));

        verifyNoInteractions(ocorrenciaRepository);
    }

    @Test
    void deveRecusarIndicadoresParaUsuarioRemovidoAntesDeConsultarContagens() {
        UUID usuarioId = UUID.randomUUID();
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> ocorrenciaService.consultarIndicadores(usuarioId));

        verifyNoInteractions(ocorrenciaRepository);
    }
}
