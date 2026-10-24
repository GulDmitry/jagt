package dev.jagt.orchestrator.stand;

import dev.jagt.orchestrator.adapter.agent.StandTrust;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class StandTraces {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern ENCODED_ROOT = Pattern.compile("(" + Pattern.quote(StandRoots.PREFIX) + "\\d+-\\d+)(?:-.*)?");

    public record Removed(int trust, int transcripts) {
    }

    private final Path base;
    private final Path configFile;
    private final Path projectsDir;

    public StandTraces(Path base, Path configFile, Path projectsDir) {
        this.base = base;
        this.configFile = configFile;
        this.projectsDir = projectsDir;
    }

    public static StandTraces ofTheHuman(Path base) {
        String configured = System.getenv("CLAUDE_CONFIG_DIR");
        Path claudeDir = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home")).resolve(".claude")
                : Path.of(configured);
        return new StandTraces(base, StandTrust.configFile(), claudeDir.resolve("projects"));
    }

    public Removed remove(Predicate<String> rootSelected) {
        return new Removed(forgetTrust(rootSelected), deleteTranscripts(rootSelected));
    }

    private int forgetTrust(Predicate<String> rootSelected) {
        int forgotten = 0;
        for (String key : projectKeys()) {
            if (rootOfKey(key).filter(rootSelected).isPresent()) {
                StandTrust.forget(configFile, Path.of(key));
                forgotten++;
            }
        }
        return forgotten;
    }

    private List<String> projectKeys() {
        if (!Files.isRegularFile(configFile)) {
            return List.of();
        }
        try {
            JsonNode projects = JSON.readTree(Files.readString(configFile)).path("projects");
            return projects.isObject() ? List.copyOf(projects.propertyNames()) : List.of();
        } catch (IOException | JacksonException e) {
            throw new StandFailure("cannot read " + configFile, e);
        }
    }

    private Optional<String> rootOfKey(String key) {
        for (String prefix : bases()) {
            if (key.startsWith(prefix + "/")) {
                String first = key.substring(prefix.length() + 1).split("/", 2)[0];
                return StandRoots.named(Path.of(first)) ? Optional.of(first) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private int deleteTranscripts(Predicate<String> rootSelected) {
        if (!Files.isDirectory(projectsDir)) {
            return 0;
        }
        int deleted = 0;
        try (Stream<Path> entries = Files.list(projectsDir)) {
            for (Path dir : entries.filter(Files::isDirectory).filter(dir -> !Files.isSymbolicLink(dir)).toList()) {
                if (rootOfTranscript(dir.getFileName().toString()).filter(rootSelected).isPresent()) {
                    StandShell.deleteTree(dir);
                    deleted++;
                }
            }
        } catch (IOException e) {
            throw new StandFailure("cannot list " + projectsDir, e);
        }
        return deleted;
    }

    private Optional<String> rootOfTranscript(String name) {
        for (String prefix : bases()) {
            String encoded = prefix.replaceAll("[^A-Za-z0-9]", "-") + "-";
            if (name.startsWith(encoded)) {
                Matcher root = ENCODED_ROOT.matcher(name.substring(encoded.length()));
                return root.matches() ? Optional.of(root.group(1)) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Set<String> bases() {
        Set<String> forms = new LinkedHashSet<>();
        forms.add(base.toAbsolutePath().normalize().toString());
        try {
            forms.add(base.toRealPath().toString());
        } catch (IOException gone) {
            return forms;
        }
        return forms;
    }
}
