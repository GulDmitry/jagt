package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Reads and re-runs a pipeline through the code host's own CLI, signed in as whoever runs jagt. */
@Component
@ConditionalOnExpression("'${orchestrator.agent.cli:claude}' != 'replay'")
@RequiredArgsConstructor
public class CliPipelineRetry implements PipelineRetry {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Set<String> NOT_SIGNED_IN = Set.of("401", "not logged in", "auth login");
    private static final Set<String> RAN_FINE = Set.of("success", "skipped", "neutral");
    private static final CommitPipeline NO_PIPELINE = new CommitPipeline("none", "");
    private static final JsonMapper JSON = new JsonMapper();

    private final Processes processes;

    @Override
    public Answer<List<FailedJob>> failedJobs(String pipelineUrl) {
        return PipelineAddress.parse(pipelineUrl).map(address -> switch (address.kind()) {
            case GITLAB -> parsed(run(address, List.of("api", "--hostname", address.hostname(),
                    "projects/" + encoded(address) + "/pipelines/" + address.id() + "/jobs?scope[]=failed&per_page=100")),
                    "glab", CliPipelineRetry::gitlabJobs);
            case GITHUB -> parsed(run(address, List.of("run", "view", address.id(), "--json", "conclusion,jobs",
                    "-R", address.project())), "gh", output -> githubJobs(address, output));
        }).orElseGet(() -> notAPipeline(pipelineUrl));
    }

    @Override
    public Answer<String> retryFailed(String pipelineUrl) {
        return PipelineAddress.parse(pipelineUrl).map(address -> switch (address.kind()) {
            case GITLAB -> run(address, List.of("api", "--hostname", address.hostname(), "-X", "POST",
                    "projects/" + encoded(address) + "/pipelines/" + address.id() + "/retry"));
            case GITHUB -> run(address, List.of("run", "rerun", address.id(), "--failed", "-R", address.project()));
        }).orElseGet(() -> notAPipeline(pipelineUrl));
    }

    @Override
    public Answer<CommitPipeline> onCommit(String remoteUrl, String sha) {
        return PipelineAddress.ofRemote(remoteUrl, sha).map(address -> switch (address.kind()) {
            case GITLAB -> parsed(run(address, List.of("api", "--hostname", address.hostname(),
                    "projects/" + encoded(address) + "/pipelines?sha=" + address.id() + "&per_page=1")),
                    "glab", CliPipelineRetry::gitlabPipeline);
            case GITHUB -> parsed(run(address, List.of("run", "list", "--commit", address.id(), "--json",
                    "status,conclusion,url", "--limit", "100", "-R", address.project())),
                    "gh", CliPipelineRetry::githubPipeline);
        }).orElseGet(() -> new Answer<>(Optional.of(CommitPipeline.unhosted()), TokenUsage.NONE));
    }

    private Answer<String> run(PipelineAddress address, List<String> arguments) {
        String cli = address.kind().cli();
        List<String> command = new ArrayList<>(List.of(Executables.resolve(cli)));
        command.addAll(arguments);
        try {
            Processes.Result result = processes.run(null, TIMEOUT, command);
            if (result.exitCode() == 0) {
                return new Answer<>(Optional.of(result.stdout().isBlank() ? result.stderr() : result.stdout()),
                        TokenUsage.NONE);
            }
            return Answer.failed(TokenUsage.NONE, refusal(cli, address.hostname(), result));
        } catch (IllegalStateException e) {
            return Answer.failed(TokenUsage.NONE, cli + " did not run: " + OneLine.clipped(e.getMessage()));
        }
    }

    private static String refusal(String cli, String hostname, Processes.Result result) {
        String said = OneLine.clipped(result.stderr().isBlank() ? result.stdout() : result.stderr());
        String lower = said == null ? "" : said.toLowerCase(java.util.Locale.ROOT);
        if (NOT_SIGNED_IN.stream().anyMatch(lower::contains)) {
            return cli + " not logged in to " + hostname;
        }
        return cli + " exited " + result.exitCode() + (lower.isEmpty() ? "" : ": " + said);
    }

    private static <T> Answer<T> parsed(Answer<String> output, String cli, Function<String, T> reader) {
        if (output.facts().isEmpty()) {
            return Answer.failed(TokenUsage.NONE, output.failure());
        }
        try {
            return new Answer<>(Optional.of(reader.apply(output.facts().get())), TokenUsage.NONE);
        } catch (JacksonException e) {
            return Answer.failed(TokenUsage.NONE, cli + " gave output that is not JSON");
        }
    }

    private static CommitPipeline gitlabPipeline(String output) {
        JsonNode newest = JSON.readTree(output).path(0);
        return newest.isMissingNode() ? NO_PIPELINE
                : new CommitPipeline(newest.path("status").asString(""), newest.path("web_url").asString(""));
    }

    private static CommitPipeline githubPipeline(String output) {
        CommitPipeline worst = NO_PIPELINE;
        for (JsonNode run : JSON.readTree(output)) {
            String status = run.path("status").asString("");
            String said = "completed".equals(status) ? run.path("conclusion").asString("") : status;
            CommitPipeline read = new CommitPipeline(RAN_FINE.contains(said) ? "success" : said, run.path("url").asString(""));
            if (worst == NO_PIPELINE || Pipeline.of(read.status()).severity() < Pipeline.of(worst.status()).severity()) {
                worst = read;
            }
        }
        return worst;
    }

    private static List<FailedJob> gitlabJobs(String output) {
        List<FailedJob> jobs = new ArrayList<>();
        JSON.readTree(output).forEach(job -> jobs.add(new FailedJob(job.path("id").asString(""),
                job.path("name").asString(""), job.path("failure_reason").asString(""))));
        return jobs;
    }

    private static List<FailedJob> githubJobs(PipelineAddress address, String output) {
        JsonNode run = JSON.readTree(output);
        if ("startup_failure".equals(run.path("conclusion").asString(""))) {
            return List.of(new FailedJob(address.id(), "workflow", "startup_failure"));
        }
        List<FailedJob> jobs = new ArrayList<>();
        run.path("jobs").forEach(job -> {
            String conclusion = job.path("conclusion").asString("");
            if (!conclusion.isBlank() && !RAN_FINE.contains(conclusion)) {
                jobs.add(new FailedJob(job.path("databaseId").asString(""), job.path("name").asString(""),
                        conclusion));
            }
        });
        return jobs;
    }

    private static String encoded(PipelineAddress address) {
        return URLEncoder.encode(address.project(), StandardCharsets.UTF_8);
    }

    private static <T> Answer<T> notAPipeline(String url) {
        return Answer.failed(TokenUsage.NONE, "not the address of a pipeline: " + OneLine.clipped(url));
    }
}
