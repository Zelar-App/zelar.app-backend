package app.zelar.shared.exception;

import app.zelar.ocorrencia.exception.TransicaoStatusInvalidaException;
import app.zelar.ocorrencia.exception.FotografiaInvalidaException;
import app.zelar.ocorrencia.exception.FotografiaConflitoException;
import app.zelar.ocorrencia.exception.FotografiaStorageException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import app.zelar.usuario.exception.EmailJaCadastradoException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FotografiaInvalidaException.class)
    public ResponseEntity<ApiError> handleFotografiaInvalida(
            FotografiaInvalidaException exception,
            HttpServletRequest request
    ) {
        return criarResposta(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(FotografiaConflitoException.class)
    public ResponseEntity<ApiError> handleFotografiaConflitante(
            FotografiaConflitoException exception,
            HttpServletRequest request
    ) {
        return criarResposta(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(FotografiaStorageException.class)
    public ResponseEntity<ApiError> handleStorageIndisponivel(
            FotografiaStorageException exception,
            HttpServletRequest request
    ) {
        return criarResposta(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleArquivoAusente(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        return criarResposta(HttpStatus.BAD_REQUEST, "Envie a fotografia no campo 'foto'.", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadExcedido(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return criarResposta(HttpStatus.PAYLOAD_TOO_LARGE, "A fotografia deve possuir no máximo 5 MiB.", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleCredenciaisInvalidas(
            BadCredentialsException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(new ApiError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                        exception.getMessage(), request.getRequestURI(), LocalDateTime.now()));
    }

    @ExceptionHandler(TransicaoStatusInvalidaException.class)
    public ResponseEntity<ApiError> handleTransicaoStatusInvalida(
            TransicaoStatusInvalidaException exception,
            HttpServletRequest request
    ) {
        return criarResposta(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ApiError> handleEmailJaCadastrado(
            EmailJaCadastradoException exception,
            HttpServletRequest request
    ) {
        return criarResposta(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ApiError> handleRecursoNaoEncontrado(
            RecursoNaoEncontradoException exception,
            HttpServletRequest request
    ) {
        return criarResposta(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleDadosInvalidos(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String mensagem = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .sorted()
                .distinct()
                .collect(Collectors.joining("; "));

        return criarResposta(
                HttpStatus.BAD_REQUEST,
                mensagem,
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleParametroInvalido(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return criarResposta(
                HttpStatus.BAD_REQUEST,
                "Parâmetro '" + exception.getName() + "' inválido.",
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleCorpoInvalido(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return criarResposta(
                HttpStatus.BAD_REQUEST,
                "Corpo da requisição ausente ou inválido. Envie um JSON válido.",
                request
        );
    }

    private ResponseEntity<ApiError> criarResposta(
            HttpStatus status,
            String mensagem,
            HttpServletRequest request
    ) {
        ApiError erro = new ApiError(
                status.value(),
                status.getReasonPhrase(),
                mensagem,
                request.getRequestURI(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(status).body(erro);
    }
}
