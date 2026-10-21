package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.PressLog;
import dev.jagt.orchestrator.service.PressLog.Interaction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class InteractionsController {

    public record Batch(List<Interaction> presses) {
    }

    private final PressLog presses;
    private final Clock clock;

    @PostMapping("/api/interactions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@RequestBody Batch batch) {
        presses.record(batch.presses() == null ? List.of() : batch.presses(), clock.millis());
    }
}
