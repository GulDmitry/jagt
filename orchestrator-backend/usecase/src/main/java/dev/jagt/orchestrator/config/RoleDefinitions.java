package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.protocol.Schema;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles a {@code roles/<id>/role.yml} into a {@link RoleDefinition}; every word it holds is from a closed vocabulary. */
public final class RoleDefinitions {

    private static final Set<String> TRIGGERS = Set.of("deployed", "pipelines-green", "tracker-open",
            "environment-named");
    private static final Set<String> ONCE_PER = Set.of("deploy");
    private static final Set<String> OUTCOMES = Set.of("round", "question", "wait", "dispute", "stop", "label");

    private RoleDefinitions() {
    }

    public static RoleDefinition fromFile(Path file) {
        try {
            return compile(Files.readString(file));
        } catch (IOException unread) {
            throw new UncheckedIOException(unread);
        }
    }

    public static RoleDefinition compile(String yaml) {
        Object tree = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        if (!(tree instanceof Map<?, ?> document)) {
            throw new RoleInvalid("a role.yml is a mapping of id, name, trigger, outcomes, tools, answer and decline");
        }
        String id = text(document, "id");
        List<String> outcomes = words(document, "outcomes", OUTCOMES);
        if (outcomes.isEmpty()) {
            throw new RoleInvalid(id + ": its read has no exit, for no outcome ends it");
        }
        List<String> trigger = words(document, "trigger", TRIGGERS);
        if (!trigger.contains("deployed")) {
            throw new RoleInvalid(id + ": its trigger names deployed, or a check would open before the change is live");
        }
        return new RoleDefinition(id, text(document, "name"), text(document, "model"),
                trigger, word(document, "oncePer", ONCE_PER), outcomes,
                tools(map(document, "tools")), schema(document, "answer"), schema(document, "decline"));
    }

    private static RoleDefinition.Tools tools(Map<?, ?> tools) {
        return new RoleDefinition.Tools(strings(tools, "cli"), strings(tools, "allow"), strings(tools, "refuse"),
                Boolean.TRUE.equals(tools.get("git")), "environments".equals(tools.get("fetch")));
    }

    private static Schema schema(Map<?, ?> document, String key) {
        Schema schema = Schema.answer();
        for (Object item : list(document, key)) {
            if (!(item instanceof Map<?, ?> field) || field.size() < 1) {
                throw new RoleInvalid(key + ": every entry is a mapping naming one field");
            }
            if (field.containsKey("required")) {
                schema.required(String.valueOf(field.get("required")), "string", null);
            } else if (field.containsKey("text")) {
                schema.text(String.valueOf(field.get("text")), null);
            } else if (field.containsKey("records")) {
                schema.records(String.valueOf(field.get("records")), null, strings(field, "fields"), choices(field));
            } else {
                throw new RoleInvalid(key + ": an entry is required, text or records, not " + field.keySet());
            }
        }
        return schema;
    }

    private static Map<String, List<String>> choices(Map<?, ?> field) {
        Map<String, List<String>> choices = new LinkedHashMap<>();
        if (field.get("choices") instanceof Map<?, ?> named) {
            named.forEach((name, values) -> choices.put(String.valueOf(name), strings(Map.of("v", values), "v")));
        }
        return choices;
    }

    private static String text(Map<?, ?> document, String key) {
        Object value = document.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new RoleInvalid("a role.yml names its " + key);
        }
        return String.valueOf(value);
    }

    private static String word(Map<?, ?> document, String key, Set<String> vocabulary) {
        String value = text(document, key);
        if (!vocabulary.contains(value)) {
            throw new RoleInvalid(key + " is one of " + vocabulary.stream().sorted().toList() + ", not " + value);
        }
        return value;
    }

    private static List<String> words(Map<?, ?> document, String key, Set<String> vocabulary) {
        List<String> words = strings(document, key);
        words.stream().filter(word -> !vocabulary.contains(word)).findFirst().ifPresent(unknown -> {
            throw new RoleInvalid(key + " is of " + vocabulary.stream().sorted().toList() + ", not " + unknown);
        });
        return words;
    }

    private static Map<?, ?> map(Map<?, ?> document, String key) {
        return document.get(key) instanceof Map<?, ?> found ? found : Map.of();
    }

    private static List<?> list(Map<?, ?> document, String key) {
        return document.get(key) instanceof List<?> found ? found : List.of();
    }

    private static List<String> strings(Map<?, ?> document, String key) {
        List<String> strings = new ArrayList<>();
        list(document, key).forEach(item -> strings.add(String.valueOf(item)));
        return strings;
    }
}
