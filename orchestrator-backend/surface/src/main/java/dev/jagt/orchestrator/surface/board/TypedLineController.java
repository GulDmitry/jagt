package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.surface.board.TaskCommandsController.ActionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TypedLineController {

  public record InterpretRequest(String text) {}

  public record LineRequest(String line) {}

  private final NaturalLanguageDispatch naturalLanguage;
  private final AgentSessions sessions;
  private final CommandService commands;

  /** A line typed where a round is read: it goes into the session, never over the brief on disk. */
  @PostMapping("/tasks/say")
  public ActionResult say(@RequestParam("task") String taskId, @RequestBody LineRequest request) {
    String line = request.line() == null ? "" : request.line().strip();
    if (line.isBlank()) {
      throw new IllegalArgumentException("There is nothing to say");
    }
    return new ActionResult(sessions.say(taskId, line));
  }

  /** The line travels in the body, never the URL, and goes through the verb's own gate. */
  @PostMapping("/tasks/run")
  public ActionResult run(@RequestParam("task") String taskId, @RequestBody LineRequest request) {
    String line = request.line() == null ? "" : request.line().strip();
    if (line.isBlank()) {
      throw new IllegalArgumentException("There is nothing to run");
    }
    return new ActionResult(commands.executeLine(taskId, TaskAction.RUN, line));
  }

  public record KeyRequest(String key) {}

  @PostMapping("/tasks/key")
  public ActionResult key(@RequestParam("task") String taskId, @RequestBody KeyRequest request) {
    SessionHost.Key key;
    try {
      key = SessionHost.Key.valueOf(String.valueOf(request.key()));
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException("Not a key a session accepts: " + request.key());
    }
    return new ActionResult(sessions.press(taskId, key));
  }

  public record Screen(boolean held, String shown) {}

  /**
   * The open task's session as it reads now; `held` is false when no window holds it, whatever
   * `shown` is.
   */
  @GetMapping("/tasks/screen")
  public Screen screen(@RequestParam("task") String taskId) {
    return sessions
        .screen(taskId)
        .map(shown -> new Screen(true, shown))
        .orElseGet(() -> new Screen(false, ""));
  }

  /** The model only proposes; the proposal passes the same gate as a button. */
  @PostMapping("/interpret")
  public ActionResult interpret(@RequestBody InterpretRequest request) {
    return new ActionResult(naturalLanguage.interpret(request.text()));
  }
}
