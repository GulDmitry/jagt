package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectRemoval {

    private final ProjectSetup setup;
    private final StateService state;

    /** Deletes the project, unless a task not yet done works in it. */
    public String remove(String key) {
        List<String> using = state.tasks().entrySet().stream()
                .filter(task -> task.getValue().status() != TaskStatus.DONE && task.getValue().projects().contains(key))
                .map(Map.Entry::getKey).sorted().toList();
        if (!using.isEmpty()) {
            throw new IllegalArgumentException("The project " + key + " is used by " + String.join(", ", using)
                    + ": finish " + (using.size() == 1 ? "that task" : "those tasks") + " first");
        }
        return setup.remove(key);
    }
}
