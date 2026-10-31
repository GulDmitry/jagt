package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.task.QaVerdict.Status;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class QaCases {

    private static final String ROLE = "eval.role";

    private QaCases() {
    }

    public static List<QaCase> all() {
        try (Stream<Path> files = Files.list(Path.of("..", "roles", System.getProperty(ROLE, "qa"), "eval"))) {
            return files.filter(file -> file.getFileName().toString().endsWith(".yml")).sorted()
                    .map(QaCases::read).toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(new IOException("the role's eval cases unreadable: " + unreadable, unreadable));
        }
    }

    private static QaCase read(Path file) {
        try {
            return parse(Files.readString(file));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @SuppressWarnings("unchecked")
    public static QaCase parse(String yaml) {
        Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        Map<String, Object> ticket = (Map<String, Object>) root.get("ticket");
        Map<String, Object> deploy = (Map<String, Object>) root.get("deploy");
        Map<String, Object> expect = (Map<String, Object>) root.get("expect");
        List<QaCase.Criterion> criteria = ((List<Map<String, Object>>) ticket.get("criteria")).stream()
                .map(criterion -> new QaCase.Criterion(text(criterion.get("id")), text(criterion.get("text")),
                        !Boolean.FALSE.equals(criterion.get("testable")))).toList();
        Map<String, Status> rows = new LinkedHashMap<>();
        ((Map<String, Object>) expect.get("rows")).forEach((id, status) ->
                rows.put(id, Status.of(text(status)).orElseThrow()));
        return new QaCase(text(root.get("case")), text(ticket.get("key")), text(ticket.get("title")), criteria,
                new QaCase.Deploy(text(deploy.get("sha")), text(deploy.get("landed")),
                        ((List<Object>) deploy.get("diff")).stream().map(QaCases::text).toList(),
                        deploy.get("landed_sha_served") == null ? "" : text(deploy.get("landed_sha_served"))),
                (Map<String, Object>) root.getOrDefault("environment", Map.of()), decline(root), rows,
                text(expect.get("report")), expect.get("probe") == null ? "" : text(expect.get("probe")),
                text(expect.get("ending")), ((List<Object>) root.get("names")).stream().map(QaCases::text).toList());
    }

    @SuppressWarnings("unchecked")
    private static QaCase.Decline decline(Map<String, Object> root) {
        Map<String, Object> reply = (Map<String, Object>) root.get("session_reply");
        if (reply == null) {
            return null;
        }
        Map<String, Object> previous = (Map<String, Object>) root.get("previous_check");
        return new QaCase.Decline(text(previous.get("evidence")), text(reply.get("why")));
    }

    private static String text(Object value) {
        return String.valueOf(value);
    }
}
