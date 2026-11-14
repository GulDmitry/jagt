package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.scenario.OpenedWindows;
import dev.jagt.orchestrator.surface.board.TaskCommandsController.ActionResult;
import dev.jagt.orchestrator.surface.board.TypedLineController.LineRequest;
import dev.jagt.orchestrator.surface.board.TypedLineController.Screen;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/windows")
@RequiredArgsConstructor
public class WindowsController {

  private final OpenedWindows windows;

  @GetMapping
  public List<String> open() {
    return windows.open();
  }

  @GetMapping("/screen")
  public Screen screen(@RequestParam("name") String name) {
    return windows
        .screen(name)
        .map(shown -> new Screen(true, shown))
        .orElseGet(() -> new Screen(false, ""));
  }

  @PostMapping("/say")
  public ActionResult say(@RequestParam("name") String name, @RequestBody LineRequest request) {
    String line = request.line() == null ? "" : request.line().strip();
    if (line.isBlank()) {
      throw new IllegalArgumentException("There is nothing to say");
    }
    return new ActionResult(windows.say(name, line));
  }
}
