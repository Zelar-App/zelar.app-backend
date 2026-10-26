package app.zelar.ocorrencia.entity;

public enum StatusOcorrencia {

    ENVIADA,
    EM_ANALISE,
    RESOLVIDA;

    public boolean permiteTransicaoPara(StatusOcorrencia destino) {
        return this == ENVIADA && destino == EM_ANALISE
                || this == EM_ANALISE && destino == RESOLVIDA;
    }

}
