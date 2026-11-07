package dev.jagt.orchestrator.adapter.codehost;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads and re-runs a pipeline and closes a request through {@code gh}; every call names its host,
 * so an Enterprise host answers.
 */
@Component
public class GitHubCli implements CodeHostDriver {

  static final String ID = "github";
  private static final String MARKER = "/actions/runs/";
  private static final String REQUEST_MARKER = "/pull/";
  private static final Set<String> RAN_FINE = Set.of("success", "skipped", "neutral");
  private static final Set<String> FAILED =
      Set.of("failure", "startup_failure", "cancelled", "timed_out", "action_required");
  private static final Set<String> RUNNING =
      Set.of("queued", "in_progress", "waiting", "requested", "pending");
  private static final Set<String> AHEAD = Set.of("ahead", "identical");
  private static final CommitPipeline NO_PIPELINE = new CommitPipeline("none", "");
  private static final String THREADS_QUERY =
      "query($owner:String!,$name:String!,$number:Int!){viewer{login}"
          + " repository(owner:$owner,name:$name){pullRequest(number:$number)"
          + "{reviewThreads(first:100){nodes{isResolved"
          + " comments(last:1){nodes{databaseId author{login}}}}}}}}";
  private static final JsonMapper JSON = new JsonMapper();

  private final HostCli cli;

  public GitHubCli(Processes processes, Reach reach) {
    this.cli =
        new HostCli(
            processes,
            reach,
            "gh",
            hostname -> List.of("auth", "login", "--hostname", hostname, "--web"));
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public Answer<List<FailedJob>> failedJobs(HostSettings host, String pipelineUrl) {
    return HostAddress.ofPipeline(pipelineUrl, MARKER, false)
        .map(
            address ->
                cli.parsed(
                    cli.run(
                        address.hostname(),
                        List.of(
                            "run",
                            "view",
                            address.id(),
                            "--json",
                            "conclusion,jobs",
                            "-R",
                            repository(address))),
                    output -> jobs(address, output)))
        .orElseGet(() -> HostCli.notPipeline(pipelineUrl));
  }

  @Override
  public Answer<String> retryFailed(HostSettings host, String pipelineUrl) {
    return HostAddress.ofPipeline(pipelineUrl, MARKER, false)
        .map(
            address ->
                cli.run(
                    address.hostname(),
                    List.of("run", "rerun", address.id(), "--failed", "-R", repository(address))))
        .orElseGet(() -> HostCli.notPipeline(pipelineUrl));
  }

  @Override
  public Answer<String> closeRequest(HostSettings host, String requestUrl) {
    return HostAddress.ofPipeline(requestUrl, REQUEST_MARKER, false)
        .map(address -> cli.run(address.hostname(), List.of("pr", "close", requestUrl.strip())))
        .orElseGet(() -> HostCli.notRequest(requestUrl));
  }

  @Override
  public Answer<List<String>> unresolvedNotes(HostSettings host, String requestUrl) {
    return HostAddress.ofPipeline(requestUrl, REQUEST_MARKER, false)
        .map(
            address ->
                cli.parsed(
                    cli.run(
                        address.hostname(),
                        List.of(
                            "api",
                            "graphql",
                            "--hostname",
                            address.hostname(),
                            "-f",
                            "query=" + THREADS_QUERY,
                            "-f",
                            "owner=" + address.project().split("/", 2)[0],
                            "-f",
                            "name="
                                + address.project().substring(address.project().indexOf('/') + 1),
                            "-F",
                            "number=" + address.id())),
                    GitHubCli::unresolved))
        .orElseGet(() -> HostCli.notRequest(requestUrl));
  }

  @Override
  public Answer<CommitPipeline> onCommit(HostSettings host, String remoteUrl, String sha) {
    return HostAddress.ofRemote(remoteUrl, sha, host.host().name(), false)
        .map(
            address ->
                cli.parsed(
                    cli.run(
                        address.hostname(),
                        List.of(
                            "run",
                            "list",
                            "--commit",
                            address.id(),
                            "--json",
                            "status,conclusion,url",
                            "--limit",
                            "100",
                            "-R",
                            repository(address))),
                    GitHubCli::pipeline))
        .orElseGet(() -> new Answer<>(Optional.of(CommitPipeline.unhosted()), TokenUsage.NONE));
  }

  @Override
  public Answer<CommitPipeline> onBranchSince(
      HostSettings host, String remoteUrl, String branch, String sha) {
    return HostAddress.ofRemote(remoteUrl, sha, host.host().name(), false)
        .map(
            address ->
                HostCli.then(
                    cli.parsed(
                        cli.run(
                            address.hostname(),
                            List.of(
                                "run",
                                "list",
                                "--branch",
                                branch,
                                "--json",
                                "status,conclusion,url,headSha",
                                "--limit",
                                "100",
                                "-R",
                                repository(address))),
                        GitHubCli::newest),
                    newest ->
                        newest.since(
                            cli,
                            sha,
                            () ->
                                cli.run(
                                    address.hostname(),
                                    List.of(
                                        "api",
                                        "--hostname",
                                        address.hostname(),
                                        "repos/"
                                            + address.project()
                                            + "/compare/"
                                            + sha
                                            + "..."
                                            + newest.sha(),
                                        "--jq",
                                        ".status")),
                            status -> AHEAD.contains(status.strip()))))
        .orElseGet(() -> new Answer<>(Optional.of(CommitPipeline.unhosted()), TokenUsage.NONE));
  }

  @Override
  public List<Problem> problems(HostSettings host) {
    return cli.missing(host.selectedBy());
  }

  private static String repository(HostAddress address) {
    return address.hostname() + "/" + address.project();
  }

  private static List<String> unresolved(String output) {
    JsonNode data = JSON.readTree(output).path("data");
    String me = data.path("viewer").path("login").asString("");
    List<String> ids = new ArrayList<>();
    data.path("repository")
        .path("pullRequest")
        .path("reviewThreads")
        .path("nodes")
        .forEach(
            thread -> {
              JsonNode newest = thread.path("comments").path("nodes").path(0);
              String author = newest.path("author").path("login").asString("");
              if (!thread.path("isResolved").asBoolean(false)
                  && !newest.isMissingNode()
                  && !(author.equalsIgnoreCase(me) && !me.isBlank())) {
                ids.add(newest.path("databaseId").asString(""));
              }
            });
    return ids;
  }

  private static BranchTip newest(String output) {
    JsonNode runs = JSON.readTree(output);
    String head = runs.path(0).path("headSha").asString("");
    return new BranchTip(head, worst(runs, run -> run.path("headSha").asString("").equals(head)));
  }

  private static CommitPipeline pipeline(String output) {
    return worst(JSON.readTree(output), run -> true);
  }

  private static CommitPipeline worst(JsonNode runs, Predicate<JsonNode> counted) {
    CommitPipeline worst = NO_PIPELINE;
    for (JsonNode run : runs) {
      if (!counted.test(run)) {
        continue;
      }
      String status = run.path("status").asString("");
      String said = "completed".equals(status) ? run.path("conclusion").asString("") : status;
      CommitPipeline read = new CommitPipeline(verdict(said), run.path("url").asString(""));
      if (worst.equals(NO_PIPELINE)
          || Pipeline.of(read.status()).severity() < Pipeline.of(worst.status()).severity()) {
        worst = read;
      }
    }
    return worst;
  }

  private static String verdict(String said) {
    if (RAN_FINE.contains(said)) {
      return "success";
    }
    if (FAILED.contains(said)) {
      return "failed";
    }
    return RUNNING.contains(said) ? "running" : "unknown";
  }

  private static List<FailedJob> jobs(HostAddress address, String output) {
    JsonNode run = JSON.readTree(output);
    if ("startup_failure".equals(run.path("conclusion").asString(""))) {
      return List.of(new FailedJob(address.id(), "workflow", "startup_failure", true));
    }
    List<FailedJob> jobs = new ArrayList<>();
    run.path("jobs")
        .forEach(
            job -> {
              String conclusion = job.path("conclusion").asString("");
              if (!conclusion.isBlank() && !RAN_FINE.contains(conclusion)) {
                jobs.add(
                    new FailedJob(
                        job.path("databaseId").asString(""),
                        job.path("name").asString(""),
                        conclusion,
                        false));
              }
            });
    return jobs;
  }
}
