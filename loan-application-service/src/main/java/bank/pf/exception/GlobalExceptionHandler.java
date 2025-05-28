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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(ValidationException ex, HttpServletRequest request) {
        log.warn("Falha na validação da solicitação: {}", ex.getErrors());

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setType(URI.create("https://api.bank.pf/errors/validation"));
        problemDetail.setTitle("Erro de validação");
        problemDetail.setDetail("A solicitação contém erros de validação");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("errors", ex.getErrors());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleApplicationNotFoundException(ApplicationNotFoundException ex, HttpServletRequest request) {
        log.warn("Solicitação não encontrada: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setType(URI.create("https://api.bank.pf/errors/not-found"));
        problemDetail.setTitle("Recurso não encontrado");
        problemDetail.setDetail(ex.getMessage());
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(ExecutionException.class)
    public ResponseEntity<ProblemDetail> handleExecutionException(ExecutionException ex, HttpServletRequest request) {
        log.error("Erro de execução concorrente: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setType(URI.create("https://api.bank.pf/errors/concurrency"));
        problemDetail.setTitle("Erro de processamento concorrente");
        problemDetail.setDetail("Erro interno ao processar a solicitação");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    // Add a handler for thread interruption
    @ExceptionHandler(value = {java.lang.InterruptedException.class})
    public ResponseEntity<ProblemDetail> handleInterruptedException(Exception ex, HttpServletRequest request) {
        log.error("Operação interrompida: {}", ex.getMessage());
        Thread.currentThread().interrupt(); // Restore interrupted status

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setType(URI.create("https://api.bank.pf/errors/interruption"));
        problemDetail.setTitle("Operação interrompida");
        problemDetail.setDetail("A operação foi interrompida durante o processamento");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    @ExceptionHandler(EventPublishingFailedException.class)
    public ResponseEntity<ProblemDetail> handleEventPublishingFailedException(EventPublishingFailedException ex, HttpServletRequest request) {
        log.error("Falha ao publicar evento: {}", ex.getMessage(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setType(URI.create("https://api.bank.pf/errors/event-publishing"));
        problemDetail.setTitle("Falha na publicação de evento");
        problemDetail.setDetail("Erro crítico ao processar a solicitação e publicar evento");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        log.warn("Erro de validação de argumentos: {}", errors);

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setType(URI.create("https://api.bank.pf/errors/validation"));
        problemDetail.setTitle("Erro de validação");
        problemDetail.setDetail("A solicitação contém erros de validação");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("errors", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado: {}", ex.getMessage(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setType(URI.create("https://api.bank.pf/errors/internal"));
        problemDetail.setTitle("Erro interno do servidor");
        problemDetail.setDetail("Ocorreu um erro inesperado");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}
