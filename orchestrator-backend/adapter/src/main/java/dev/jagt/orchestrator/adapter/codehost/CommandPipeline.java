package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Any provider: the project's own command prints the state of a commit's pipeline and holds whatever credential it needs. */
@Component
@RequiredArgsConstructor
public class CommandPipeline implements CodeHostDriver {

    static final String ID = "command";
    private static final int SIGNED_OUT = 77;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String VOCABULARY = "success | failed | running | none | unknown";
    private static final Set<String> STATES = Set.of("success", "failed", "running", "none", "unknown");

    private final Processes processes;
    private final Reach reach;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Answer<List<FailedJob>> failedJobs(HostSettings host, String pipelineUrl) {
        return new Answer<>(Optional.of(List.of()), TokenUsage.NONE);
    }

    @Override
    public Answer<String> retryFailed(HostSettings host, String pipelineUrl) {
        if (host.retryCommand().isEmpty()) {
            return Answer.failed(TokenUsage.NONE, host.key("retryCommand") + " is not set");
        }
        return run(host, "retryCommand", host.retryCommand(), "{pipeline}", pipelineUrl);
    }

    @Override
    public Answer<CommitPipeline> onCommit(HostSettings host, String remoteUrl, String sha) {
        if (host.pipelineCommand().isEmpty()) {
            return Answer.failed(TokenUsage.NONE, host.key("pipelineCommand") + " is not set");
        }
        Answer<String> printed = run(host, "pipelineCommand", host.pipelineCommand(), "{sha}", sha);
        if (printed.facts().isEmpty()) {
            return new Answer<>(Optional.empty(), TokenUsage.NONE, printed.failure(), false, printed.reach());
        }
        String state = printed.facts().get().strip().toLowerCase(Locale.ROOT);
        return STATES.contains(state) ? new Answer<>(Optional.of(new CommitPipeline(state, "")), TokenUsage.NONE)
                : Answer.failed(TokenUsage.NONE, host.key("pipelineCommand") + " printed '"
                        + OneLine.clipped(state) + "', not one of " + VOCABULARY);
    }

    @Override
    public Answer<CommitPipeline> onBranchSince(HostSettings host, String remoteUrl, String branch, String sha) {
        return Answer.failed(TokenUsage.NONE, host.key("pipelineCommand") + " reads one commit, not a branch");
    }

    @Override
    public List<Problem> problems(HostSettings host) {
        String key = host.key("pipelineCommand");
        if (host.pipelineCommand().isEmpty()) {
            return List.of(new Problem(key, key + ": codeHost command needs a pipelineCommand printing the state of {sha}."));
        }
        return Executables.unresolved(Executables.resolve(host.pipelineCommand().getFirst()))
                ? List.of(new Problem(key, key + ": '" + host.pipelineCommand().getFirst()
                        + "' is not on PATH nor in the usual install directories."))
                : List.of();
    }

    private Answer<String> run(HostSettings host, String name, List<String> template, String placeholder, String value) {
        List<String> command = template.stream().map(part -> part.replace(placeholder, value)).toList();
        List<String> resolved = new ArrayList<>(command);
        resolved.set(0, Executables.resolve(command.getFirst()));
        try {
            Processes.Result result = processes.run(host.dir(), TIMEOUT, resolved);
            if (result.exitCode() == 0) {
                reach.reached(ID, host.project());
                return new Answer<>(Optional.of(result.stdout()), TokenUsage.NONE);
            }
            if (result.exitCode() == SIGNED_OUT) {
                return unreached(host, host.loginCommand(), host.key(name) + " says it is signed out");
            }
            String said = OneLine.clipped(result.stderr().isBlank() ? result.stdout() : result.stderr());
            return Answer.failed(TokenUsage.NONE, host.key(name) + " exited " + result.exitCode()
                    + (said == null || said.isEmpty() ? "" : ": " + said));
        } catch (IllegalStateException e) {
            if (Executables.unresolved(resolved.getFirst())) {
                return unreached(host, List.of(), host.key(name) + ": '" + command.getFirst() + "' is not installed");
            }
            return Answer.failed(TokenUsage.NONE, host.key(name) + " did not run: " + OneLine.clipped(e.getMessage()));
        }
    }

    private Answer<String> unreached(HostSettings host, List<String> fix, String said) {
        ReachLost lost = new ReachLost(ID, host.project(), fix, said);
        reach.lost(lost);
        return Answer.unreached(lost);
    }
}
