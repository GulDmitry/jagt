package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.StartupCheck;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The machine itself, checked before anything can drive it: a status nothing can put a task into is a state no task
 * will ever be in, and reading the table will not tell you which one that is.
 *
 * <p>There is deliberately no "stuck status" check to go with it. A task leaves a status through either door, and
 * the report door is judged by the status being reported rather than by the one being left — so no status can trap
 * a task, and asserting that would assert nothing.
 */
@Component
public class FlowCheck implements StartupCheck {

    /**
     * NEW is where a task is born, so nothing needs to lead there. DONE is where one is retired, and retiring
     * REMOVES it — no live task ever holds that status, which is why nothing leads there either.
     */
    private static final Set<TaskStatus> WITHOUT_A_WAY_IN = EnumSet.of(TaskStatus.NEW, TaskStatus.DONE);

    @Override
    public List<String> problems() {
        return java.util.Arrays.stream(TaskStatus.values())
                .filter(status -> !WITHOUT_A_WAY_IN.contains(status))
                .filter(status -> !FlowRules.targets().contains(status) && !FlowRules.reportable(status))
                .map(status -> "flow: nothing can put a task into " + status
                        + " — no action leads there and no agent may report it")
                .toList();
    }
}
