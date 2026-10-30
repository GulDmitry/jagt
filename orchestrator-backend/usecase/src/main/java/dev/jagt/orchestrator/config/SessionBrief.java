package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.task.Step;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** A section the human's copy lacks is read from the shipped file. */
@Component
@RequiredArgsConstructor
public class SessionBrief {

    public static final String FILE = "session-brief.md";

    private final OrchestratorPaths paths;

    public Optional<String> section(Step step) {
        return HumanCopy.read(paths.root(), FILE).stream()
                .flatMap(file -> Sections.of(file.text()).stream().filter(section -> section.id().equals(step.id()))
                        .findFirst().stream())
                .map(Sections.Section::body)
                .findFirst();
    }

    /** For a command line or a window that takes one line; empty where the brief has no such step. */
    public String line(Step step) {
        return section(step).map(text -> text.replaceAll("\\s*\\n\\s*", " ")).orElse("");
    }

    public List<Problem> unreadable() {
        return HumanCopy.unreadable(paths.root(), FILE);
    }

    /** Every step either file holds a section for. */
    public Set<String> steps() {
        return HumanCopy.read(paths.root(), FILE).stream()
                .flatMap(file -> Sections.of(file.text()).stream())
                .map(Sections.Section::id)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
