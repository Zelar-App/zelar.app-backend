package app.zelar.ocorrencia.service;

import app.zelar.ocorrencia.dto.FotografiaValidada;
import app.zelar.ocorrencia.exception.FotografiaInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;

@Service
public class ValidadorFotografia {

    private static final int DIMENSAO_MAXIMA = 8192;
    private static final long PIXELS_MAXIMOS = 16_000_000;

    public FotografiaValidada validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new FotografiaInvalidaException("Envie uma fotografia não vazia.");
        }
        if (arquivo.getSize() > FotografiaValidada.TAMANHO_MAXIMO_BYTES) {
            throw tamanhoInvalido();
        }
        String tipoDeclarado = arquivo.getContentType();
        if (!"image/jpeg".equals(tipoDeclarado) && !"image/png".equals(tipoDeclarado)) {
            throw new FotografiaInvalidaException("Envie uma fotografia JPEG ou PNG.");
        }

        try (InputStream entrada = arquivo.getInputStream()) {
            byte[] conteudo = entrada.readNBytes(FotografiaValidada.TAMANHO_MAXIMO_BYTES + 1);
            if (conteudo.length > FotografiaValidada.TAMANHO_MAXIMO_BYTES) {
                throw tamanhoInvalido();
            }
            return validarConteudo(conteudo, tipoDeclarado);
        } catch (IOException exception) {
            throw new FotografiaInvalidaException("Não foi possível ler a fotografia.");
        }
    }

    private FotografiaValidada validarConteudo(byte[] conteudo, String tipoDeclarado) throws IOException {
        try (var entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(conteudo))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw new FotografiaInvalidaException("O conteúdo não corresponde a uma fotografia válida.");
            }
            ImageReader leitor = leitores.next();
            try {
                String formato = leitor.getFormatName().toLowerCase(Locale.ROOT);
                String tipoReal = switch (formato) {
                    case "png" -> "image/png";
                    case "jpeg", "jpg" -> "image/jpeg";
                    default -> throw new FotografiaInvalidaException("Envie uma fotografia JPEG ou PNG.");
                };
                if (!tipoReal.equals(tipoDeclarado)) {
                    throw new FotografiaInvalidaException("O tipo informado não corresponde ao conteúdo da fotografia.");
                }
                leitor.setInput(entrada, true, true);
                int largura = leitor.getWidth(0);
                int altura = leitor.getHeight(0);
                if (largura <= 0 || altura <= 0 || largura > DIMENSAO_MAXIMA || altura > DIMENSAO_MAXIMA
                        || (long) largura * altura > PIXELS_MAXIMOS) {
                    throw new FotografiaInvalidaException("A fotografia excede as dimensões permitidas: 8192 por lado e 16 milhões de pixels.");
                }

                var imagem = leitor.read(0);
                ByteArrayOutputStream saida = new ByteArrayOutputStream();
                String extensao = "image/png".equals(tipoReal) ? "png" : "jpg";
                if (imagem == null || !ImageIO.write(imagem, extensao, saida)) {
                    throw new FotografiaInvalidaException("Não foi possível preparar a fotografia.");
                }
                if (saida.size() > FotografiaValidada.TAMANHO_MAXIMO_BYTES) {
                    throw tamanhoInvalido();
                }
                return new FotografiaValidada(saida.toByteArray(), tipoReal, extensao, largura, altura);
            } finally {
                leitor.dispose();
            }
        }
    }

    private FotografiaInvalidaException tamanhoInvalido() {
        return new FotografiaInvalidaException("A fotografia deve possuir no máximo 5 MiB.");
    }
}
