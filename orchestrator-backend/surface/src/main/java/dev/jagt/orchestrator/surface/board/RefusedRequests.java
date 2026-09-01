package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.flow.Refusal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RefusedRequests {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> refused(RuntimeException e) {
        String message = e.getMessage() == null ? "refused" : e.getMessage();
        return ResponseEntity.badRequest().body(e instanceof Refusal refusal
                ? Map.of("error", message, "code", refusal.code().name())
                : Map.of("error", message));
    }
}
