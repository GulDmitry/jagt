package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.port.Problem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** A file jagt ships as {@code <name>.dist}; the human's copy beside it, named {@code <name>}, is read first. */
final class HumanCopy {

    record Text(String file, String text) {
    }

    private HumanCopy() {
    }

    /** The copy, then the shipped file, each only where it exists and reads; {@link #unreadable} names the rest. */
    static List<Text> read(Path root, String copy) {
        return Stream.of(copy, copy + ".dist").map(name -> file(root, name)).flatMap(Optional::stream).toList();
    }

    /** What keeps a file that exists from being read: startup refuses on it, so no later read may pass for the shipped one. */
    static List<Problem> unreadable(Path root, String copy) {
        return Stream.of(copy, copy + ".dist").map(name -> failure(root, name)).flatMap(Optional::stream).toList();
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
