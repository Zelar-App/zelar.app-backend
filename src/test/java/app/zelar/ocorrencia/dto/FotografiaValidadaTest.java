package app.zelar.ocorrencia.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FotografiaValidadaTest {

    @ParameterizedTest
    @CsvSource({"image/png,png", "image/jpeg,jpg"})
    void deveAceitarTiposCorrespondentesEPreservarCopiasDefensivas(String tipo, String extensao) {
        byte[] original = {1, 2, 3};
        FotografiaValidada foto = new FotografiaValidada(original, tipo, extensao, 10, 8);
        original[0] = 9;
        byte[] leitura = foto.conteudo();
        leitura[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, foto.conteudo());
        assertEquals(3, foto.tamanhoBytes());
        assertEquals(tipo, foto.tipoConteudo());
        assertEquals(extensao, foto.extensao());
    }

    @Test
    void deveAceitarTamanhoExatamenteNoLimite() {
        var foto = new FotografiaValidada(new byte[FotografiaValidada.TAMANHO_MAXIMO_BYTES], "image/png", "png", 1, 1);
        assertEquals(FotografiaValidada.TAMANHO_MAXIMO_BYTES, foto.tamanhoBytes());
    }

    @Test
    void deveRecusarConteudoNulo() {
        assertThrows(NullPointerException.class, () -> new FotografiaValidada(null, "image/png", "png", 1, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, FotografiaValidada.TAMANHO_MAXIMO_BYTES + 1})
    void deveRecusarConteudoVazioOuAcimaDoLimite(int tamanho) {
        assertThrows(IllegalArgumentException.class,
                () -> new FotografiaValidada(new byte[tamanho], "image/png", "png", 1, 1));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"image/gif", "image/svg+xml", "IMAGE/PNG"})
    void deveRecusarTipoNaoPermitido(String tipo) {
        assertThrows(IllegalArgumentException.class,
                () -> new FotografiaValidada(new byte[]{1}, tipo, "png", 1, 1));
    }

    @ParameterizedTest
    @CsvSource(value = {"image/png|jpg", "image/jpeg|png", "image/png|NULL", "image/jpeg|NULL"},
            delimiter = '|', nullValues = "NULL")
    void deveRecusarExtensaoAusenteOuDiferenteDoTipo(String tipo, String extensao) {
        assertThrows(IllegalArgumentException.class,
                () -> new FotografiaValidada(new byte[]{1}, tipo, extensao, 1, 1));
    }

    @ParameterizedTest
    @CsvSource({"0,1", "-1,1", "1,0", "1,-1"})
    void deveRecusarCadaDimensaoNaoPositiva(int largura, int altura) {
        assertThrows(IllegalArgumentException.class,
                () -> new FotografiaValidada(new byte[]{1}, "image/png", "png", largura, altura));
    }
}
