package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The kind of code host a project reads, the project's key over the orchestrator's over {@link
 * #DEFAULT}.
 */
public record CodeHostChoice(String kind, HostSettings host) {

  static final String DEFAULT = "github";
  private static final String ROOT = "orchestrator";

  /** What {@code jagt.yml} chose for {@code project}; a failure says why it could not be read. */
  public static Answer<CodeHostChoice> read(ConfigService configService, String project) {
    try {
      ConfigFile config = configService.load();
      ProjectConfig declared = config.projects().get(project);
      if (declared == null) {
        return Answer.failed(TokenUsage.NONE, "no project '" + project + "' in jagt.yml");
      }
      return new Answer<>(Optional.of(of(config, project, declared)), TokenUsage.NONE);
    } catch (RuntimeException unreadable) {
      return Answer.failed(
          TokenUsage.NONE, "jagt.yml not read: " + OneLine.clipped(unreadable.getMessage()));
    }
  }

  /**
   * {@code question} asked of the driver whose kinds hold what {@code jagt.yml} chose for {@code
   * project}.
   */
  public static <D, T> Answer<T> ask(
      ConfigService configService,
      String project,
      List<D> drivers,
      Function<D, Set<String>> kinds,
      BiFunction<D, HostSettings, Answer<T>> question) {
    Answer<CodeHostChoice> read = read(configService, project);
    if (read.facts().isEmpty()) {
      return Answer.failed(TokenUsage.NONE, read.failure());
    }
    CodeHostChoice choice = read.facts().get();
    return drivers.stream()
        .filter(driver -> kinds.apply(driver).contains(choice.kind()))
        .findFirst()
        .map(driver -> question.apply(driver, choice.host()))
        .orElseGet(() ->
            Answer.failed(
                TokenUsage.NONE,
                choice.host().selectedBy()
                    + ": '"
                    + choice.kind()
                    + "' is not a code host jagt knows"));
  }

  public static CodeHostChoice of(ConfigFile config, String project, ProjectConfig declared) {
    String selectedBy = ROOT + ".projects." + project + ".codeHost";
    String kind = declared.codeHost();
    if (kind == null || kind.isBlank()) {
      selectedBy = ROOT + ".codeHost";
      kind = config.codeHost();
    }
    if (kind == null || kind.isBlank()) {
      kind = declared.gitRemote().local() ? ProjectConfig.NO_CODE_HOST : DEFAULT;
    }
    return new CodeHostChoice(
        kind.strip().toLowerCase(Locale.ROOT),
        new HostSettings(
            project,
            selectedBy,
            Path.of(String.valueOf(declared.path())),
            withBots(declared.host(), config.codeReview().reviewBotsOrEmpty())));
  }

  private static ProjectConfig.CodeHost withBots(ProjectConfig.CodeHost host, List<String> shared) {
    ProjectConfig.Review own = host.review();
    List<String> bots =
        Stream.concat(shared.stream(), own.reviewBots().stream()).distinct().toList();
    return new ProjectConfig.CodeHost(
        host.kind(),
        host.name(),
        host.pipelineCommand(),
        host.retryCommand(),
        host.loginCommand(),
        host.closeCommand(),
        host.notesCommand(),
        new ProjectConfig.Review(
            own.awaitingCommand(), own.changeCommand(), own.postCommand(), bots));
  }
}
