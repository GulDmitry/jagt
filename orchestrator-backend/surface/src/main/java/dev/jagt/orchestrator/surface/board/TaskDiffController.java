package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.TaskDiff;
import java.util.List;
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

  @GetMapping("/tasks/diff")
  public List<TaskDiff.RepoDiff> diff(@RequestParam("task") String taskId) {
    return diff.read(taskId);
  }
}
