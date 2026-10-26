package app.zelar.ocorrencia.entity;

import app.zelar.ocorrencia.exception.TransicaoStatusInvalidaException;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class OcorrenciaTest {

    private final Usuario usuario = new Usuario("João", "joao@email.com", "hash",
            PerfilUsuario.CIDADAO, null);
    private final Localizacao localizacao = new Localizacao(-12.97, -38.50, null);

    @Test
    void deveCriarOcorrenciaEnviadaSemPrioridadeNemAtualizacao() {
        Ocorrencia ocorrencia = new Ocorrencia(usuario, CategoriaOcorrencia.BURACO,
                "Buraco na via", localizacao);

        assertSame(usuario, ocorrencia.getUsuario());
        assertEquals(StatusOcorrencia.ENVIADA, ocorrencia.getStatus());
        assertNull(ocorrencia.getPrioridade());
        assertNull(ocorrencia.getDataAtualizacao());
        assertNotNull(ocorrencia.getDataCriacao());
    }

    @Test
    void deveAvancarStatusSemAlterarDataDeCriacaoOuAutor() {
        Ocorrencia ocorrencia = new Ocorrencia(usuario, CategoriaOcorrencia.BURACO,
                "Buraco na via", localizacao);
        var dataCriacao = ocorrencia.getDataCriacao();

        ocorrencia.atualizarStatus(StatusOcorrencia.EM_ANALISE);
        assertEquals(StatusOcorrencia.EM_ANALISE, ocorrencia.getStatus());
        assertNotNull(ocorrencia.getDataAtualizacao());
        ocorrencia.atualizarStatus(StatusOcorrencia.RESOLVIDA);

        assertEquals(StatusOcorrencia.RESOLVIDA, ocorrencia.getStatus());
        assertEquals(dataCriacao, ocorrencia.getDataCriacao());
        assertSame(usuario, ocorrencia.getUsuario());
        assertNull(ocorrencia.getPrioridade());
    }

    @ParameterizedTest
    @CsvSource({"ENVIADA,ENVIADA", "ENVIADA,RESOLVIDA", "EM_ANALISE,ENVIADA",
            "EM_ANALISE,EM_ANALISE", "RESOLVIDA,ENVIADA", "RESOLVIDA,EM_ANALISE", "RESOLVIDA,RESOLVIDA"})
    void deveRecusarTransicaoInvalidaSemAlterarEstado(StatusOcorrencia origem, StatusOcorrencia destino) {
        Ocorrencia ocorrencia = new Ocorrencia(usuario, CategoriaOcorrencia.BURACO,
                "Buraco na via", localizacao);
        ReflectionTestUtils.setField(ocorrencia, "status", origem);

        assertThrows(TransicaoStatusInvalidaException.class, () -> ocorrencia.atualizarStatus(destino));

        assertEquals(origem, ocorrencia.getStatus());
        assertNull(ocorrencia.getDataAtualizacao());
    }

    @Test
    void deveRecusarStatusNuloSemAlterarOcorrencia() {
        Ocorrencia ocorrencia = new Ocorrencia(usuario, CategoriaOcorrencia.BURACO,
                "Buraco na via", localizacao);

        assertThrows(NullPointerException.class, () -> ocorrencia.atualizarStatus(null));
        assertEquals(StatusOcorrencia.ENVIADA, ocorrencia.getStatus());
        assertNull(ocorrencia.getDataAtualizacao());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void deveRejeitarDescricaoAusente(String descricao) {
        assertThrows(IllegalArgumentException.class,
                () -> new Ocorrencia(usuario, CategoriaOcorrencia.BURACO, descricao, localizacao));
    }

    @Test
    void deveExigirUsuarioCategoriaELocalizacao() {
        assertThrows(NullPointerException.class,
                () -> new Ocorrencia(null, CategoriaOcorrencia.BURACO, "Buraco na via", localizacao));
        assertThrows(NullPointerException.class,
                () -> new Ocorrencia(usuario, null, "Buraco na via", localizacao));
        assertThrows(NullPointerException.class,
                () -> new Ocorrencia(usuario, CategoriaOcorrencia.BURACO, "Buraco na via", null));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-90.01, 90.01, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void deveRejeitarLatitudeInvalida(double latitude) {
        assertThrows(IllegalArgumentException.class, () -> new Localizacao(latitude, 0.0, null));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.01, 180.01, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void deveRejeitarLongitudeInvalida(double longitude) {
        assertThrows(IllegalArgumentException.class, () -> new Localizacao(0.0, longitude, null));
    }

    @Test
    void deveExigirCoordenadasELimitarEndereco() {
        assertThrows(IllegalArgumentException.class, () -> new Localizacao(null, 0.0, null));
        assertThrows(IllegalArgumentException.class, () -> new Localizacao(0.0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Localizacao(0.0, 0.0, "a".repeat(256)));
        assertDoesNotThrow(() -> new Localizacao(-90.0, -180.0, "a".repeat(255)));
        assertDoesNotThrow(() -> new Localizacao(90.0, 180.0, null));
    }
}
