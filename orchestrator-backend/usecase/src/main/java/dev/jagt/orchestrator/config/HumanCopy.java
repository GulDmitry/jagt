package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.port.Problem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** A file jagt ships as {@code <name>.dist}; the human's copy beside it, named {@code <name>}, is read first. */
final class HumanCopy {

    record Text(String file, String text) {
    }

    private HumanCopy() {
    }

    /** The first name is the human's own copy; each later one is read only where it exists, after the ones before it. */
    static List<Text> read(Path root, List<String> names) {
        return names.stream().map(name -> file(root, name)).flatMap(Optional::stream).toList();
    }

    /** What keeps a file that exists from being read: startup refuses on it, so no later read may pass for the shipped one. */
    static List<Problem> unreadable(Path root, List<String> names) {
        return names.stream().map(name -> failure(root, name)).flatMap(Optional::stream).toList();
    }

    static CaseBook stepCases(Path root, List<String> names) {
        return cases(root, names, CaseBook.Shape.STEPS);
    }

    static CaseBook moveCases(Path root, List<String> names) {
        return cases(root, names, CaseBook.Shape.MOVES);
    }

    private static CaseBook cases(Path root, List<String> names, CaseBook.Shape shape) {
        return read(root, names).stream()
                .map(file -> shape == CaseBook.Shape.STEPS ? CaseBook.parse(file.file(), file.text())
                        : CaseBook.parseMoves(file.file(), file.text()))
                .reduce(CaseBook::over)
                .orElseGet(() -> CaseBook.empty(shape))
                .failing(unreadable(root, names));
    }

    static Optional<Path> firstExisting(Path root, List<String> names) {
        return names.stream().map(root::resolve).filter(Files::exists).findFirst();
    }

    static Set<String> sectionIds(Path root, List<String> names) {
        return read(root, names).stream()
                .flatMap(file -> Sections.of(file.text()).stream())
                .map(Sections.Section::id)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Optional<Text> file(Path root, String name) {
        Path file = root.resolve(name);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Text(name, Files.readString(file)));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<Problem> failure(Path root, String name) {
        Path file = root.resolve(name);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            Files.readString(file);
            return Optional.empty();
        } catch (IOException unreadable) {
            return Optional.of(new Problem(name, name + ": cannot be read — " + unreadable));
        }
    }
}
