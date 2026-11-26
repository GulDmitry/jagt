package dev.jagt.orchestrator.service.plan;

import dev.jagt.orchestrator.service.task.TaskArtifacts;
import dev.jagt.orchestrator.task.plan.Artifact;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What the head of a task's {@code plan.md} says of its ticket. */
@Component
@RequiredArgsConstructor
public class PlanVerdict {

  private static final Pattern SOUND = Pattern.compile("verdict: sound(\\P{L}.*)?");

  private final TaskArtifacts artifacts;

  /** A plan that is absent or headed otherwise is not sound; one that cannot be read throws. */
  public boolean sound(String taskId) {
    String file = Artifact.PLAN.fileName();
    if (!artifacts.present(taskId).contains(file)) {
      return false;
    }
    String plan = artifacts.read(taskId, file);
    return SOUND.matcher(plan.lines().findFirst().orElse("").strip()).matches();
  }
}
