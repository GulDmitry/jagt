package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.TokenUsage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads and re-runs a pipeline, closes a request, through {@code glab}, on the host the remote or the configured name gives. */
@Component
public class GitLabCli implements CodeHostDriver {

    static final String ID = "gitlab";
    private static final String MARKER = "/pipelines/";
    private static final String REQUEST_MARKER = "/merge_requests/";
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
    public Answer<String> closeRequest(HostSettings host, String requestUrl) {
        return HostAddress.ofPipeline(requestUrl, REQUEST_MARKER, true).map(address -> cli.run(address.hostname(),
                List.of("mr", "close", address.id(), "-R", address.hostname() + "/" + address.project())))
                .orElseGet(() -> HostCli.notARequest(requestUrl));
    }

    @Override
    public Answer<List<String>> unresolvedNotes(HostSettings host, String requestUrl) {
        return HostAddress.ofPipeline(requestUrl, REQUEST_MARKER, true).map(address -> HostCli.then(
                cli.parsed(cli.run(address.hostname(), List.of("api", "--hostname", address.hostname(), "user")),
                        output -> JSON.readTree(output).path("username").asString("")),
                me -> cli.parsed(cli.run(address.hostname(), api(address, "/merge_requests/" + address.id()
                        + "/discussions?per_page=100")), output -> unresolved(output, me))))
                .orElseGet(() -> HostCli.notARequest(requestUrl));
    }

    @Override
    public Answer<CommitPipeline> onCommit(HostSettings host, String remoteUrl, String sha) {
        return HostAddress.ofRemote(remoteUrl, sha, host.host().name(), true).map(address -> cli.parsed(
                cli.run(address.hostname(), api(address, "/pipelines?sha=" + address.id() + "&per_page=1")),
                GitLabCli::pipeline)).orElseGet(() -> new Answer<>(Optional.of(CommitPipeline.unhosted()), TokenUsage.NONE));
    }

    @Override
    public Answer<CommitPipeline> onBranchSince(HostSettings host, String remoteUrl, String branch, String sha) {
        // Any source, as onCommit reads its commit's newest: a scheduled or API pipeline runs the branch's CI too.
        return HostAddress.ofRemote(remoteUrl, sha, host.host().name(), true).map(address -> HostCli.then(
                cli.parsed(cli.run(address.hostname(), api(address, "/pipelines?ref="
                        + URLEncoder.encode(branch, StandardCharsets.UTF_8) + "&scope=finished&per_page=1")),
                        GitLabCli::newest),
                newest -> newest.since(cli, sha, () -> cli.run(address.hostname(), api(address,
                        "/repository/merge_base?refs[]=" + sha + "&refs[]=" + newest.sha())),
                        base -> JSON.readTree(base).path("id").asString("").startsWith(sha))))
                .orElseGet(() -> new Answer<>(Optional.of(CommitPipeline.unhosted()), TokenUsage.NONE));
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

    private static List<String> unresolved(String output, String me) {
        List<String> ids = new ArrayList<>();
        JSON.readTree(output).forEach(discussion -> {
            JsonNode notes = discussion.path("notes");
            boolean open = false;
            for (JsonNode note : notes) {
                open |= note.path("resolvable").asBoolean(false) && !note.path("resolved").asBoolean(false);
            }
            JsonNode newest = notes.path(notes.size() - 1);
            String author = newest.path("author").path("username").asString("");
            if (open && !(author.equalsIgnoreCase(me) && !me.isBlank())) {
                ids.add(newest.path("id").asString(""));
            }
        });
        return ids;
    }

    private static BranchTip newest(String output) {
        return new BranchTip(JSON.readTree(output).path(0).path("sha").asString(""), pipeline(output));
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
