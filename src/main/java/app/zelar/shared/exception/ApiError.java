package app.zelar.shared.exception;

import java.time.LocalDateTime;

public record ApiError(
        int status,
        String erro,
        String mensagem,
        String caminho,
        LocalDateTime timestamp
) {
}