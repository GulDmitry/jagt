package dev.jagt.orchestrator.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** A file jagt ships as {@code <name>.dist}; the human's copy beside it, named {@code <name>}, is read first. */
@Slf4j
final class HumanCopy {

    record Text(String file, String text) {
    }

    private HumanCopy() {
    }

    /** The copy, then the shipped file, each only where it exists and reads. */
    static List<Text> read(Path root, String copy) {
        return Stream.of(copy, copy + ".dist").map(name -> file(root, name)).flatMap(Optional::stream).toList();
    }

    private static Optional<Text> file(Path root, String name) {
        Path file = root.resolve(name);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Text(name, Files.readString(file)));
        } catch (IOException unreadable) {
            log.atError().setMessage("human file unreadable").addKeyValue("file", file)
                    .addKeyValue("cause", unreadable.toString())
                    .log();
            return Optional.empty();
        }
    }
}
