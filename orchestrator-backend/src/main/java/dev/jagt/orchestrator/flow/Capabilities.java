package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.port.TaskCapability;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every {@link TaskCapability} there is, one per action: the highest priority claimant wins and says so. */
public class Capabilities {

    private final Map<TaskAction, TaskCapability> byAction = new EnumMap<>(TaskAction.class);
    private final List<String> takeovers = new java.util.ArrayList<>();

    public Capabilities(List<TaskCapability> declared) {
        declared.stream().sorted(Comparator.comparingInt(TaskCapability::priority)).forEach(capability -> {
            TaskCapability replaced = byAction.put(capability.action(), capability);
            if (replaced != null) {
                takeovers.add(capability.getClass().getSimpleName() + " takes over `"
                        + capability.action().id() + "` from " + replaced.getClass().getSimpleName());
            }
        });
    }

    /** Which declarations displaced which, for whoever assembles this to report — nothing here logs. */
    public List<String> takeovers() {
        return List.copyOf(takeovers);
    }

    public Optional<TaskCapability> of(TaskAction action) {
        return Optional.ofNullable(byAction.get(action));
    }
}
