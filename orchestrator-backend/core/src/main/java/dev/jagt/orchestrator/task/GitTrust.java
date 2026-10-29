package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.With;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The git state a human last trusted, as one digest per entry, and what was found changed since, null while
 * nothing was. An entry names a config key and its file, or a hook file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GitTrust(Map<String, String> recorded, @With @Nullable String changed) {

    public GitTrust {
        recorded = recorded == null ? Map.of() : Map.copyOf(recorded);
    }

    public static GitTrust of(Map<String, String> read) {
        return new GitTrust(read, null);
    }

    /** This record with {@code more}'s entries added, which name files this one does not. */
    public GitTrust including(GitTrust more) {
        Map<String, String> both = new HashMap<>(recorded);
        both.putAll(more.recorded());
        return new GitTrust(both, changed);
    }

    /** Each entry differing from the record, in name order, followed by how. */
    public List<String> differences(Map<String, String> read) {
        TreeSet<String> entries = new TreeSet<>(recorded.keySet());
        entries.addAll(read.keySet());
        return entries.stream().filter(entry -> !Objects.equals(recorded.get(entry), read.get(entry)))
                .map(entry -> entry + (!recorded.containsKey(entry) ? " added"
                        : !read.containsKey(entry) ? " removed" : " changed"))
                .toList();
    }
}
