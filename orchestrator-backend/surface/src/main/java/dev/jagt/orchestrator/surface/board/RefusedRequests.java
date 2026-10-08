package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.flow.Refusal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class RefusedRequests {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> refused(RuntimeException e) {
        String message = e.getMessage() == null ? "refused" : e.getMessage();
        return ResponseEntity.badRequest().body(e instanceof Refusal refusal
                ? Map.of("error", message, "code", refusal.code().name())
                : Map.of("error", message));
    }

    /** A request the framework could not bind answers in the same shape as a refusal. */
    @ExceptionHandler({ServletRequestBindingException.class, HttpMessageNotReadableException.class,
            HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> malformed(Exception e, HttpServletRequest request) {
        log.atWarn().setMessage("request refused")
                .addKeyValue("url", request.getRequestURI())
                .addKeyValue("cause", e.getMessage())
                .log();
        String said = e instanceof HttpMessageNotReadableException ? "the request body is not readable JSON"
                : e.getMessage();
        return ResponseEntity.status(e instanceof ErrorResponse framework ? framework.getStatusCode()
                : HttpStatus.BAD_REQUEST).body(Map.of("error", said));
    }
}
