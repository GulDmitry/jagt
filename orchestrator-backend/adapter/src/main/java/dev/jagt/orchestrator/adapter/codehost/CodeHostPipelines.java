package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;

/** Asks the driver of the code host the project's {@code jagt.yml} chose, read on every call. */
@Component
@RequiredArgsConstructor
public class CodeHostPipelines implements PipelineRetry {

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
    public Answer<CommitPipeline> onCommit(String project, String remoteUrl, String sha) {
        return ask(project, (driver, host) -> driver.onCommit(host, remoteUrl, sha));
    }

    private <T> Answer<T> ask(String project, BiFunction<CodeHostDriver, HostSettings, Answer<T>> question) {
        CodeHostChoice choice;
        try {
            ConfigService.ConfigFile config = configService.load();
            ProjectConfig declared = config.projects().get(project);
            if (declared == null) {
                return Answer.failed(TokenUsage.NONE, "no project '" + project + "' in jagt.yml");
            }
            choice = CodeHostChoice.of(config, project, declared);
        } catch (RuntimeException unreadable) {
            return Answer.failed(TokenUsage.NONE, "jagt.yml not read: " + OneLine.clipped(unreadable.getMessage()));
        }
        return drivers.stream().filter(driver -> driver.id().equals(choice.kind())).findFirst()
                .map(driver -> question.apply(driver, choice.host()))
                .orElseGet(() -> Answer.failed(TokenUsage.NONE, choice.host().selectedBy() + ": '" + choice.kind()
                        + "' is not a code host jagt knows"));
    }
}
