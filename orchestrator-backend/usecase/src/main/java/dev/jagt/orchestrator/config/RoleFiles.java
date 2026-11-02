package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.port.Problem;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The brief and the cases of one role: the human's copy under {@code roles/<id>/}, then the shipped {@code .dist} there. */
public final class RoleFiles {

    private final Path root;
    private final List<String> brief;
    private final List<String> cases;

    public RoleFiles(OrchestratorPaths paths, String id) {
        this.root = paths.root();
        this.brief = names(id, "brief");
        this.cases = names(id, "cases");
    }

    private static List<String> names(String id, String kind) {
        String own = "roles/" + id + "/" + kind + ".md";
        return List.of(own, own + ".dist");
    }

    public List<Problem> unreadable() {
        return HumanCopy.unreadable(root, brief);
    }

    public Set<String> steps() {
        return HumanCopy.sectionIds(root, brief);
    }

    public String text() {
        return HumanCopy.read(root, brief).stream().findFirst().map(HumanCopy.Text::text).orElse("");
    }

    public String section(String step) {
        return HumanCopy.read(root, brief).stream()
                .flatMap(file -> Sections.of(file.text()).stream().filter(section -> section.id().equals(step)))
                .map(Sections.Section::body).findFirst().orElse("");
    }

    public CaseBook book() {
        return HumanCopy.stepCases(root, cases);
    }

    public CaseBook moves() {
        return HumanCopy.moveCases(root, cases);
    }

    public Optional<Path> briefFile() {
        return HumanCopy.firstExisting(root, brief);
    }

    public String casesFile() {
        return cases.getFirst();
    }
}
