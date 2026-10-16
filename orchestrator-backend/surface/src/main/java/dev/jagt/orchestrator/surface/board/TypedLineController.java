package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.AgentSessions;
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

    public record InterpretRequest(String text) {
    }

    public record LineRequest(String line) {
    }

    private final NaturalLanguageDispatch naturalLanguage;
    private final AgentSessions sessions;

    /** A line typed where a round is read: it goes into the session, never over the brief on disk. */
    @PostMapping("/tasks/say")
    public ActionResult say(@RequestParam("task") String taskId, @RequestBody LineRequest request) {
        String line = request.line() == null ? "" : request.line().strip();
        if (line.isBlank()) {
            throw new IllegalArgumentException("There is nothing to say");
        }
        return new ActionResult(sessions.say(taskId, line));
    }

    public record Screen(String shown) {
    }

    /** The open task's session as it reads now, so it can be read without leaving the board. */
    @GetMapping("/tasks/screen")
    public Screen screen(@RequestParam("task") String taskId) {
        return new Screen(sessions.screen(taskId).orElse(""));
    }

    /** The model only proposes; the proposal passes the same gate as a button. */
    @PostMapping("/interpret")
    public ActionResult interpret(@RequestBody InterpretRequest request) {
        return new ActionResult(naturalLanguage.interpret(request.text()));
    }
}
