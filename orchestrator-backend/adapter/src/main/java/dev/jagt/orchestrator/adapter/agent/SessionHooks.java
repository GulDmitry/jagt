package dev.jagt.orchestrator.adapter.agent;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Which of one CLI's own events mean what to jagt, as a resource per runtime rather than a table inside Java:
 * this is the vendor knowledge a human comes looking for, and it changes when the CLI does rather than when
 * jagt does.
 *
 * <p>A runtime with no resource of its own maps nothing, which is how a CLI without hooks stays supported.
 */
final class SessionHooks {

    private SessionHooks() {
    }

    /** Declarations that are not an event-to-state line: one is answered, the other is a vendor's word. */
    private static final String GATE = "gate";
    private static final String COMPACTED_START = "compacted-start";
    private static final java.util.Set<String> NOT_A_STATE = java.util.Set.of(GATE, COMPACTED_START);

    /** The event this CLI answers with allow or deny, or nothing where it has none. */
    static Optional<String> gate(String runtime) {
        return Optional.ofNullable(declared(runtime).getProperty(GATE));
    }

    /** What this CLI calls a start that follows a compaction, or blank where it says nothing about starts. */
    static String compactedStart(String runtime) {
        return declared(runtime).getProperty(COMPACTED_START, "");
    }

    /** Event to state, in a fixed order so the generated file does not churn between provisionings. */
    static Map<String, String> of(String runtime) {
        Properties declared = declared(runtime);
        Map<String, String> events = new LinkedHashMap<>();
        new TreeSet<>(declared.stringPropertyNames()).stream().filter(event -> !NOT_A_STATE.contains(event))
                .forEach(event -> events.put(event, declared.getProperty(event)));
        return events;
    }

    private static Properties declared(String runtime) {
        Properties declared = new Properties();
        try (InputStream in = new ClassPathResource("hooks/" + runtime + ".properties").getInputStream()) {
            declared.load(in);
        } catch (IOException e) {
            return new Properties();
        }
        return declared;
    }
}
