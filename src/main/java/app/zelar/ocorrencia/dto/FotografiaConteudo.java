package app.zelar.ocorrencia.dto;

public record FotografiaConteudo(String tipoConteudo, byte[] conteudo) {
    public FotografiaConteudo {
        conteudo = conteudo.clone();
    }

    @Override
    public byte[] conteudo() {
        return conteudo.clone();
    }
}
