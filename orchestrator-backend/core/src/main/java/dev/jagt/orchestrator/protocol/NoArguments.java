package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** A tool that asks for nothing. It is still a message, so that no tool reaches a verb off the declared path. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NoArguments() implements Message {

    public static Schema schema(String description) {
        return Schema.of(description);
    }

    @Override
    public List<Violation> violations(MessageContext context) {
        return List.of();
    }
}
