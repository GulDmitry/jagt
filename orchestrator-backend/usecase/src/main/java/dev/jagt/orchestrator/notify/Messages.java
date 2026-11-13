package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.notify.Routes.Routed;
import dev.jagt.orchestrator.port.ChannelMessage;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.BoardUrl;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Fills a route's title and text. A placeholder about the task is empty for a point without one,
 * and a title drops the segments left empty.
 */
@Component
public class Messages {

  public static final List<String> PLACEHOLDERS =
      List.of(
          "task",
          "alias",
          "ticket",
          "title",
          "body",
          "status",
          "said",
          "next",
          "project",
          "requests",
          "request",
          "pipeline",
          "sha",
          "board",
          "role",
          "stop",
          "origin");

  public static final List<String> OF_ANY_POINT = List.of("title", "body", "board");

  private static final Pattern TOKEN = Pattern.compile("\\{([a-z]+)}");
  private static final String SEPARATOR = " · ";

  private final BoardUrl board;
  private final NextMove next;

  public Messages(BoardUrl board, NextMove next) {
    this.board = board;
    this.next = next;
  }

  public ChannelMessage of(Routed routed, Notification sent, Optional<TaskState> task) {
    Map<String, Supplier<String>> values = values(sent, task);
    return new ChannelMessage(
        sent.point(),
        sent.taskId(),
        sent.alias(),
        withoutEmptySegments(fill(routed.title(), values)),
        fill(routed.text(), values));
  }

  public static List<String> unknownIn(String template) {
    return namedIn(template).filter(name -> !PLACEHOLDERS.contains(name)).distinct().toList();
  }

  public static List<String> ofTaskIn(String template) {
    return namedIn(template)
        .filter(PLACEHOLDERS::contains)
        .filter(name -> !OF_ANY_POINT.contains(name))
        .distinct()
        .toList();
  }

  private static Stream<String> namedIn(String template) {
    return TOKEN.matcher(template).results().map(found -> found.group(1));
  }

  private static String fill(String template, Map<String, Supplier<String>> values) {
    return TOKEN
        .matcher(template)
        .replaceAll(
            found ->
                Matcher.quoteReplacement(
                    values.containsKey(found.group(1))
                        ? values.get(found.group(1)).get()
                        : found.group()));
  }

  private static String withoutEmptySegments(String title) {
    return String.join(
        SEPARATOR,
        Arrays.stream(title.split(SEPARATOR)).filter(segment -> !segment.isBlank()).toList());
  }

  private Map<String, Supplier<String>> values(Notification sent, Optional<TaskState> task) {
    return Map.ofEntries(
        value("task", () -> orEmpty(sent.taskId())),
        value("alias", () -> orEmpty(sent.alias())),
        value("title", () -> titleOf(sent)),
        value("body", sent::body),
        value("ticket", () -> ofTask(task, TaskState::ticketUrl)),
        value(
            "status", () -> ofTask(task, state -> state.status().name().toLowerCase(Locale.ROOT))),
        value("said", () -> ofTask(task, Messages::lastLine)),
        value("next", () -> ofTask(task, this::nextOf)),
        value("project", () -> ofTask(task, TaskState::project)),
        value("requests", () -> ofTask(task, state -> String.join("\n", state.requests()))),
        value(
            "request",
            () -> ofTask(task, state -> state.requests().stream().findFirst().orElse(""))),
        value("pipeline", () -> ofTask(task, Messages::pipelineOf)),
        value("sha", () -> ofTask(task, Messages::tip)),
        value("board", () -> sent.taskId() == null ? board.root() : board.ofTask(sent.taskId())),
        value("role", () -> ofTask(task, state -> roleOf(state, sent.point()))),
        value("stop", () -> ofTask(task, Messages::stopOf)),
        value("origin", () -> ofTask(task, Messages::originOf)));
  }

  private static Map.Entry<String, Supplier<String>> value(String key, Supplier<String> read) {
    return Map.entry(key, read);
  }

  private static String ofTask(
      Optional<TaskState> task, Function<TaskState, @Nullable String> read) {
    return task.map(read).orElse("");
  }

  private static String titleOf(Notification sent) {
    return sent.title().isBlank() ? sent.topic().name().toLowerCase(Locale.ROOT) : sent.title();
  }

  private static String lastLine(TaskState task) {
    String message = task.message();
    if (message == null) {
      return "";
    }
    return Arrays.stream(message.split("\n"))
        .filter(line -> !line.isBlank())
        .reduce((first, last) -> last)
        .orElse("");
  }

  private String nextOf(TaskState task) {
    Move move = next.of(task);
    return move.ask() == null ? move.hint() : move.ask();
  }

  private static @Nullable String stopOf(TaskState task) {
    return task.stop() == null ? null : task.stop().trigger().reason();
  }

  private static @Nullable String pipelineOf(TaskState task) {
    return task.pipelineUrl() == null ? task.pipelineStatus() : task.pipelineUrl();
  }

  private static @Nullable String tip(TaskState task) {
    return task.checksTip() == null ? task.headTip() : task.checksTip();
  }

  private static String roleOf(TaskState task, String point) {
    return task.roles().keySet().stream()
        .filter(role -> point.startsWith(role + "-"))
        .findFirst()
        .orElse("");
  }

  private static @Nullable String originOf(TaskState task) {
    return task.history().stream()
        .reduce((a, b) -> b)
        .map(StatusChange::origin)
        .map(ActionOrigin::label)
        .orElse(null);
  }

  private static String orEmpty(@Nullable String text) {
    return text == null ? "" : text;
  }
}
