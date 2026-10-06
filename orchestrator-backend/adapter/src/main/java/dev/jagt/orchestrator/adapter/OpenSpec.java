package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.Specs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class OpenSpec implements Specs {

    private static final String BINARY = "openspec";
    private static final String HOME = "openspec";
    private static final Duration LIMIT = Duration.ofMinutes(2);
    /** An archived change is named `YYYY-MM-DD-<change>`. */
    private static final int ARCHIVE_DATE = "2026-01-01-".length();

    private final Processes processes;
    private final String binary;

    @Autowired
    public OpenSpec(Processes processes) {
        this(processes, Executables.resolve(BINARY));
    }

    OpenSpec(Processes processes, String binary) {
        this.processes = processes;
        this.binary = binary;
    }

    @Override
    public Optional<String> owed(Path worktree, String taskId) {
        if (!keepsSpecs(worktree)) {
            return Optional.empty();
        }
        String change = changeName(taskId);
        try {
            List<String> open = open(worktree, change);
            if (open.isEmpty()) {
                return archived(worktree, change) ? Optional.empty() : Optional.of(howToOpen(change));
            }
            String refused = open.stream().map(name -> refusal(worktree, name)).flatMap(Optional::stream)
                    .collect(Collectors.joining("\n"));
            return refused.isEmpty() ? Optional.empty() : Optional.of(refused);
        } catch (UncheckedIOException e) {
            return Optional.of("could not read openspec/changes: " + e.getCause().getMessage());
        }
    }

    @Override
    public void fold(Path worktree, String taskId) {
        if (!keepsSpecs(worktree)) {
            return;
        }
        for (String name : open(worktree, changeName(taskId))) {
            refusal(worktree, name).ifPresent(refused -> {
                throw new IllegalStateException(refused);
            });
            run(worktree, List.of("archive", name, "-y")).expectSuccess("openspec archive " + name);
        }
    }

    static String changeName(String taskId) {
        return taskId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static boolean keepsSpecs(Path worktree) {
        return Files.isDirectory(worktree.resolve(HOME));
    }

    /** `archive -y` folds a change it never validated, so every fold validates first. */
    private Optional<String> refusal(Path worktree, String name) {
        Processes.Result result = run(worktree, List.of("validate", name, "--type", "change", "--strict",
                "--no-interactive"));
        if (result.exitCode() == 0) {
            return Optional.empty();
        }
        return Optional.of("openspec/changes/" + name + " does not validate — fix every line and hand back again:\n"
                + (result.stdout() + "\n" + result.stderr()).strip());
    }

    private Processes.Result run(Path worktree, List<String> arguments) {
        if (Executables.unresolved(binary)) {
            throw new IllegalStateException("this repository keeps openspec/ and jagt finds no `openspec` CLI:"
                    + " install it (npm i -g @fission-ai/openspec) — ask the human, this is not yours to fix");
        }
        return processes.run(worktree, LIMIT, environment(),
                Stream.concat(Stream.of(binary), arguments.stream()).toList());
    }

    /** The CLI is a `#!/usr/bin/env node` script, and an install puts node beside it rather than on jagt's PATH. */
    private Map<String, String> environment() {
        String inherited = System.getenv("PATH");
        String path = Path.of(binary).getParent() + (inherited == null ? "" : File.pathSeparator + inherited);
        return Map.of("OPENSPEC_TELEMETRY", "0", "PATH", path);
    }

    private static List<String> open(Path worktree, String change) {
        return names(worktree.resolve(HOME).resolve("changes"), name -> !name.equals("archive")
                && isOf(name, change));
    }

    private static boolean archived(Path worktree, String change) {
        return !names(worktree.resolve(HOME).resolve("changes").resolve("archive"),
                name -> name.length() > ARCHIVE_DATE && isOf(name.substring(ARCHIVE_DATE), change)).isEmpty();
    }

    /** A later round opens `<change>-2`, since the first is archived under its own name. */
    private static boolean isOf(String name, String change) {
        return name.equals(change) || name.startsWith(change + "-");
    }

    private static List<String> names(Path directory, Predicate<String> wanted) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(Files::isDirectory).map(entry -> entry.getFileName().toString())
                    .filter(wanted).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String howToOpen(String change) {
        return "this repository records its behaviour in openspec/specs, and your hand-back carries no change:"
                + " run `openspec new change " + change + "`, then write its delta under openspec/changes/" + change
                + "/specs/ (`openspec instructions specs --change " + change + "` says how), or set"
                + " `skip_specs: true` in its .openspec.yaml when no behaviour changes";
    }
}
