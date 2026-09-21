package dev.jagt.orchestrator.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Something a human is to be told now, outside any task's own line. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserNotice(String title, String message) implements Message {

    public static final Schema SCHEMA = Schema
            .of("Send an OS push notification to the human (e.g. 'review round addressed — ABC-123')."
                    + " Use when human attention is needed.")
            .text("title", "Defaults to 'jagt'.")
            .required("message", "string", "One line, for a banner that is read in passing.");

    public UserNotice {
        title = title == null || title.isBlank() ? null : title;
        message = message == null || message.isBlank() ? null : message;
    }

    @Override
    public List<Violation> violations(MessageContext context) {
        return message == null
                ? List.of(new Violation("message", "required: a banner with no line says nothing"))
                : List.of();
    }
}
