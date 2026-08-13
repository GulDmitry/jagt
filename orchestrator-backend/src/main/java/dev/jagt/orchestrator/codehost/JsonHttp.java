package dev.jagt.orchestrator.codehost;

import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;

/**
 * A GET-returning-JSON port, so a code host is testable without a socket: the host implementations hold the
 * part worth testing (URL shapes, which API fields mean "approved", how a note becomes a relay line), and
 * this hides the transport they all share.
 */
public interface JsonHttp {

    /** Empty on any non-success or unparseable body — an implementation logs the cause, callers just degrade. */
    Optional<JsonNode> get(String url, Map<String, String> headers);
}
