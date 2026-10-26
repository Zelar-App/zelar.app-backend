package app.zelar.ocorrencia.fotografia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidadorFotografiaTest {

    private final ValidadorFotografia validador = new ValidadorFotografia();

    @ParameterizedTest
    @CsvSource({"png,image/png,png", "jpeg,image/jpeg,jpg"})
    void deveValidarERecodificarImagemSemConfiarNoNome(String formato, String tipo, String extensao) throws Exception {
        byte[] original = imagem(formato);
        byte[] comConteudoExtra = Arrays.copyOf(original, original.length + 20);
        Arrays.fill(comConteudoExtra, original.length, comConteudoExtra.length, (byte) 'X');
        FotografiaValidada fotografia = validador.validar(
                new MockMultipartFile("foto", "../../arquivo.exe", tipo, comConteudoExtra));

        assertEquals(tipo, fotografia.tipoConteudo());
        assertEquals(extensao, fotografia.extensao());
        assertEquals(10, fotografia.largura());
        assertEquals(8, fotografia.altura());
        assertTrue(fotografia.tamanhoBytes() > 0);
        assertTrue(fotografia.tamanhoBytes() <= 5 * 1024 * 1024);
        assertArrayEquals(original, fotografia.conteudo());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(fotografia.conteudo())));
    }

    @Test
    void devePreservarConteudoValidadoContraAlteracaoExterna() throws Exception {
        FotografiaValidada fotografia = validador.validar(arquivo("image/png", imagem("png")));
        byte[] copia = fotografia.conteudo();
        byte primeiro = copia[0];
        copia[0] = 0;
        assertEquals(primeiro, fotografia.conteudo()[0]);
    }

    @Test
    void deveRecusarArquivoAusenteOuVazio() {
        assertThrows(FotografiaInvalidaException.class, () -> validador.validar(null));
        assertThrows(FotografiaInvalidaException.class, () -> validador.validar(arquivo("image/png", new byte[0])));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"application/pdf", "image/svg+xml", "image/gif", "text/html"})
    void deveRecusarTipoNaoPermitidoMesmoComImagemValida(String tipo) throws Exception {
        byte[] png = imagem("png");
        assertThrows(FotografiaInvalidaException.class, () -> validador.validar(arquivo(tipo, png)));
    }

    @Test
    void deveRecusarMimeQueNaoCorrespondeAoConteudo() throws Exception {
        byte[] png = imagem("png");
        assertThrows(FotografiaInvalidaException.class, () -> validador.validar(arquivo("image/jpeg", png)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>alert(1)</script>", "<svg xmlns='http://www.w3.org/2000/svg'/>", "%PDF-1.7"})
    void deveRecusarConteudoFalsoMesmoComNomeEMimeDeImagem(String conteudo) {
        assertThrows(FotografiaInvalidaException.class,
                () -> validador.validar(arquivo("image/png", conteudo.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    @Test
    void deveRecusarImagemTruncada() throws Exception {
        byte[] truncada = Arrays.copyOf(imagem("png"), 20);
        assertThrows(FotografiaInvalidaException.class, () -> validador.validar(arquivo("image/png", truncada)));
    }

    @Test
    void deveRecusarArquivoAcimaDoLimite() {
        var exception = assertThrows(FotografiaInvalidaException.class,
                () -> validador.validar(arquivo("image/png", new byte[5 * 1024 * 1024 + 1])));
        assertTrue(exception.getMessage().contains("5 MiB"));
    }

    @Test
    void deveLimitarLeituraMesmoQuandoTamanhoDeclaradoForMenor() throws Exception {
        MultipartFile arquivo = mock(MultipartFile.class);
        when(arquivo.isEmpty()).thenReturn(false);
        when(arquivo.getSize()).thenReturn(1L);
        when(arquivo.getContentType()).thenReturn("image/png");
        when(arquivo.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[5 * 1024 * 1024 + 1]));
        var exception = assertThrows(FotografiaInvalidaException.class, () -> validador.validar(arquivo));
        assertTrue(exception.getMessage().contains("5 MiB"));
    }

    @ParameterizedTest
    @CsvSource({"8193,8", "5000,4000"})
    void deveRecusarDimensoesAntesDeDecodificarTodosOsPixels(int largura, int altura) throws Exception {
        byte[] png = imagem("png");
        ByteBuffer.wrap(png).putInt(16, largura).putInt(20, altura);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
        var exception = assertThrows(FotografiaInvalidaException.class, () -> validador.validar(arquivo("image/png", png)));
        assertTrue(exception.getMessage().contains("dimensões"));
    }

    @Test
    void deveTratarFalhaDeLeituraSemExporDetalhesDoArquivo() throws Exception {
        MultipartFile arquivo = mock(MultipartFile.class);
        when(arquivo.isEmpty()).thenReturn(false);
        when(arquivo.getSize()).thenReturn(10L);
        when(arquivo.getContentType()).thenReturn("image/png");
        when(arquivo.getInputStream()).thenThrow(new IOException("conteudoConfidencial"));
        var exception = assertThrows(FotografiaInvalidaException.class, () -> validador.validar(arquivo));
        assertFalse(exception.getMessage().contains("conteudoConfidencial"));
    }

    private MockMultipartFile arquivo(String tipo, byte[] conteudo) {
        return new MockMultipartFile("foto", "foto.png", tipo, conteudo);
    }

    private byte[] imagem(String formato) throws IOException {
        BufferedImage imagem = new BufferedImage(10, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(imagem, formato, saida));
        return saida.toByteArray();
    }
}
