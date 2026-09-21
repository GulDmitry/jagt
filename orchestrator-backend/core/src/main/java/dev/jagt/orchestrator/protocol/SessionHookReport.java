package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What a session's own harness says about it. The one message jagt does not shape: a hook posts whatever the CLI
 * handed it, and the hook swallows the answer, so a violation here is not a correction anybody will read. jagt
 * therefore DROPS what it cannot believe and records the rest — the field names are the vendor's, kept in this
 * one place so the rest of jagt never spells them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionHookReport(@JsonProperty("transcript_path") String transcriptPath,
                                @JsonProperty("source") String source,
                                @JsonProperty("message") String message) implements Message {

    public SessionHookReport {
        transcriptPath = absent(transcriptPath);
        source = absent(source);
        message = absent(message);
    }

    /** Nothing was posted at all, which is a state the address alone still reports. */
    public static SessionHookReport none() {
        return new SessionHookReport(null, null, null);
    }

    /**
     * A relative path is the one thing jagt must not believe: it would resolve against the orchestrator's own
     * directory and name a log some other process writes.
     */
    @Override
    public List<Violation> violations(MessageContext context) {
        return transcriptPath != null && !Path.of(transcriptPath).isAbsolute()
                ? List.of(new Violation("transcript_path",
                        "an absolute path; '" + transcriptPath + "' would resolve against jagt's own directory"))
                : List.of();
    }

    /** Where this session writes its log, or empty where the harness named none jagt can follow. */
    public Optional<Path> sessionLog() {
        return violations(MessageContext.NONE).isEmpty() && transcriptPath != null
                ? Optional.of(Path.of(transcriptPath))
                : Optional.empty();
    }

    private static String absent(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
