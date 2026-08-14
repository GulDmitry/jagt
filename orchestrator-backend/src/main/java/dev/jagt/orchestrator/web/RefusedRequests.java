package dev.jagt.orchestrator.web;

import dev.jagt.orchestrator.service.Refusal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Every refusal a human can cause — an action that is not legal now, an unknown project, a task closed in
 * another tab — is a 400 with the sentence, not a stack trace. A caller that must ACT on the reason gets a
 * {@link Refusal.Code} beside it.
 */
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
