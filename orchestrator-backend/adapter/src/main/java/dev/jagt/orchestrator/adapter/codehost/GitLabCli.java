package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.TokenUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads and re-runs a pipeline through {@code glab api}, on the host the remote or the configured name gives. */
@Component
@ConditionalOnExpression("'${orchestrator.agent.cli:claude}' != 'replay'")
public class GitLabCli implements CodeHostDriver {

    static final String ID = "gitlab";
    private static final String MARKER = "/pipelines/";
    private static final Set<String> FAILED = Set.of("failed", "canceled", "canceling");
    private static final Set<String> RUNNING = Set.of("created", "waiting_for_resource", "preparing", "pending",
            "running", "scheduled");
    private static final Set<String> INFRASTRUCTURE = Set.of("runner_system_failure", "stuck_or_timeout_failure",
            "api_failure", "scheduler_failure", "data_integrity_failure");
    private static final CommitPipeline NO_PIPELINE = new CommitPipeline("none", "");
    private static final JsonMapper JSON = new JsonMapper();

    private final HostCli cli;

    public GitLabCli(Processes processes, Reach reach) {
        this.cli = new HostCli(processes, reach, "glab",
                hostname -> List.of("auth", "login", "--hostname", hostname));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Answer<List<FailedJob>> failedJobs(HostSettings host, String pipelineUrl) {
        return HostAddress.ofPipeline(pipelineUrl, MARKER, true).map(address -> cli.parsed(
                cli.run(address.hostname(), api(address, "/pipelines/" + address.id() + "/jobs?scope[]=failed&per_page=100")),
                GitLabCli::jobs)).orElseGet(() -> HostCli.notAPipeline(pipelineUrl));
    }

    @Override
    public Answer<String> retryFailed(HostSettings host, String pipelineUrl) {
        return HostAddress.ofPipeline(pipelineUrl, MARKER, true).map(address -> cli.run(address.hostname(),
                api(address, "-X", "POST", "/pipelines/" + address.id() + "/retry")))
                .orElseGet(() -> HostCli.notAPipeline(pipelineUrl));
    }

    @Override
    public Answer<CommitPipeline> onCommit(HostSettings host, String remoteUrl, String sha) {
        return HostAddress.ofRemote(remoteUrl, sha, host.hostName(), true).map(address -> cli.parsed(
                cli.run(address.hostname(), api(address, "/pipelines?sha=" + address.id() + "&per_page=1")),
                GitLabCli::pipeline)).orElseGet(() -> new Answer<>(Optional.of(CommitPipeline.unhosted()), TokenUsage.NONE));
    }

    @Override
    public List<Problem> problems(HostSettings host) {
        return cli.missing(host.selectedBy());
    }

    private static List<String> api(HostAddress address, String path) {
        return List.of("api", "--hostname", address.hostname(), "projects/" + encoded(address) + path);
    }

    private static List<String> api(HostAddress address, String flag, String method, String path) {
        return List.of("api", "--hostname", address.hostname(), flag, method, "projects/" + encoded(address) + path);
    }

    private static String encoded(HostAddress address) {
        return URLEncoder.encode(address.project(), StandardCharsets.UTF_8);
    }

    private static CommitPipeline pipeline(String output) {
        JsonNode newest = JSON.readTree(output).path(0);
        return newest.isMissingNode() ? NO_PIPELINE
                : new CommitPipeline(verdict(newest.path("status").asString("")), newest.path("web_url").asString(""));
    }

    private static String verdict(String said) {
        if ("success".equals(said)) {
            return "success";
        }
        if (FAILED.contains(said)) {
            return "failed";
        }
        return RUNNING.contains(said) ? "running" : "unknown";
    }

    private static List<FailedJob> jobs(String output) {
        List<FailedJob> jobs = new ArrayList<>();
        JSON.readTree(output).forEach(job -> {
            String reason = job.path("failure_reason").asString("");
            jobs.add(new FailedJob(job.path("id").asString(""), job.path("name").asString(""), reason,
                    INFRASTRUCTURE.contains(reason)));
        });
        return jobs;
    }
}
