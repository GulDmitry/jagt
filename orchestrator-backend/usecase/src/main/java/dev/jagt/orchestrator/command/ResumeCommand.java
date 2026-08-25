package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.service.TaskLauncher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ResumeCommand implements GlobalCommand {

    private final TaskLauncher launcher;

    @Override
    public String id() {
        return "resume";
    }

    @Override
    public String hint() {
        return "reopened request: take over its branch and its commits, and link it — no second request";
    }

    @Override
    public List<String> usage() {
        return List.of("resume <request-url>");
    }

    /** The request names its own branches, so anything typed beside its URL could only contradict it. */
    @Override
    public String run(String tail) {
        List<String> tokens = Arrays.stream(tail.split("\\s+")).filter(token -> !token.isBlank()).toList();
        String url = tokens.stream().filter(token -> token.startsWith("http")).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("usage: resume <request-url>"));
        if (tokens.size() > 1) {
            throw new IllegalArgumentException("usage: resume <request-url> — the request carries its own branches;"
                    + " to start a NEW task on a new branch use `do <ticket>`");
        }
        return launcher.resume(url).message();
    }
}
