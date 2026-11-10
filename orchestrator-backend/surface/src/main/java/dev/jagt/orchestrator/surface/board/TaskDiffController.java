package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.TaskDiff;
import dev.jagt.orchestrator.service.TrustedGitState;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskDiffController {

  private final TaskDiff diff;
  private final TrustedGitState trusted;

  @GetMapping("/tasks/diff")
  public List<TaskDiff.RepoDiff> diff(@RequestParam("task") String taskId) {
    return Stream.concat(
            diff.read(taskId).stream(),
            trusted.changes(taskId).stream())
        .toList();
  }
}
