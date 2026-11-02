package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.TokenUsage;
import tools.jackson.core.JacksonException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** A host's own CLI, signed in as whoever runs jagt. */
final class HostCli {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Set<String> NOT_SIGNED_IN = Set.of("401", "not logged in", "auth login");

    private final Processes processes;
    private final String cli;

    HostCli(Processes processes, String cli) {
        this.processes = processes;
        this.cli = cli;
    }

    Answer<String> run(String hostname, List<String> arguments) {
        List<String> command = new ArrayList<>(List.of(Executables.resolve(cli)));
        command.addAll(arguments);
        try {
            Processes.Result result = processes.run(null, TIMEOUT, command);
            if (result.exitCode() == 0) {
                return new Answer<>(Optional.of(result.stdout().isBlank() ? result.stderr() : result.stdout()),
                        TokenUsage.NONE);
            }
            if (result.stderr().contains("404") && !signedIn(hostname)) {
                return Answer.failed(TokenUsage.NONE, cli + " not logged in to " + hostname);
            }
            return Answer.failed(TokenUsage.NONE, refusal(hostname, result));
        } catch (IllegalStateException e) {
            return Answer.failed(TokenUsage.NONE, cli + " did not run: " + OneLine.clipped(e.getMessage()));
        }
    }

    <T> Answer<T> parsed(Answer<String> output, Function<String, T> reader) {
        if (output.facts().isEmpty()) {
            return Answer.failed(TokenUsage.NONE, output.failure());
        }
        try {
            return new Answer<>(Optional.of(reader.apply(output.facts().get())), TokenUsage.NONE);
        } catch (JacksonException e) {
            return Answer.failed(TokenUsage.NONE, cli + " gave output that is not JSON");
        }
    }

    List<Problem> missing(String key) {
        return Executables.unresolved(Executables.resolve(cli))
                ? List.of(new Problem(key, key + ": codeHost needs `" + cli + "`, which is not on PATH nor in the usual"
                        + " install directories. Install it, or choose another codeHost."))
                : List.of();
    }

    static <T> Answer<T> notAPipeline(String url) {
        return Answer.failed(TokenUsage.NONE, "not the address of a pipeline: " + OneLine.clipped(url));
    }

    // A host hides a private project from a CLI signed out of it behind the same 404 as one that does not exist.
    private boolean signedIn(String hostname) {
        return processes.run(null, TIMEOUT, List.of(Executables.resolve(cli), "auth", "status", "--hostname", hostname))
                .exitCode() == 0;
    }

    private String refusal(String hostname, Processes.Result result) {
        String said = OneLine.clipped(result.stderr().isBlank() ? result.stdout() : result.stderr());
        String lower = said == null ? "" : said.toLowerCase(Locale.ROOT);
        if (NOT_SIGNED_IN.stream().anyMatch(lower::contains)) {
            return cli + " not logged in to " + hostname;
        }
        return cli + " exited " + result.exitCode() + (lower.isEmpty() ? "" : ": " + said);
    }
}
