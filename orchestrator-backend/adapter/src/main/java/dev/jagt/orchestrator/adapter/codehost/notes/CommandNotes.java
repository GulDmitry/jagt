package dev.jagt.orchestrator.adapter.codehost.notes;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.adapter.codehost.cli.HostCli;
import dev.jagt.orchestrator.adapter.codehost.command.CommandPipeline;
import dev.jagt.orchestrator.adapter.codehost.command.CommandRun;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.ReviewRequest;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Any provider: the project's own commands print what the port reads, as JSON, and post a note. A
 * command that is not set reads nothing, but for a change or a post, whose absence is a failure.
 */
@Component
public class CommandNotes implements NotesDriver {

  private static final JsonMapper JSON = new JsonMapper();

  private final CommandRun run;

  public CommandNotes(Processes processes, Reach reach) {
    this.run = new CommandRun(processes, reach, CommandPipeline.ID);
  }

  @Override
  public Set<String> kinds() {
    return Set.of(CommandPipeline.ID);
  }

  @Override
  public Answer<List<ReviewRequest>> awaitingReview(HostSettings host, String remoteUrl) {
    List<String> command = host.host().review().awaitingCommand();
    if (command.isEmpty()) {
      return new Answer<>(Optional.of(List.of()), TokenUsage.NONE);
    }
    return parsed(
        host,
        "awaitingCommand",
        run.run(host, "awaitingCommand", command, Map.of("{remote}", remoteUrl)),
        CommandNotes::awaiting);
  }

  @Override
  public Answer<RequestChange> change(HostSettings host, String requestUrl) {
    List<String> command = host.host().review().changeCommand();
    if (command.isEmpty()) {
      return Answer.failed(TokenUsage.NONE, host.key("changeCommand") + " is not set");
    }
    return parsed(
        host,
        "changeCommand",
        run.run(host, "changeCommand", command, Map.of("{request}", requestUrl)),
        CommandNotes::described);
  }

  @Override
  public Answer<List<RequestNote>> notes(HostSettings host, String requestUrl) {
    if (host.host().notesCommand().isEmpty()) {
      return new Answer<>(Optional.of(List.of()), TokenUsage.NONE);
    }
    return run.run(
            host, "notesCommand", host.host().notesCommand(), Map.of("{request}", requestUrl))
        .map(printed ->
            printed
                .lines()
                .map(String::strip)
                .filter(id -> !id.isEmpty())
                .map(id -> RequestNote.defaults().withId(id).withThread(id))
                .toList());
  }

  @Override
  public Answer<String> post(HostSettings host, String requestUrl, NoteDraft draft) {
    List<String> command = host.host().review().postCommand();
    if (command.isEmpty()) {
      return Answer.failed(TokenUsage.NONE, host.key("postCommand") + " is not set");
    }
    return run.run(
            host,
            "postCommand",
            command,
            Map.of("{request}", requestUrl, "{replyTo}", draft.replyTo()),
            draft.body())
        .map(String::strip);
  }

  @Override
  public Answer<Map<String, Reaction>> reactions(
      HostSettings host, String requestUrl, List<String> noteIds) {
    return Answer.unavailable();
  }

  @Override
  public List<Problem> problems(HostSettings host) {
    List<Problem> problems = new ArrayList<>();
    Map<String, List<String>> commands = new LinkedHashMap<>();
    commands.put("awaitingCommand", host.host().review().awaitingCommand());
    commands.put("changeCommand", host.host().review().changeCommand());
    commands.put("postCommand", host.host().review().postCommand());
    commands.forEach((name, command) -> {
      if (!command.isEmpty() && Executables.unresolved(Executables.resolve(command.getFirst()))) {
        String key = host.key(name);
        problems.add(
            new Problem(
                key,
                key
                    + ": '"
                    + command.getFirst()
                    + "' is not on PATH nor in the usual install directories."));
      }
    });
    return problems;
  }

  private static <T> Answer<T> parsed(
      HostSettings host, String name, Answer<String> printed, Function<String, T> reader) {
    if (printed.facts().isEmpty()) {
      return HostCli.failureOf(printed);
    }
    try {
      return new Answer<>(Optional.of(reader.apply(printed.facts().get())), TokenUsage.NONE);
    } catch (JacksonException e) {
      return Answer.failed(TokenUsage.NONE, host.key(name) + " printed output that is not JSON");
    }
  }

  private static List<ReviewRequest> awaiting(String output) {
    return StreamSupport.stream(JSON.readTree(output).spliterator(), false)
        .map(request ->
            new ReviewRequest(
                request.path("url").asString(""),
                request.path("title").asString(""),
                request.path("author").asString(""),
                request.path("headSha").asString("")))
        .toList();
  }

  private static RequestChange described(String output) {
    JsonNode change = JSON.readTree(output);
    return RequestChange.defaults()
        .withTitle(change.path("title").asString(""))
        .withDescription(change.path("description").asString(""))
        .withBaseBranch(change.path("baseBranch").asString(""))
        .withHeadSha(change.path("headSha").asString(""))
        .withDiff(change.path("diff").asString(""));
  }
}
