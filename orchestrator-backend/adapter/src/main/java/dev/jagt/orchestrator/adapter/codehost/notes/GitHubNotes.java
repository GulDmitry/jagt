package dev.jagt.orchestrator.adapter.codehost.notes;

import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.adapter.codehost.cli.GitHubCli;
import dev.jagt.orchestrator.adapter.codehost.cli.HostAddress;
import dev.jagt.orchestrator.adapter.codehost.cli.HostCli;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.Resolution;
import dev.jagt.orchestrator.port.codehost.review.ReviewRequest;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.reach.Reach;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads and posts a request's notes through {@code gh}, on the host the address names. */
@Component
public class GitHubNotes implements NotesDriver {

  private static final String MARKER = "/pull/";
  private static final String NOTES_QUERY =
      "query($owner:String!,$name:String!,$number:Int!){viewer{login}"
          + " repository(owner:$owner,name:$name){pullRequest(number:$number){"
          + "comments(first:100){nodes{databaseId body author{login __typename}}}"
          + " reviewThreads(first:100){nodes{id isResolved comments(first:100){nodes{"
          + "databaseId body path author{login __typename}}}}}}}}";
  private static final JsonMapper JSON = new JsonMapper();

  private final HostCli cli;

  public GitHubNotes(Processes processes, Reach reach) {
    this.cli = GitHubCli.cli(processes, reach);
  }

  @Override
  public Set<String> kinds() {
    return Set.of(GitHubCli.ID);
  }

  @Override
  public Answer<List<ReviewRequest>> awaitingReview(HostSettings host, String remoteUrl) {
    return HostAddress.ofRemote(remoteUrl, "HEAD", host.host().name(), false)
        .map(address ->
            cli.parsed(
                cli.run(
                    address.hostname(),
                    List.of(
                        "pr",
                        "list",
                        "--search",
                        "review-requested:@me",
                        "--state",
                        "open",
                        "--json",
                        "url,title,author,headRefOid",
                        "--limit",
                        "100",
                        "-R",
                        address.hostname() + "/" + address.project())),
                GitHubNotes::awaiting))
        .orElseGet(() -> HostCli.notRemote(remoteUrl));
  }

  @Override
  public Answer<RequestChange> change(HostSettings host, String requestUrl) {
    return HostAddress.ofPipeline(requestUrl, MARKER, false)
        .map(address ->
            HostCli.then(
                cli.parsed(
                    cli.run(
                        address.hostname(),
                        List.of(
                            "pr",
                            "view",
                            requestUrl.strip(),
                            "--json",
                            "title,body,baseRefName,headRefOid")),
                    GitHubNotes::described),
                change ->
                    cli.run(address.hostname(), List.of("pr", "diff", requestUrl.strip()))
                        .map(change::withDiff)))
        .orElseGet(() -> HostCli.notRequest(requestUrl));
  }

  @Override
  public Answer<List<RequestNote>> notes(HostSettings host, String requestUrl) {
    return HostAddress.ofPipeline(requestUrl, MARKER, false)
        .map(address ->
            cli.parsed(
                cli.run(
                    address.hostname(),
                    List.of(
                        "api",
                        "graphql",
                        "--hostname",
                        address.hostname(),
                        "-f",
                        "query=" + NOTES_QUERY,
                        "-f",
                        "owner=" + owner(address),
                        "-f",
                        "name=" + name(address),
                        "-F",
                        "number=" + address.id())),
                output -> noted(output, host.host().review().reviewBots())))
        .orElseGet(() -> HostCli.notRequest(requestUrl));
  }

  @Override
  public Answer<String> post(HostSettings host, String requestUrl, NoteDraft draft) {
    return HostAddress.ofPipeline(requestUrl, MARKER, false)
        .map(address ->
            cli.parsed(
                cli.run(
                    address.hostname(),
                    List.of(
                        "api",
                        "--hostname",
                        address.hostname(),
                        "-X",
                        "POST",
                        "repos/" + address.project() + "/" + target(address, draft),
                        "-f",
                        "body=" + draft.body())),
                output -> JSON.readTree(output).path("id").asString("")))
        .orElseGet(() -> HostCli.notRequest(requestUrl));
  }

  @Override
  public Answer<Map<String, Reaction>> reactions(
      HostSettings host, String requestUrl, List<String> noteIds) {
    return HostAddress.ofPipeline(requestUrl, MARKER, false)
        .map(address ->
            NoteReads.eachNote(
                noteIds,
                id ->
                    cli.parsed(
                        cli.run(
                            address.hostname(),
                            List.of(
                                "api",
                                "--hostname",
                                address.hostname(),
                                "repos/"
                                    + address.project()
                                    + "/pulls/comments/"
                                    + id
                                    + "/reactions?per_page=100")),
                        GitHubNotes::tallied)))
        .orElseGet(() -> HostCli.notRequest(requestUrl));
  }

  private static String target(HostAddress address, NoteDraft draft) {
    return draft.replyTo().isBlank()
        ? "issues/" + address.id() + "/comments"
        : "pulls/" + address.id() + "/comments/" + draft.replyTo() + "/replies";
  }

  private static String owner(HostAddress address) {
    return address.project().split("/", 2)[0];
  }

  private static String name(HostAddress address) {
    return address.project().substring(address.project().indexOf('/') + 1);
  }

  private static List<ReviewRequest> awaiting(String output) {
    List<ReviewRequest> requests = new ArrayList<>();
    JSON.readTree(output)
        .forEach(request ->
            requests.add(
                ReviewRequest.defaults()
                    .withUrl(request.path("url").asString(""))
                    .withTitle(request.path("title").asString(""))
                    .withAuthor(request.path("author").path("login").asString(""))
                    .withHeadSha(request.path("headRefOid").asString(""))));
    return requests;
  }

  private static RequestChange described(String output) {
    JsonNode request = JSON.readTree(output);
    return RequestChange.defaults()
        .withTitle(request.path("title").asString(""))
        .withDescription(request.path("body").asString(""))
        .withBaseBranch(request.path("baseRefName").asString(""))
        .withHeadSha(request.path("headRefOid").asString(""));
  }

  private static Reaction tallied(String output) {
    int likes = 0;
    int dislikes = 0;
    for (JsonNode reaction : JSON.readTree(output)) {
      String content = reaction.path("content").asString("");
      likes += "+1".equals(content) ? 1 : 0;
      dislikes += "-1".equals(content) ? 1 : 0;
    }
    return new Reaction(likes, dislikes);
  }

  private static List<RequestNote> noted(String output, List<String> bots) {
    JsonNode data = JSON.readTree(output).path("data");
    String me = data.path("viewer").path("login").asString("");
    JsonNode request = data.path("repository").path("pullRequest");
    List<RequestNote> notes = new ArrayList<>();
    request
        .path("comments")
        .path("nodes")
        .forEach(comment -> notes.add(note(comment, "", Resolution.UNKNOWN, me, bots)));
    request
        .path("reviewThreads")
        .path("nodes")
        .forEach(thread -> {
          Resolution resolution =
              thread.path("isResolved").asBoolean(false) ? Resolution.RESOLVED : Resolution.OPEN;
          String id = thread.path("id").asString("");
          thread
              .path("comments")
              .path("nodes")
              .forEach(comment -> notes.add(note(comment, id, resolution, me, bots)));
        });
    return notes;
  }

  private static RequestNote note(
      JsonNode comment, String thread, Resolution resolution, String me, List<String> bots) {
    String id = comment.path("databaseId").asString("");
    String author = comment.path("author").path("login").asString("");
    boolean bot = "Bot".equals(comment.path("author").path("__typename").asString(""));
    return RequestNote.defaults()
        .withId(id)
        .withThread(thread.isBlank() ? id : thread)
        .withAuthor(author)
        .withKind(NoteReads.kind(author, me, bot, bots))
        .withBody(comment.path("body").asString(""))
        .withPath(comment.path("path").asString(""))
        .withResolution(resolution);
  }
}
