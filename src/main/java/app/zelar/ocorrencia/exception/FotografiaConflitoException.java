package app.zelar.ocorrencia.exception;

public class FotografiaConflitoException extends RuntimeException {
    public FotografiaConflitoException() {
        super("Já existe uma fotografia ou um envio pendente para esta ocorrência.");
    }
}
