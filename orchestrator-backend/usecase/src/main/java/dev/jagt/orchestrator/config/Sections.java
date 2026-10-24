package dev.jagt.orchestrator.config;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A file split at its {@code ## <id>} headings; text above the first belongs to no section. */
final class Sections {

    record Section(String id, String body) {
    }

    /** Only an id heading ends a section: a body may show a heading of its own. */
    private static final Pattern HEADING = Pattern.compile("## ([a-z][a-z0-9-]*)");

    private Sections() {
    }

    static List<Section> of(String text) {
        List<Section> sections = new ArrayList<>();
        @Nullable String id = null;
        List<String> body = new ArrayList<>();
        for (String line : text.lines().toList()) {
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                add(sections, id, body);
                id = heading.group(1);
                body = new ArrayList<>();
            } else {
                body.add(line);
            }
        }
        add(sections, id, body);
        return List.copyOf(sections);
    }

    private static void add(List<Section> sections, @Nullable String id, List<String> body) {
        if (id != null) {
            sections.add(new Section(id, String.join("\n", body).strip()));
        }
    }
}
