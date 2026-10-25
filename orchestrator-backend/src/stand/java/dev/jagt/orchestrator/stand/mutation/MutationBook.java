package dev.jagt.orchestrator.stand.mutation;

import dev.jagt.orchestrator.stand.StandFailure;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MutationBook(List<Mutation> mutations) {

    private static final Pattern HEADING = Pattern.compile("## ([a-z][a-z0-9-]*)");
    private static final Pattern KEY = Pattern.compile("(kind|ask|event|holds|expects):\\s*(.*)");

    public static MutationBook shipped() {
        try (InputStream file = MutationBook.class.getResourceAsStream("/mutations.md")) {
            if (file == null) {
                throw new StandFailure("mutations.md is not on the stand's classpath");
            }
            return parse(new String(file.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new StandFailure("cannot read mutations.md", e);
        }
    }

    public static MutationBook parse(String text) {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        Map<String, String> current = null;
        for (String line : text.lines().map(String::strip).toList()) {
            Matcher heading = HEADING.matcher(line);
            Matcher key = KEY.matcher(line);
            if (heading.matches()) {
                current = new HashMap<>();
                if (sections.put(heading.group(1), current) != null) {
                    throw new StandFailure("mutations.md: " + heading.group(1) + " is written twice");
                }
            } else if (current != null && key.matches()) {
                current.put(key.group(1), key.group(2).strip());
            }
        }
        List<Mutation> mutations = new ArrayList<>();
        sections.forEach((name, keys) -> mutations.add(mutation(name, keys)));
        return new MutationBook(List.copyOf(mutations));
    }

    public Mutation pick(long seed) {
        return mutations.get(Math.floorMod(seed, mutations.size()));
    }

    public List<Mutation> named(String names) {
        return Arrays.stream(names.split(",")).map(String::strip).filter(name -> !name.isEmpty())
                .map(name -> mutations.stream().filter(m -> m.name().equals(name)).findFirst()
                        .orElseThrow(() -> new StandFailure("mutations.md holds no mutation " + name)))
                .toList();
    }

    private static Mutation mutation(String name, Map<String, String> keys) {
        Mutation.Kind kind = Arrays.stream(Mutation.Kind.values())
                .filter(k -> k.name().toLowerCase(Locale.ROOT).equals(keys.getOrDefault("kind", "")))
                .findFirst().orElseThrow(() -> fault(name, "has kind '" + keys.getOrDefault("kind", "")
                        + "', which is not ticket, world or attack"));
        Mutation mutation = new Mutation(name, kind, keys.getOrDefault("ask", ""), keys.getOrDefault("event", ""),
                keys.getOrDefault("holds", ""), keys.getOrDefault("expects", ""));
        if (!mutation.expects().isEmpty() && Arrays.stream(TaskStatus.values())
                .noneMatch(status -> status.name().equals(mutation.expects()))) {
            throw fault(name, "expects '" + mutation.expects() + "', which is no task status");
        }
        if (mutation.holds().isEmpty()) {
            throw fault(name, "names no holds");
        }
        boolean said = switch (kind) {
            case TICKET -> !mutation.ask().isEmpty();
            case WORLD -> !mutation.event().isEmpty();
            case ATTACK -> !mutation.ask().isEmpty() || !mutation.event().isEmpty();
        };
        if (!said) {
            throw fault(name, "says neither the ask nor the event its kind needs");
        }
        return mutation;
    }

    private static StandFailure fault(String name, String what) {
        return new StandFailure("mutations.md: " + name + " " + what);
    }
}
