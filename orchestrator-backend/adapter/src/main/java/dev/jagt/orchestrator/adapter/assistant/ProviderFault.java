package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.OneLine;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** What a finished CLI run says about the model provider, read from the result envelope's own fields. */
final class ProviderFault {

    enum Verdict {
        /** The provider answered, a rate or usage limit included. */
        REACHED,
        FAILED,
        UNKNOWN
    }

    record Reading(Verdict verdict, String cause) {
    }

    private static final String API_ERROR = "api_error";
    private static final int RATE_LIMITED = 429;
    private static final int SERVER_ERROR = 500;
    private static final JsonMapper MAPPER = new JsonMapper();

    private ProviderFault() {
    }

    static Reading of(Processes.Result result) {
        JsonNode envelope = envelope(result.stdout());
        if (envelope == null || !envelope.isObject()) {
            return new Reading(Verdict.UNKNOWN, "");
        }
        if (!envelope.path("is_error").asBoolean(false)) {
            return new Reading(Verdict.REACHED, "");
        }
        if (!API_ERROR.equals(envelope.path("terminal_reason").asString(""))) {
            return new Reading(Verdict.UNKNOWN, "");
        }
        JsonNode status = envelope.path("api_error_status");
        if (status.isNumber() && status.asInt() == RATE_LIMITED) {
            return new Reading(Verdict.REACHED, "");
        }
        if (status.isNumber() && status.asInt() < SERVER_ERROR) {
            return new Reading(Verdict.UNKNOWN, "");
        }
        return new Reading(Verdict.FAILED, String.valueOf(OneLine.clipped(envelope.path("result").asString(""))));
    }

    private static @Nullable JsonNode envelope(String stdout) {
        try {
            return MAPPER.readTree(stdout);
        } catch (RuntimeException unparseable) {
            return null;
        }
    }
}
