package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.port.Problem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;


@Component
@RequiredArgsConstructor
public class QaBrief {

    public static final String FILE = "qa-brief.md";

    private final OrchestratorPaths paths;

    public List<Problem> unreadable() {
        return HumanCopy.unreadable(paths.root(), FILE);
    }

    public Set<String> steps() {
        return HumanCopy.sectionIds(paths.root(), FILE);
    }

    public String text() {
        return HumanCopy.read(paths.root(), FILE).stream().findFirst().map(HumanCopy.Text::text).orElse("");
    }

    public String section(String step) {
        return HumanCopy.read(paths.root(), FILE).stream()
                .flatMap(file -> Sections.of(file.text()).stream().filter(section -> section.id().equals(step)))
                .map(Sections.Section::body).findFirst().orElse("");
    }
}
