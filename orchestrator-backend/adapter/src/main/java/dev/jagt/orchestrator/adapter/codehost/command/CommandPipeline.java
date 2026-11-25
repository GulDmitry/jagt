package dev.jagt.orchestrator.adapter.codehost.command;

import dev.jagt.orchestrator.adapter.codehost.CodeHostDriver;
import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.CommitPipeline;
import dev.jagt.orchestrator.port.codehost.RequestDraft;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.deploy.FailedJob;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Any provider: the project's own command prints the state of a commit's pipeline and holds
 * whatever credential it needs.
 */
@Component
public class CommandPipeline implements CodeHostDriver {

  public static final String ID = "command";
  private static final String VOCABULARY = "success | failed | running | none | unknown";
  private static final Set<String> STATES =
      Set.of("success", "failed", "running", "none", "unknown");

  private final CommandRun run;

  public CommandPipeline(Processes processes, Reach reach) {
    this.run = new CommandRun(processes, reach, ID);
  }

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
    if (host.host().retryCommand().isEmpty()) {
      return Answer.failed(TokenUsage.NONE, host.key("retryCommand") + " is not set");
    }
    return run.run(
        host, "retryCommand", host.host().retryCommand(), Map.of("{pipeline}", pipelineUrl));
  }

  @Override
  public Answer<String> closeRequest(HostSettings host, String requestUrl) {
    if (host.host().closeCommand().isEmpty()) {
      return Answer.failed(TokenUsage.NONE, host.key("closeCommand") + " is not set");
    }
    return run.run(
        host, "closeCommand", host.host().closeCommand(), Map.of("{request}", requestUrl));
  }

  @Override
  public Answer<String> openRequest(HostSettings host, String remoteUrl, RequestDraft draft) {
    return Answer.unavailable();
  }

  @Override
  public Answer<String> openRequestOf(HostSettings host, String remoteUrl, String branch) {
    return Answer.unavailable();
  }

  @Override
  public Answer<List<String>> unresolvedNotes(HostSettings host, String requestUrl) {
    if (host.host().notesCommand().isEmpty()) {
      return new Answer<>(Optional.of(List.of()), TokenUsage.NONE);
    }
    Answer<String> printed =
        run.run(host, "notesCommand", host.host().notesCommand(), Map.of("{request}", requestUrl));
    if (printed.facts().isEmpty()) {
      return new Answer<>(
          Optional.empty(), TokenUsage.NONE, printed.failure(), false, printed.reach());
    }
    return new Answer<>(
        Optional.of(
            printed.facts().get().lines().map(String::strip).filter(id -> !id.isEmpty()).toList()),
        TokenUsage.NONE);
  }

  @Override
  public Answer<String> requestFingerprint(HostSettings host, String requestUrl) {
    return Answer.unavailable();
  }

  @Override
  public Answer<CommitPipeline> onCommit(HostSettings host, String remoteUrl, String sha) {
    if (host.host().pipelineCommand().isEmpty()) {
      return Answer.failed(TokenUsage.NONE, host.key("pipelineCommand") + " is not set");
    }
    Answer<String> printed =
        run.run(host, "pipelineCommand", host.host().pipelineCommand(), Map.of("{sha}", sha));
    if (printed.facts().isEmpty()) {
      return new Answer<>(
          Optional.empty(), TokenUsage.NONE, printed.failure(), false, printed.reach());
    }
    String state = printed.facts().get().strip().toLowerCase(Locale.ROOT);
    return STATES.contains(state)
        ? new Answer<>(Optional.of(new CommitPipeline(state, "")), TokenUsage.NONE)
        : Answer.failed(
            TokenUsage.NONE,
            host.key("pipelineCommand")
                + " printed '"
                + OneLine.clipped(state)
                + "', not one of "
                + VOCABULARY);
  }

  @Override
  public Answer<CommitPipeline> onBranchSince(
      HostSettings host, String remoteUrl, String branch, String sha) {
    return Answer.failed(
        TokenUsage.NONE, host.key("pipelineCommand") + " reads one commit, not a branch");
  }

  @Override
  public List<Problem> problems(HostSettings host) {
    String key = host.key("pipelineCommand");
    if (host.host().pipelineCommand().isEmpty()) {
      return List.of(
          new Problem(
              key,
              key + ": codeHost command needs a pipelineCommand printing the state of {sha}."));
    }
    return Executables.unresolved(Executables.resolve(host.host().pipelineCommand().getFirst()))
        ? List.of(
            new Problem(
                key,
                key
                    + ": '"
                    + host.host().pipelineCommand().getFirst()
                    + "' is not on PATH nor in the usual install directories."))
        : List.of();
  }
}
