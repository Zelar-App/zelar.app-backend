package app.zelar.ocorrencia.exception;

public class TransicaoStatusInvalidaException extends RuntimeException {

    public TransicaoStatusInvalidaException() {
        super("Transição de status inválida.");
    }
}
