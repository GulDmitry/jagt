package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SubscriptionUsage.Answer;
import dev.jagt.orchestrator.port.SubscriptionUsage.NoSubscription;
import dev.jagt.orchestrator.port.SubscriptionUsage.Unreadable;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;

@Slf4j
final class UsageRead {

    private UsageRead() {
    }

    static Answer of(OAuthCredential credential, UsageEndpoint endpoint, URI url, String cli,
                     Function<OAuthCredential.Token, Map<String, String>> headers, Function<String, Answer> parse) {
        Answer answer = switch (credential) {
            case OAuthCredential.None none -> new NoSubscription();
            case OAuthCredential.Failed failed -> new Unreadable(failed.cause());
            case OAuthCredential.Token token -> switch (endpoint.get(url, headers.apply(token))) {
                case UsageEndpoint.Body body -> parse.apply(body.json());
                case UsageEndpoint.Refused refused when refused.status() == 401 || refused.status() == 403 ->
                        new Unreadable(refused.cause() + ", running " + cli + " refreshes the token");
                case UsageEndpoint.Refused refused -> new Unreadable(refused.cause());
            };
        };
        if (answer instanceof Unreadable(String cause)) {
            log.atError().setMessage("usage read failed")
                    .addKeyValue("url", url)
                    .addKeyValue("cause", cause)
                    .log();
        }
        return answer;
    }
}
