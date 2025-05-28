package bank.pf.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private ProblemDetail createProblemDetail(HttpStatus status, String type, String title, String detail, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setType(URI.create("https://api.bank.pf/errors/" + type));
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        return problemDetail;
    }

    private ResponseEntity<ProblemDetail> createProblemResponse(HttpStatus status, String type, String title, String detail, HttpServletRequest request) {
        return ResponseEntity.status(status).body(createProblemDetail(status, type, title, detail, request));
    }

    private ResponseEntity<ProblemDetail> createProblemResponse(HttpServletRequest request, Map<String, Object> additionalProperties) {
        ProblemDetail problemDetail = createProblemDetail(HttpStatus.BAD_REQUEST, "validation", "Erro de validação", "A solicitação contém erros de validação", request);
        additionalProperties.forEach(problemDetail::setProperty);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(ValidationException ex, HttpServletRequest request) {
        log.warn("Falha na validação da solicitação: {}", ex.getErrors());

        return createProblemResponse(
                request,
                Map.of("errors", ex.getErrors())
        );
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleApplicationNotFoundException(ApplicationNotFoundException ex, HttpServletRequest request) {
        log.warn("Solicitação não encontrada: {}", ex.getMessage());

        return createProblemResponse(
                HttpStatus.NOT_FOUND,
                "not-found",
                "Recurso não encontrado",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(ExecutionException.class)
    public ResponseEntity<ProblemDetail> handleExecutionException(ExecutionException ex, HttpServletRequest request) {
        log.error("Erro de execução concorrente: {}", ex.getMessage());

        return createProblemResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "concurrency",
                "Erro de processamento concorrente",
                "Erro interno ao processar a solicitação",
                request
        );
    }

    // Add a handler for thread interruption
    @ExceptionHandler(value = {java.lang.InterruptedException.class})
    public ResponseEntity<ProblemDetail> handleInterruptedException(Exception ex, HttpServletRequest request) {
        log.error("Operação interrompida: {}", ex.getMessage());
        Thread.currentThread().interrupt(); // Restore interrupted status

        return createProblemResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "interruption",
                "Operação interrompida",
                "A operação foi interrompida durante o processamento",
                request
        );
    }

    @ExceptionHandler(EventPublishingFailedException.class)
    public ResponseEntity<ProblemDetail> handleEventPublishingFailedException(EventPublishingFailedException ex, HttpServletRequest request) {
        log.error("Falha ao publicar evento: {}", ex.getMessage(), ex);

        return createProblemResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "event-publishing",
                "Falha na publicação de evento",
                "Erro crítico ao processar a solicitação e publicar evento",
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        log.warn("Erro de validação de argumentos: {}", errors);

        return createProblemResponse(
                request,
                Map.of("errors", errors)
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado: {}", ex.getMessage(), ex);

        return createProblemResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal",
                "Erro interno do servidor",
                "Ocorreu um erro inesperado",
                request
        );
    }
}
