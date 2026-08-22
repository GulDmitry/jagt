package dev.jagt.orchestrator.adapter.agent;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
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

    /** Event to state, in a fixed order so the generated file does not churn between provisionings. */
    static Map<String, String> of(String runtime) {
        Properties declared = new Properties();
        try (InputStream in = new ClassPathResource("hooks/" + runtime + ".properties").getInputStream()) {
            declared.load(in);
        } catch (IOException e) {
            return Map.of();
        }
        Map<String, String> events = new LinkedHashMap<>();
        new TreeSet<>(declared.stringPropertyNames())
                .forEach(event -> events.put(event, declared.getProperty(event)));
        return events;
    }
}
