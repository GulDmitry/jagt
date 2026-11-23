package dev.jagt.orchestrator.adapter.codehost.notes;

import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.adapter.codehost.cli.GitLabCli;
import dev.jagt.orchestrator.adapter.codehost.cli.HostAddress;
import dev.jagt.orchestrator.adapter.codehost.cli.HostCli;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestState;
import dev.jagt.orchestrator.port.codehost.review.Resolution;
import dev.jagt.orchestrator.port.codehost.review.ReviewRequest;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads and posts a request's notes through {@code glab}, on the host the address names. */
@Component
public class GitLabNotes implements NotesDriver {

  private static final String MARKER = "/merge_requests/";
  private static final JsonMapper JSON = new JsonMapper();

  private final HostCli cli;

  public GitLabNotes(Processes processes, Reach reach) {
    this.cli = GitLabCli.cli(processes, reach);
  }

  @Override
  public Set<String> kinds() {
    return Set.of(GitLabCli.ID);
  }

  @Override
  public Answer<List<ReviewRequest>> awaitingReview(HostSettings host, String remoteUrl) {
    return HostAddress.ofRemote(remoteUrl, "HEAD", host.host().name(), true)
        .map(address ->
            HostCli.then(
                me(address),
                me ->
                    cli.parsed(
                        cli.run(
                            address.hostname(),
                            api(
                                address,
                                "/merge_requests?state=opened&per_page=100&reviewer_username="
                                    + URLEncoder.encode(me, StandardCharsets.UTF_8))),
                        output -> awaiting(output, me))))
        .orElseGet(() -> HostCli.notRemote(remoteUrl));
  }

  @Override
  public Answer<RequestChange> change(HostSettings host, String requestUrl) {
    return HostAddress.ofPipeline(requestUrl, MARKER, true)
        .map(address ->
            HostCli.then(
                cli.parsed(
                    cli.run(address.hostname(), api(address, MARKER + address.id())),
                    GitLabNotes::described),
                change ->
                    cli.parsed(
                            cli.run(
                                address.hostname(),
                                api(address, MARKER + address.id() + "/changes")),
                            GitLabNotes::diff)
                        .map(change::withDiff)))
        .orElseGet(() -> HostCli.notRequest(requestUrl));
  }

  @Override
  public Answer<List<RequestNote>> notes(HostSettings host, String requestUrl) {
    return HostAddress.ofPipeline(requestUrl, MARKER, true)
        .map(address ->
            HostCli.then(
                me(address),
                me ->
                    cli.parsed(
                        cli.run(address.hostname(), discussions(address)),
                        output -> noted(output, me, host.host().review().reviewBots()))))
        .orElseGet(() -> HostCli.notRequest(requestUrl));
  }

  @Override
  public Answer<String> post(HostSettings host, String requestUrl, NoteDraft draft) {
    return HostAddress.ofPipeline(requestUrl, MARKER, true)
        .map(address ->
            draft.replyTo().isBlank()
                ? topLevel(address, draft)
                : HostCli.then(
                    cli.parsed(
                        cli.run(address.hostname(), discussions(address)),
                        output -> discussionOf(output, draft.replyTo())),
                    discussion ->
                        discussion.isBlank()
                            ? Answer.<String>failed(
                                TokenUsage.NONE, "no note " + draft.replyTo() + " on the request")
                            : posted(
                                address,
                                MARKER + address.id() + "/discussions/" + discussion + "/notes",
                                draft)))
        .orElseGet(() -> HostCli.notRequest(requestUrl));
  }

  @Override
  public Answer<Map<String, Reaction>> reactions(
      HostSettings host, String requestUrl, List<String> noteIds) {
    return HostAddress.ofPipeline(requestUrl, MARKER, true)
        .map(address ->
            NoteReads.eachNote(
                noteIds,
                id ->
                    cli.parsed(
                        cli.run(
                            address.hostname(),
                            api(
                                address,
                                MARKER
                                    + address.id()
                                    + "/notes/"
                                    + id
                                    + "/award_emoji?per_page=100")),
                        GitLabNotes::tallied)))
        .orElseGet(() -> HostCli.notRequest(requestUrl));
  }

  private Answer<String> me(HostAddress address) {
    return cli.parsed(
        cli.run(address.hostname(), List.of("api", "--hostname", address.hostname(), "user")),
        output -> JSON.readTree(output).path("username").asString(""));
  }

  private Answer<String> topLevel(HostAddress address, NoteDraft draft) {
    if (!draft.anchor().inline()) {
      return posted(address, MARKER + address.id() + "/notes", draft);
    }
    return HostCli.then(
        cli.parsed(
            cli.run(address.hostname(), api(address, MARKER + address.id())),
            output -> JSON.readTree(output).path("diff_refs")),
        refs -> {
          List<String> command =
              new ArrayList<>(api(address, MARKER + address.id() + "/discussions"));
          command.addAll(List.of("-X", "POST", "-f", "body=" + draft.body()));
          String file = draft.anchor().file();
          for (Map.Entry<String, String> position :
              Map.of(
                      "position_type",
                      "text",
                      "base_sha",
                      refs.path("base_sha").asString(""),
                      "start_sha",
                      refs.path("start_sha").asString(""),
                      "head_sha",
                      draft.anchor().headSha(),
                      "new_path",
                      file,
                      "old_path",
                      file)
                  .entrySet()) {
            command.addAll(
                List.of("-f", "position[" + position.getKey() + "]=" + position.getValue()));
          }
          command.addAll(List.of("-F", "position[new_line]=" + draft.anchor().line()));
          return cli.parsed(
              cli.run(address.hostname(), command),
              output -> JSON.readTree(output).path("notes").path(0).path("id").asString(""));
        });
  }

  private Answer<String> posted(HostAddress address, String path, NoteDraft draft) {
    List<String> command = new ArrayList<>(api(address, path));
    command.addAll(List.of("-X", "POST", "-f", "body=" + draft.body()));
    return cli.parsed(
        cli.run(address.hostname(), command),
        output -> JSON.readTree(output).path("id").asString(""));
  }

  private static List<String> discussions(HostAddress address) {
    return api(address, MARKER + address.id() + "/discussions?per_page=100");
  }

  private static List<String> api(HostAddress address, String path) {
    return List.of(
        "api",
        "--hostname",
        address.hostname(),
        "projects/" + URLEncoder.encode(address.project(), StandardCharsets.UTF_8) + path);
  }

  private static List<ReviewRequest> awaiting(String output, String me) {
    List<ReviewRequest> requests = new ArrayList<>();
    JSON.readTree(output)
        .forEach(request -> {
          String author = request.path("author").path("username").asString("");
          if (!author.equalsIgnoreCase(me)) {
            requests.add(
                ReviewRequest.defaults()
                    .withUrl(request.path("web_url").asString(""))
                    .withTitle(request.path("title").asString(""))
                    .withAuthor(author)
                    .withHeadSha(request.path("sha").asString("")));
          }
        });
    return requests;
  }

  private static RequestChange described(String output) {
    JsonNode request = JSON.readTree(output);
    return RequestChange.defaults()
        .withTitle(request.path("title").asString(""))
        .withDescription(request.path("description").asString(""))
        .withBaseBranch(request.path("target_branch").asString(""))
        .withHeadSha(request.path("sha").asString(""))
        .withState(RequestState.of(request.path("state").asString("")));
  }

  private static String diff(String output) {
    StringBuilder diff = new StringBuilder();
    JSON.readTree(output)
        .path("changes")
        .forEach(change ->
            diff.append("--- a/")
                .append(change.path("old_path").asString(""))
                .append("\n+++ b/")
                .append(change.path("new_path").asString(""))
                .append('\n')
                .append(change.path("diff").asString("")));
    return diff.toString();
  }

  private static String discussionOf(String output, String noteId) {
    for (JsonNode discussion : JSON.readTree(output)) {
      for (JsonNode note : discussion.path("notes")) {
        if (note.path("id").asString("").equals(noteId)) {
          return discussion.path("id").asString("");
        }
      }
    }
    return "";
  }

  private static Reaction tallied(String output) {
    int likes = 0;
    int dislikes = 0;
    for (JsonNode award : JSON.readTree(output)) {
      String name = award.path("name").asString("");
      likes += "thumbsup".equals(name) ? 1 : 0;
      dislikes += "thumbsdown".equals(name) ? 1 : 0;
    }
    return new Reaction(likes, dislikes);
  }

  private static List<RequestNote> noted(String output, String me, List<String> bots) {
    List<RequestNote> notes = new ArrayList<>();
    JSON.readTree(output)
        .forEach(discussion ->
            discussion
                .path("notes")
                .forEach(note -> {
                  if (!note.path("system").asBoolean(false)) {
                    notes.add(note(note, discussion.path("id").asString(""), me, bots));
                  }
                }));
    return notes;
  }

  private static RequestNote note(JsonNode note, String thread, String me, List<String> bots) {
    String author = note.path("author").path("username").asString("");
    return RequestNote.defaults()
        .withId(note.path("id").asString(""))
        .withThread(thread)
        .withAuthor(author)
        .withKind(
            NoteReads.kind(author, me, note.path("author").path("bot").asBoolean(false), bots))
        .withBody(note.path("body").asString(""))
        .withPath(note.path("position").path("new_path").asString(""))
        .withResolution(resolution(note));
  }

  private static Resolution resolution(JsonNode note) {
    if (!note.path("resolvable").asBoolean(false)) {
      return Resolution.UNKNOWN;
    }
    return note.path("resolved").asBoolean(false) ? Resolution.RESOLVED : Resolution.OPEN;
  }
}
