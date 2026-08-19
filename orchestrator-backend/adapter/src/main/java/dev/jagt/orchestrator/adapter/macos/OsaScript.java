package dev.jagt.orchestrator.adapter.macos;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OsaScript {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final ProcessRunner processRunner;

    public void run(String script) {
        processRunner.run(null, TIMEOUT, List.of("osascript", "-e", script)).expectSuccess("osascript");
    }

    public static String string(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
