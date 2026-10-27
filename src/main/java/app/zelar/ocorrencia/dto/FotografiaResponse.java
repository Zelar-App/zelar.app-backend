package app.zelar.ocorrencia.dto;

import app.zelar.ocorrencia.entity.Anexo;

import java.util.UUID;

public record FotografiaResponse(UUID id, String tipoConteudo, long tamanhoBytes, int largura, int altura) {
    public static FotografiaResponse from(Anexo anexo) {
        return new FotografiaResponse(anexo.getId(), anexo.getTipoConteudo(),
                anexo.getTamanhoBytes(), anexo.getLargura(), anexo.getAltura());
    }
}
