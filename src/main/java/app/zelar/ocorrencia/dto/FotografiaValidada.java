package app.zelar.ocorrencia.dto;

import org.springframework.util.Assert;

import java.util.Objects;

public record FotografiaValidada(byte[] conteudo, String tipoConteudo, String extensao, int largura, int altura) {

    public static final int TAMANHO_MAXIMO_BYTES = 5 * 1024 * 1024;

    public FotografiaValidada {
        Objects.requireNonNull(conteudo, "O conteúdo é obrigatório.");
        Assert.isTrue(conteudo.length > 0 && conteudo.length <= TAMANHO_MAXIMO_BYTES,
                "O conteúdo deve possuir entre 1 byte e 5 MiB.");
        Assert.isTrue(("image/jpeg".equals(tipoConteudo) && "jpg".equals(extensao))
                        || ("image/png".equals(tipoConteudo) && "png".equals(extensao)),
                "O tipo e a extensão devem corresponder a JPEG ou PNG.");
        Assert.isTrue(largura > 0 && altura > 0, "As dimensões devem ser positivas.");
        conteudo = conteudo.clone();
    }

    @Override
    public byte[] conteudo() {
        return conteudo.clone();
    }

    public long tamanhoBytes() {
        return conteudo.length;
    }
}
