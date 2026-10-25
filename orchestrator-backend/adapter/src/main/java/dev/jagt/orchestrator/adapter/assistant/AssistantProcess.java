package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** The one way a headless CLI process runs: refused while the breaker is open, and its outcome fed back to it. */
@Component
@RequiredArgsConstructor
class AssistantProcess {

    private final ProcessRunner runner;
    private final AssistantBreaker breaker;

    Processes.Result run(Path cwd, Duration timeout, List<String> command) {
        breaker.admit();
        Processes.Result result;
        try {
            result = runner.run(cwd, timeout, command);
        } catch (RuntimeException unfinished) {
            breaker.inconclusive();
            throw unfinished;
        }
        ProviderFault.Reading reading = ProviderFault.of(result);
        switch (reading.verdict()) {
            case REACHED -> breaker.succeeded();
            case FAILED -> breaker.failed(reading.cause());
            case UNKNOWN -> breaker.inconclusive();
        }
        return result;
    }
}
