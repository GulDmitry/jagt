package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CodeHost;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.List;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Asks the driver of the code host the project's {@code jagt.yml} chose, read on every call. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChosenCodeHost implements CodeHost {

  private final ConfigService configService;
  private final List<CodeHostDriver> drivers;

  @Override
  public Answer<List<FailedJob>> failedJobs(String project, String pipelineUrl) {
    return ask(project, (driver, host) -> driver.failedJobs(host, pipelineUrl));
  }

  @Override
  public Answer<String> retryFailed(String project, String pipelineUrl) {
    return ask(project, (driver, host) -> driver.retryFailed(host, pipelineUrl));
  }

  @Override
  public Answer<String> closeRequest(
      String project, @Nullable String remoteUrl, String requestUrl) {
    return ask(
        project,
        (driver, host) ->
            NoCodeHost.ID.equals(driver.id())
                ? driver.closeRequest(host, requestUrl)
                : LinkRefusal.of(remoteUrl, host.host().name(), requestUrl)
                    .map(refusal -> refused(project, requestUrl, refusal))
                    .orElseGet(() -> driver.closeRequest(host, requestUrl)));
  }

  @Override
  public Answer<List<String>> unresolvedNotes(String project, String requestUrl) {
    return ask(project, (driver, host) -> driver.unresolvedNotes(host, requestUrl));
  }

  @Override
  public Answer<String> requestFingerprint(String project, String requestUrl) {
    return ask(project, (driver, host) -> driver.requestFingerprint(host, requestUrl));
  }

  @Override
  public Answer<CommitPipeline> onCommit(String project, String remoteUrl, String sha) {
    return ask(project, (driver, host) -> driver.onCommit(host, remoteUrl, sha));
  }

  @Override
  public Answer<CommitPipeline> onBranchSince(
      String project, String remoteUrl, String branch, String sha) {
    return ask(project, (driver, host) -> driver.onBranchSince(host, remoteUrl, branch, sha));
  }

  private static Answer<String> refused(String project, String requestUrl, LinkRefusal refusal) {
    log.atWarn()
        .setMessage("code host write refused")
        .addKeyValue("project", project)
        .addKeyValue("request", requestUrl)
        .addKeyValue("cause", refusal)
        .addKeyValue("effect", "nothing was sent to the host")
        .log();
    return Answer.failed(TokenUsage.NONE, refusal.said());
  }

  private <T> Answer<T> ask(
      String project, BiFunction<CodeHostDriver, HostSettings, Answer<T>> question) {
    CodeHostChoice choice;
    try {
      ConfigService.ConfigFile config = configService.load();
      ProjectConfig declared = config.projects().get(project);
      if (declared == null) {
        return Answer.failed(TokenUsage.NONE, "no project '" + project + "' in jagt.yml");
      }
      choice = CodeHostChoice.of(config, project, declared);
    } catch (RuntimeException unreadable) {
      return Answer.failed(
          TokenUsage.NONE, "jagt.yml not read: " + OneLine.clipped(unreadable.getMessage()));
    }
    return drivers.stream()
        .filter(driver -> driver.id().equals(choice.kind()))
        .findFirst()
        .map(driver -> question.apply(driver, choice.host()))
        .orElseGet(
            () ->
                Answer.failed(
                    TokenUsage.NONE,
                    choice.host().selectedBy()
                        + ": '"
                        + choice.kind()
                        + "' is not a code host jagt knows"));
  }
}
