package dev.jagt.orchestrator.adapter.agent;

/** What a CLI's stored login holds; a token lives only as long as the one read that needs it. */
sealed interface OAuthCredential {

    /** {@code accountId} is blank where the vendor asks for none. */
    record Token(String accessToken, String accountId) implements OAuthCredential {

        @Override
        public String toString() {
            return "Token[redacted]";
        }
    }

    /** A login without a subscription: an API key, or none at all. */
    record None() implements OAuthCredential {
    }

    /** {@code cause} never quotes the stored login. */
    record Failed(String cause) implements OAuthCredential {
    }
}
