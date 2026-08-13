package dev.jagt.orchestrator.codehost;

import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;

/**
 * A JSON-over-HTTP port, so a code host is testable without a socket: the host implementations hold the part
 * worth testing (URL shapes, which API fields mean "approved", how a note becomes a relay line, what a create
 * call sends), and this hides the transport they all share.
 *
 * <p>Deliberately only the three verbs a `CodeHost` is allowed to use — there is no DELETE here, and adding
 * one would need a reason that survives {@link CodeHost}'s "one write only" rule.
 */
public interface JsonHttp {

    /** Empty on any non-success or unparseable body — an implementation logs the cause, callers just degrade. */
    Optional<JsonNode> get(String url, Map<String, String> headers);

    /** Creates a resource. Empty = the host refused it; the caller must not assume the resource exists. */
    Optional<JsonNode> post(String url, Map<String, String> headers, Map<String, Object> body);

    /** Updates an existing resource. Empty = the host refused it. */
    Optional<JsonNode> put(String url, Map<String, String> headers, Map<String, Object> body);
}
