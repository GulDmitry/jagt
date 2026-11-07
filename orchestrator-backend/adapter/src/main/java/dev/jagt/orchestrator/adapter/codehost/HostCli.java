package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import tools.jackson.core.JacksonException;

/** A host's own CLI, signed in as whoever runs jagt. */
final class HostCli {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final Set<String> NOT_SIGNED_IN =
      Set.of("401", "403", "not logged in", "auth login");
  private static final String RATE_LIMITED = "rate limit";
  private static final Set<String> UNREACHABLE =
      Set.of(
          "no such host",
          "could not resolve",
          "connection refused",
          "network is unreachable",
          "dial tcp");

  private final Processes processes;
  private final Reach reach;
  private final String cli;
  private final Function<String, List<String>> login;

  /** {@code login} gives the arguments of the CLI's own interactive login on a host. */
  HostCli(Processes processes, Reach reach, String cli, Function<String, List<String>> login) {
    this.processes = processes;
    this.reach = reach;
    this.cli = cli;
    this.login = login;
  }

  Answer<String> run(String hostname, List<String> arguments) {
    List<String> command = new ArrayList<>(List.of(Executables.resolve(cli)));
    command.addAll(arguments);
    try {
      Processes.Result result = processes.run(null, TIMEOUT, command);
      if (result.exitCode() == 0) {
        reach.reached(cli, hostname);
        return new Answer<>(
            Optional.of(result.stdout().isBlank() ? result.stderr() : result.stdout()),
            TokenUsage.NONE);
      }
      if (result.stderr().contains("404") && !signedIn(hostname)) {
        return signedOut(hostname);
      }
      return refusal(hostname, result);
    } catch (IllegalStateException e) {
      if (Executables.unresolved(Executables.resolve(cli))) {
        return unreached(hostname, List.of(), cli + " is not installed");
      }
      return Answer.failed(
          TokenUsage.NONE, cli + " did not run: " + OneLine.clipped(e.getMessage()));
    }
  }

  <T> Answer<T> parsed(Answer<String> output, Function<String, T> reader) {
    if (output.facts().isEmpty()) {
      return new Answer<>(
          Optional.empty(), TokenUsage.NONE, output.failure(), false, output.reach());
    }
    try {
      return new Answer<>(Optional.of(reader.apply(output.facts().get())), TokenUsage.NONE);
    } catch (JacksonException e) {
      return Answer.failed(TokenUsage.NONE, cli + " gave output that is not JSON");
    }
  }

  /**
   * {@code next} asked of what {@code first} read; a first that read nothing ends it with its own
   * failure.
   */
  static <T, R> Answer<R> then(Answer<T> first, Function<T, Answer<R>> next) {
    return first.facts().isEmpty()
        ? new Answer<>(Optional.empty(), TokenUsage.NONE, first.failure(), false, first.reach())
        : next.apply(first.facts().get());
  }

  List<Problem> missing(String key) {
    return Executables.unresolved(Executables.resolve(cli))
        ? List.of(
            new Problem(
                key,
                key
                    + ": codeHost needs `"
                    + cli
                    + "`, which is not on PATH nor in the usual"
                    + " install directories. Install it, or choose another codeHost."))
        : List.of();
  }

  static <T> Answer<T> notAPipeline(String url) {
    return Answer.failed(TokenUsage.NONE, "not the address of a pipeline: " + OneLine.clipped(url));
  }

  static <T> Answer<T> notARequest(String url) {
    return Answer.failed(TokenUsage.NONE, "not the address of a request: " + OneLine.clipped(url));
  }

  // A host hides a private project from a CLI signed out of it behind the same 404 as one that does
  // not exist.
  private boolean signedIn(String hostname) {
    return processes
            .run(
                null,
                TIMEOUT,
                List.of(Executables.resolve(cli), "auth", "status", "--hostname", hostname))
            .exitCode()
        == 0;
  }

  private <T> Answer<T> signedOut(String hostname) {
    List<String> fix = new ArrayList<>(List.of(cli));
    fix.addAll(login.apply(hostname));
    return unreached(hostname, fix, cli + " not logged in to " + hostname);
  }

  private <T> Answer<T> unreached(String hostname, List<String> fix, String said) {
    ReachLost lost = new ReachLost(cli, hostname, fix, said);
    reach.lost(lost);
    return Answer.unreached(lost);
  }

  private Answer<String> refusal(String hostname, Processes.Result result) {
    String said = OneLine.clipped(result.stderr().isBlank() ? result.stdout() : result.stderr());
    String lower = said == null ? "" : said.toLowerCase(Locale.ROOT);
    if (NOT_SIGNED_IN.stream().anyMatch(lower::contains) && !lower.contains(RATE_LIMITED)) {
      return signedOut(hostname);
    }
    if (UNREACHABLE.stream().anyMatch(lower::contains)) {
      return unreached(hostname, List.of(), cli + " cannot reach " + hostname + ": " + said);
    }
    reach.reached(cli, hostname);
    return Answer.failed(
        TokenUsage.NONE,
        cli + " exited " + result.exitCode() + (lower.isEmpty() ? "" : ": " + said));
  }
}
