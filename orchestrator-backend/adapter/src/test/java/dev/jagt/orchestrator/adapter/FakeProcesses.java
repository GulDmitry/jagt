package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Processes;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class FakeProcesses implements Processes {

  private record Rule(List<String> parts, List<Result> results) {

    boolean matches(List<String> command) {
      return command.containsAll(parts);
    }
  }

  private static final Result UNANSWERED = new Result(1, "", "unanswered");

  private final List<Rule> rules = new ArrayList<>();
  private final List<List<String>> issued = new ArrayList<>();
  private final List<@Nullable Path> directories = new ArrayList<>();
  private @Nullable RuntimeException failure;

  public FakeProcesses answering(Result result, String... parts) {
    rules.add(new Rule(List.of(parts), List.of(result)));
    return this;
  }

  public FakeProcesses answeringInTurn(Result first, Result... then) {
    List<Result> results = new ArrayList<>(List.of(first));
    results.addAll(List.of(then));
    rules.add(new Rule(List.of(), results));
    return this;
  }

  public FakeProcesses failing(RuntimeException cause) {
    failure = cause;
    return this;
  }

  public List<List<String>> issued() {
    return issued;
  }

  public List<List<String>> issuedContaining(String... parts) {
    return issued.stream().filter(command -> command.containsAll(List.of(parts))).toList();
  }

  public List<String> command() {
    if (issued.size() != 1) {
      throw new AssertionError("expected one command, ran " + issued.size());
    }
    return issued.getFirst();
  }

  public List<@Nullable Path> directories() {
    return directories;
  }

  @Override
  public Result run(@Nullable Path workingDir, Duration timeout, List<String> command) {
    return answer(workingDir, command);
  }

  @Override
  public Result run(
      @Nullable Path workingDir, Duration timeout, Map<String, String> env, List<String> command) {
    return answer(workingDir, command);
  }

  @Override
  public Result run(
      @Nullable Path workingDir,
      Duration timeout,
      Map<String, String> env,
      String stdin,
      List<String> command) {
    return answer(workingDir, command);
  }

  @Override
  public Process runDetached(@Nullable Path workingDir, List<String> command) {
    throw new UnsupportedOperationException();
  }

  private Result answer(@Nullable Path workingDir, List<String> command) {
    directories.add(workingDir);
    List<List<String>> earlier = List.copyOf(issued);
    issued.add(command);
    if (failure != null) {
      throw failure;
    }
    return rules.reversed().stream()
        .filter(rule -> rule.matches(command))
        .findFirst()
        .map(rule ->
            rule.results()
                .get(
                    Math.min(
                        (int) earlier.stream().filter(rule::matches).count(),
                        rule.results().size() - 1)))
        .orElse(UNANSWERED);
  }
}
