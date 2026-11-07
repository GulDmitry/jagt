package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SubscriptionUsage.Answer;
import dev.jagt.orchestrator.port.SubscriptionUsage.NoSubscription;
import dev.jagt.orchestrator.port.SubscriptionUsage.Unreadable;
import java.net.URI;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class UsageRead {

  private UsageRead() {}

  static Answer of(
      OauthCredential credential,
      UsageEndpoint endpoint,
      URI url,
      String cli,
      Function<OauthCredential.Token, Map<String, String>> headers,
      Function<String, Answer> parse) {
    Answer answer =
        switch (credential) {
          case OauthCredential.None none -> new NoSubscription();
          case OauthCredential.Failed failed -> new Unreadable(failed.cause());
          case OauthCredential.Token token ->
              switch (endpoint.get(url, headers.apply(token))) {
                case UsageEndpoint.Body body -> parse.apply(body.json());
                case UsageEndpoint.Refused refused
                    when refused.status() == 401 || refused.status() == 403 ->
                    new Unreadable(refused.cause() + ", running " + cli + " refreshes the token");
                case UsageEndpoint.Refused refused -> new Unreadable(refused.cause());
              };
        };
    if (answer instanceof Unreadable(String cause)) {
      log.atError()
          .setMessage("usage read failed")
          .addKeyValue("url", url)
          .addKeyValue("cause", cause)
          .log();
    }
    return answer;
  }
}
