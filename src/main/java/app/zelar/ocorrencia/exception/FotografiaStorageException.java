package app.zelar.ocorrencia.exception;

public class FotografiaStorageException extends RuntimeException {

    public FotografiaStorageException() {
        super("Não foi possível acessar o armazenamento de fotografias.");
    }
}
