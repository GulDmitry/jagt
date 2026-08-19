package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.TaskAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every {@link TaskCapability} there is, one per action: the highest priority claimant wins and says so. */
@Component
@Slf4j
public class Capabilities {

    private final Map<TaskAction, TaskCapability> byAction = new EnumMap<>(TaskAction.class);

    public Capabilities(List<TaskCapability> declared) {
        declared.stream().sorted(Comparator.comparingInt(TaskCapability::priority)).forEach(capability -> {
            TaskCapability replaced = byAction.put(capability.action(), capability);
            if (replaced != null) {
                log.info("{} takes over `{}` from {}", capability.getClass().getSimpleName(),
                        capability.action().id(), replaced.getClass().getSimpleName());
            }
        });
    }

    public Optional<TaskCapability> of(TaskAction action) {
        return Optional.ofNullable(byAction.get(action));
    }
}
