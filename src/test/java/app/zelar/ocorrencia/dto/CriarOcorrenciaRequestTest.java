package app.zelar.ocorrencia.dto;

import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CriarOcorrenciaRequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void fecharValidator() {
        FACTORY.close();
    }

    @Test
    void deveAceitarCoordenadasLimiteEEnderecoOpcional() {
        assertTrue(VALIDATOR.validate(new CriarOcorrenciaRequest(CategoriaOcorrencia.LIXO,
                "Lixo acumulado", -90.0, -180.0, null)).isEmpty());
        assertTrue(VALIDATOR.validate(new CriarOcorrenciaRequest(CategoriaOcorrencia.LIXO,
                "Lixo acumulado", 90.0, 180.0, "a".repeat(255))).isEmpty());
    }

    @Test
    void deveIdentificarTodosOsCamposObrigatorios() {
        var erros = VALIDATOR.validate(new CriarOcorrenciaRequest(null, " ", null, null, null));
        Set<String> campos = erros.stream().map(erro -> erro.getPropertyPath().toString()).collect(Collectors.toSet());

        assertEquals(Set.of("categoria", "descricao", "latitude", "longitude"), campos);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-90.01, 90.01, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void deveRejeitarLatitudeForaDoDominio(double latitude) {
        assertFalse(VALIDATOR.validate(new CriarOcorrenciaRequest(CategoriaOcorrencia.BURACO,
                "Buraco na via", latitude, 0.0, null)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.01, 180.01, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void deveRejeitarLongitudeForaDoDominio(double longitude) {
        assertFalse(VALIDATOR.validate(new CriarOcorrenciaRequest(CategoriaOcorrencia.BURACO,
                "Buraco na via", 0.0, longitude, null)).isEmpty());
    }

    @Test
    void deveRejeitarEnderecoAcimaDoLimiteDoBanco() {
        var erros = VALIDATOR.validate(new CriarOcorrenciaRequest(CategoriaOcorrencia.BURACO,
                "Buraco na via", 0.0, 0.0, "a".repeat(256)));

        assertEquals(1, erros.size());
        assertEquals("enderecoAproximado", erros.iterator().next().getPropertyPath().toString());
    }
}
