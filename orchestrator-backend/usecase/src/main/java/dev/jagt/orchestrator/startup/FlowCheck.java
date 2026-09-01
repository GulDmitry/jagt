package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.StartupCheck;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** The machine itself, checked before anything can drive it: a status nothing can reach is one no task will hold. */
@Component
public class FlowCheck implements StartupCheck {

    /** No live task holds either, so nothing needs to lead there. */
    private static final Set<TaskStatus> WITHOUT_A_WAY_IN = EnumSet.of(TaskStatus.NEW, TaskStatus.DONE);

    @Override
    public List<String> problems() {
        return java.util.stream.Stream.concat(unreachableStatuses(), verbsNoRuleMentions()).toList();
    }

    private static java.util.stream.Stream<String> unreachableStatuses() {
        return java.util.Arrays.stream(TaskStatus.values())
                .filter(status -> !WITHOUT_A_WAY_IN.contains(status))
                .filter(status -> !FlowRules.targets().contains(status) && !FlowRules.reportable(status))
                .map(status -> "flow: nothing can put a task into " + status
                        + " — no action leads there and no agent may report it");
    }

    /** A missing rule looks exactly like a rule that says no. */
    private static java.util.stream.Stream<String> verbsNoRuleMentions() {
        return java.util.Arrays.stream(TaskAction.values())
                .filter(action -> !FlowRules.mentions(action))
                .map(action -> "flow: `" + action.id() + "` has no rule, so it can never be offered or run");
    }
}
